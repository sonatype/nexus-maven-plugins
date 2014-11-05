/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */

package org.sonatype.nexus.maven.staging.deploy.strategy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;
import com.google.common.io.Closeables;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.metadata.GroupRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.Plugin;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.codehaus.plexus.component.annotations.Requirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class AbstractDeployStrategy
    implements DeployStrategy
{
  protected final Logger log = LoggerFactory.getLogger(getClass());

  @Requirement
  private ArtifactInstaller artifactInstaller;

  @Requirement
  private ArtifactDeployer artifactDeployer;

  @Requirement
  private ArtifactRepositoryFactory artifactRepositoryFactory;

  @Requirement
  private ArtifactRepositoryLayout artifactRepositoryLayout;

  protected ArtifactRepository createDeploymentArtifactRepository(final String id, final String url) {
    return artifactRepositoryFactory.createDeploymentArtifactRepository(id, url, artifactRepositoryLayout, true);
  }

  protected File getStagingDirectory(final File stagingDirectoryRoot, final String profileId)
      throws MojoExecutionException
  {
    final File root = stagingDirectoryRoot;
    if (Strings.isNullOrEmpty(profileId)) {
      throw new MojoExecutionException(
          "Internal bug: passed in profileId must be non-null and non-empty string!");
    }
    return new File(root, profileId);
  }

  /**
   * Static lock object, used to synchronize access to non-thread safe methods of this strategy in case of
   * parallel build (Maven3).
   */
  private static final Object parallelLock = new Object();

  /**
   * Performs an "install" (not to be confused with "install into local repository!) into the staging repository. It
   * will retain snapshot versions, and no metadata is created at all. In short: performs a simple file copy.
   * <p/>
   * This one single method is not thread safe, as it performs IO and appends to the "index" file. Hence, this
   * one method is execute in a synchronized block of a static object's monitor, to prevent multiple parallel
   * install actions to happen. As install is local FS operation, this method should quickly finish it's job
   * as costly operations (like asking for Staging profile of REST API of remote Nexus) already happened, we
   * do only local FS ops here.
   */
  protected void install(final File source, final Artifact artifact, final ArtifactRepository stagingRepository,
                         final File stagingDirectory, final ArtifactRepository remoteRepository)
      throws ArtifactInstallationException, MojoExecutionException
  {
    synchronized (parallelLock) {
      final String path = stagingRepository.pathOf(artifact);
      try {
        artifactInstaller.install(source, artifact, stagingRepository);

        String pluginPrefix = null;
        // String pluginName = null;
        for (ArtifactMetadata artifactMetadata : artifact.getMetadataList()) {
          if (artifactMetadata instanceof GroupRepositoryMetadata) {
            final Plugin plugin =
                ((GroupRepositoryMetadata) artifactMetadata).getMetadata().getPlugins().get(0);
            pluginPrefix = plugin.getPrefix();
            // pluginName = plugin.getName();
          }
        }

        // append the index file
        final FileOutputStream fos = new FileOutputStream(new File(stagingDirectory, ".index"), true);
        final OutputStreamWriter osw = new OutputStreamWriter(fos, "ISO-8859-1");
        final PrintWriter pw = new PrintWriter(osw);
        String pomFileName = null;
        for (ArtifactMetadata artifactMetadata : artifact.getMetadataList()) {
          if (artifactMetadata instanceof ProjectArtifactMetadata) {
            pomFileName = (artifactMetadata).getLocalFilename(stagingRepository);
          }
        }
        pw.println(String.format("%s=%s:%s:%s:%s:%s:%s:%s:%s:%s:%s", path, artifact.getGroupId(),
            artifact.getArtifactId(), artifact.getVersion(),
            Strings.isNullOrEmpty(artifact.getClassifier()) ? "n/a" : artifact.getClassifier(), artifact.getType(),
            artifact.getArtifactHandler().getExtension(), Strings.isNullOrEmpty(pomFileName) ? "n/a" : pomFileName,
            Strings.isNullOrEmpty(pluginPrefix) ? "n/a" : pluginPrefix,
            remoteRepository != null ? remoteRepository.getId() : "n/a",
            remoteRepository != null ? remoteRepository.getUrl() : "n/a"));
        pw.flush();
        pw.close();
      }
      catch (IOException e) {
        throw new ArtifactInstallationException("Cannot locally stage and maintain the index file!", e);
      }
    }
  }

  // G:A:V:C:P:Ext:PomFileName:PluginPrefix:repoId:repoUrl
  private final Pattern indexProps = Pattern.compile("([^:]*):([^:]*):([^:]*):([^:]*):([^:]*):([^:]*):([^:]*):([^:]*):([^:]*):(.*)");

  protected void deployUp(final MavenSession mavenSession, final File sourceDirectory,
                          ArtifactRepository remoteRepository)
      throws ArtifactDeploymentException, IOException
  {
    // I'd need Aether RepoSystem and create one huge DeployRequest will _all_ artifacts (would be FAST as it would
    // go parallel), but we need to work in Maven2 too, so old compat and slow method remains: deploy one by one...
    final FileInputStream fis = new FileInputStream(new File(sourceDirectory, ".index"));
    final Properties index = new Properties();
    try {
      index.load(fis);
    }
    finally {
      Closeables.closeQuietly(fis);
    }
    ArtifactRepository repoToUse = remoteRepository;
    for (String includedFilePath : index.stringPropertyNames()) {
      final File includedFile = new File(sourceDirectory, includedFilePath);
      final String includedFileProps = index.getProperty(includedFilePath);
      final Matcher matcher = indexProps.matcher(includedFileProps);
      if (!matcher.matches()) {
        throw new ArtifactDeploymentException("Internal error! Line \"" + includedFileProps
            + "\" does not match pattern \"" + indexProps.toString() + "\"?");
      }

      final String groupId = matcher.group(1);
      final String artifactId = matcher.group(2);
      final String version = matcher.group(3);
      final String classifier = "n/a".equals(matcher.group(4)) ? null : matcher.group(4);
      final String packaging = matcher.group(5);
      final String extension = matcher.group(6);
      final String pomFileName = "n/a".equals(matcher.group(7)) ? null : matcher.group(7);
      final String pluginPrefix = "n/a".equals(matcher.group(8)) ? null : matcher.group(8);
      final String repoId = "n/a".equals(matcher.group(9)) ? null : matcher.group(9);
      final String repoUrl = "n/a".equals(matcher.group(10)) ? null : matcher.group(10);
      if (remoteRepository == null) {
          if (repoUrl != null && repoId != null) {
            repoToUse = createDeploymentArtifactRepository(repoId, repoUrl);
          } else {
              throw new ArtifactDeploymentException("Internal error! Remote repository for deployment not defined.");
          }
      }

      // just a synthetic one, to properly set extension
      final FakeArtifactHandler artifactHandler = new FakeArtifactHandler(packaging, extension);

      final DefaultArtifact artifact =
          new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), null, packaging,
              classifier, artifactHandler);
      if (pomFileName != null) {
        final File pomFile = new File(includedFile.getParentFile(), pomFileName);
        final ProjectArtifactMetadata pom = new ProjectArtifactMetadata(artifact, pomFile);
        artifact.addMetadata(pom);
        if ("maven-plugin".equals(artifact.getType())) {
          // So, we have a "main" artifact with type of "maven-plugin"
          // Hence, this is a Maven Plugin, Group level MD needs to be added too
          final GroupRepositoryMetadata groupMetadata = new GroupRepositoryMetadata(groupId);
          // TODO: we "simulate" the name with artifactId, same what maven-plugin-plugin
          // would do. Impact is minimal, as we don't know any tool that _uses_ the name
          // from Plugin entries. Once the "index file" is properly solved,
          // or, we are able to properly persist Artifact instances above
          // (to preserve attached metadatas like this G level, and reuse
          // deployer without reimplementing it), all this will become unneeded.
          groupMetadata.addPluginMapping(pluginPrefix, artifactId, artifactId);
          artifact.addMetadata(groupMetadata);
        }
      }
      artifactDeployer.deploy(includedFile, artifact, repoToUse, mavenSession.getLocalRepository());
    }
  }

  // ==

  /**
   * Just a "fake" synthetic handler, to overcome Maven2/3 differences (no extension setter in M2 but there is in M3
   * on {@link org.apache.maven.artifact.handler.DefaultArtifactHandler}.
   */
  public static class FakeArtifactHandler
      extends DefaultArtifactHandler
  {

    private final String extension;

    /**
     * Constructor.
     */
    public FakeArtifactHandler(final String type, final String extension) {
      super(checkNotNull(type));
      this.extension = checkNotNull(extension);
    }

    @Override
    public String getExtension() {
      return extension;
    }
  }

  // ==

  /**
   * Returns the ArtifactRepository created from passed in maven session's current project's distribution management.
   */
  protected ArtifactRepository getDeploymentRepository(final MavenSession mavenSession)
      throws MojoExecutionException
  {
    final ArtifactRepository repo = mavenSession.getCurrentProject().getDistributionManagementArtifactRepository();
    if (repo == null) {
      String msg =
          "Deployment failed: repository element was not specified in the POM inside"
              + " distributionManagement element or in -DaltDeploymentRepository=id::layout::url parameter";

      throw new MojoExecutionException(msg);
    }
    return repo;
  }

  protected ArtifactRepository getArtifactRepositoryForDirectory(final File stagingDirectory)
      throws MojoExecutionException
  {
    if (stagingDirectory != null) {
      if (stagingDirectory.exists() && (!stagingDirectory.canWrite() || !stagingDirectory.isDirectory())) {
        // it exists but is not writable or is not a directory
        throw new MojoExecutionException(
            "Staging failed: staging directory points to an existing file but is not a directory or is not writable!");
      }
      else if (!stagingDirectory.exists()) {
        // it does not exists, create it
        stagingDirectory.mkdirs();
      }

      try {
        final String id = "nexus";
        final String url = stagingDirectory.getCanonicalFile().toURI().toURL().toExternalForm();
        return createDeploymentArtifactRepository(id, url);
      }
      catch (IOException e) {
        throw new MojoExecutionException(
            "Staging failed: staging directory path cannot be converted to canonical one!", e);
      }
    }
    else {
      throw new MojoExecutionException("Staging failed: staging directory is null!");
    }
  }

}
