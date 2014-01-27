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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.sonatype.nexus.staging.client.Profile;
import com.sonatype.nexus.staging.client.ProfileMatchingParameters;
import com.sonatype.nexus.staging.client.StagingRuleFailures;
import com.sonatype.nexus.staging.client.StagingRuleFailuresException;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

import org.sonatype.nexus.client.core.NexusStatus;
import org.sonatype.nexus.client.core.exception.NexusClientErrorResponseException;
import org.sonatype.nexus.maven.staging.ErrorDumper;
import org.sonatype.nexus.maven.staging.StagingAction;
import org.sonatype.nexus.maven.staging.deploy.StagingRepository;
import org.sonatype.nexus.maven.staging.remote.Parameters;
import org.sonatype.nexus.maven.staging.remote.RemoteNexus;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import com.google.common.base.Strings;
import com.google.common.io.Closeables;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.component.annotations.Requirement;

public abstract class AbstractStagingDeployStrategy
    extends AbstractDeployStrategy
{

  @Requirement
  private SecDispatcher secDispatcher;

  private RemoteNexus remoteNexus;

  public void prepare(MavenSession mavenSession, Parameters parameters) {
    super.prepare(mavenSession, parameters);
    parameters.validateStaging();
    this.remoteNexus = new RemoteNexus(mavenSession, secDispatcher, getLogger().isDebugEnabled(), parameters);
    if (remoteNexus.getServer() != null) {
      getLogger().info(
          "Using server credentials with ID=\"" + remoteNexus.getServer().getId() + "\" from Maven settings.");
    }
    if (remoteNexus.getProxy() != null) {
      getLogger().info(
          "Using " + remoteNexus.getProxy().getProtocol().toUpperCase() + " Proxy with ID=\""
              + remoteNexus.getProxy().getId() + "\" from Maven settings.");
    }
  }

  protected RemoteNexus getRemoteNexus() {
    return remoteNexus;
  }

  // ==

  /**
   * Selects a staging profile based on informations given (configured) to Mojo.
   *
   * @param artifact the artifact for we match
   * @return the profileID selected
   */
  protected String selectStagingProfile(final Artifact artifact)
      throws MojoExecutionException
  {
    try {
      final NexusStatus nexusStatus = getRemoteNexus().getNexusStatus();
      getLogger().info(
          String.format(" * Connected to Nexus at %s, is version %s and edition \"%s\"",
              getRemoteNexus().getConnectionInfo().getBaseUrl(), nexusStatus.getVersion(),
              nexusStatus.getEditionLong()));
      final StagingWorkflowV2Service stagingService = getRemoteNexus().getStagingWorkflowV2Service();

      Profile stagingProfile;
      // if profile is not "targeted", perform a match and save the result
      if (Strings.isNullOrEmpty(getParameters().getStagingProfileId())) {
        final ProfileMatchingParameters params =
            new ProfileMatchingParameters(artifact.getGroupId(), artifact.getArtifactId(),
                artifact.getVersion());
        stagingProfile = stagingService.matchProfile(params);
        getLogger().info(
            " * Using staging profile ID \"" + stagingProfile.id() + "\" (matched by Nexus).");
      }
      else {
        stagingProfile = stagingService.selectProfile(getParameters().getStagingProfileId());
        getLogger().info(
            " * Using staging profile ID \"" + stagingProfile.id() + "\" (configured by user).");
      }
      return stagingProfile.id();
    }
    catch (NexusClientErrorResponseException e) {
      ErrorDumper.dumpErrors(getLogger(), e);
      // fail the build
      throw new MojoExecutionException("Could not perform action: Nexus ErrorResponse received!", e);
    }
  }

  protected StagingRepository beforeUpload(final Profile stagingProfile)
      throws MojoExecutionException
  {
    try {
      final StagingWorkflowV2Service stagingService = getRemoteNexus().getStagingWorkflowV2Service();
      if (Strings.isNullOrEmpty(getParameters().getStagingRepositoryId())) {
        String createdStagingRepositoryId =
            stagingService.startStaging(stagingProfile,
                getParameters().getActionDescription(StagingAction.START),
                getParameters().getTags());
        // store the one just created for us, as it means we need to "babysit" it (close or drop, depending
        // on outcome)
        if (getParameters().getTags() != null && !getParameters().getTags().isEmpty()) {
          getLogger().info(
              " * Created staging repository with ID \"" + createdStagingRepositoryId + "\", applied tags: "
                  + getParameters().getTags());
        }
        else {
          getLogger().info(" * Created staging repository with ID \"" + createdStagingRepositoryId + "\".");
        }
        final String url =
            stagingService.startedRepositoryBaseUrl(stagingProfile, createdStagingRepositoryId);
        return new StagingRepository(stagingProfile, createdStagingRepositoryId, url, true);
      }
      else {
        getLogger().info(
            " * Using non-managed staging repository with ID \"" + getParameters().getStagingRepositoryId()
                + "\" (we are NOT managing it)."); // we will not close it! This might be created by some
        // other automated component
        final String url =
            stagingService.startedRepositoryBaseUrl(stagingProfile, getParameters().getStagingRepositoryId());
        return new StagingRepository(stagingProfile, getParameters().getStagingRepositoryId(), url, false);
      }
    }
    catch (NexusClientErrorResponseException e) {
      ErrorDumper.dumpErrors(getLogger(), e);
      // fail the build
      throw new MojoExecutionException("Could not perform action: Nexus ErrorResponse received!", e);
    }
  }

  public static final String STAGING_REPOSITORY_PROPERTY_FILE_NAME_SUFFIX = ".properties";

  public static final String STAGING_REPOSITORY_ID = "stagingRepository.id";

  public static final String STAGING_REPOSITORY_PROFILE_ID = "stagingRepository.profileId";

  public static final String STAGING_REPOSITORY_URL = "stagingRepository.url";

  public static final String STAGING_REPOSITORY_MANAGED = "stagingRepository.managed";

  protected void afterUpload(final StagingRepository stagingRepository)
      throws MojoExecutionException, StagingRuleFailuresException
  {
    // if upload successful. write out the properties file
    final String stagingRepositoryUrl =
        concat(getRemoteNexus().getConnectionInfo().getBaseUrl().toString(),
            "/content/repositories", stagingRepository.getRepositoryId());

    final Properties stagingProperties = new Properties();
    // the staging repository ID where the staging went
    stagingProperties.put(STAGING_REPOSITORY_ID, stagingRepository.getRepositoryId());
    // the staging repository's profile ID where the staging went
    stagingProperties.put(STAGING_REPOSITORY_PROFILE_ID, stagingRepository.getProfile().id());
    // the staging repository URL (if closed! see below)
    stagingProperties.put(STAGING_REPOSITORY_URL, stagingRepositoryUrl);
    // targeted repo mode or not (are we closing it or someone else? If false, the URL above might not yet
    // exists if not yet closed....
    stagingProperties.put(STAGING_REPOSITORY_MANAGED, String.valueOf(stagingRepository.isManaged()));

    final File stagingPropertiesFile =
        new File(getParameters().getStagingDirectoryRoot(), stagingRepository.getProfile().id()
            + STAGING_REPOSITORY_PROPERTY_FILE_NAME_SUFFIX);

    // this below is the case with DeployRepositoryMojo, where we have no folder created
    // as we remotely staged something completely different
    if (!stagingPropertiesFile.getParentFile().isDirectory()) {
      stagingPropertiesFile.getParentFile().mkdirs();
    }

    FileOutputStream fout = null;
    try {
      fout = new FileOutputStream(stagingPropertiesFile);
      stagingProperties.store(fout, "Generated by " + getParameters().getPluginGav());
      fout.flush();
    }
    catch (IOException e) {
      throw new MojoExecutionException("Error saving staging repository properties to file "
          + stagingPropertiesFile, e);
    }
    finally {
      Closeables.closeQuietly(fout);
    }

    // if repository is managed, then manage it
    if (stagingRepository.isManaged()) {
      final StagingWorkflowV2Service stagingService = getRemoteNexus().getStagingWorkflowV2Service();
      try {
        if (!getParameters().isSkipStagingRepositoryClose()) {
          try {
            getLogger().info(
                " * Closing staging repository with ID \"" + stagingRepository.getRepositoryId() + "\".");
            stagingService.finishStaging(stagingRepository.getProfile(),
                stagingRepository.getRepositoryId(),
                getParameters().getActionDescription(StagingAction.FINISH));
          }
          catch (StagingRuleFailuresException e) {
            getLogger().error(
                "Rule failure while trying to close staging repository with ID \""
                    + stagingRepository.getRepositoryId() + "\".");
            // report staging repository failures
            ErrorDumper.dumpErrors(getLogger(), e);
            // rethrow
            throw e;
          }
        }
        else {
          getLogger().info(
              " * Not closing staging repository with ID \"" + stagingRepository.getRepositoryId() + "\".");
        }
      }
      catch (NexusClientErrorResponseException e) {
        getLogger().error(
            "Error while trying to close staging repository with ID \"" + stagingRepository.getRepositoryId()
                + "\".");
        ErrorDumper.dumpErrors(getLogger(), e);
        // fail the build
        throw new MojoExecutionException("Could not perform action against repository \""
            + stagingRepository.getRepositoryId()
            + "\": Nexus ErrorResponse received!", e);
      }
    }
  }

  /**
   * Performs various cleanup after staging repository failure.
   */
  protected void afterUploadFailure(final List<StagingRepository> stagingRepositories,
                                    final Throwable problem)
      throws MojoExecutionException
  {
    final String msg;
    final boolean keep;

    // rule failed, undo all what we did on server side and client side: drop all the products of this reactor
    if (problem instanceof StagingRuleFailuresException) {
      final StagingRuleFailuresException srfe = (StagingRuleFailuresException) problem;
      final List<String> failedRepositories = new ArrayList<String>();
      for (StagingRuleFailures failures : srfe.getFailures()) {
        failedRepositories.add(failures.getRepositoryId());
      }
      msg = "Rule failure during close of staging repositories: " + failedRepositories;
      keep = getParameters().isKeepStagingRepositoryOnCloseRuleFailure();
    }
    else if (problem instanceof IOException) {
      msg = "IO failure during deploy";
      keep = getParameters().isKeepStagingRepositoryOnFailure();
    }
    else if (problem instanceof InvalidRepositoryException) {
      msg = "Internal error: " + problem.getMessage();
      keep = getParameters().isKeepStagingRepositoryOnFailure();
    }
    else {
      return;
    }

    getLogger().error("Cleaning up local stage directory after a " + msg);
    // delete properties (as they are getting created when remotely staged)
    final File stageRoot = getParameters().getStagingDirectoryRoot();
    final File[] localStageRepositories = stageRoot.listFiles();
    if (localStageRepositories != null) {
      for (File file : localStageRepositories) {
        if (file.isFile() && file.getName().endsWith(STAGING_REPOSITORY_PROPERTY_FILE_NAME_SUFFIX)) {
          getLogger().error(" * Deleting context " + file.getName());
          file.delete();
        }
      }
    }

    getLogger().error("Cleaning up remote stage repositories after a " + msg);
    // drop all created staging repositories
    final StagingWorkflowV2Service stagingService = getRemoteNexus().getStagingWorkflowV2Service();
    for (StagingRepository stagingRepository : stagingRepositories) {
      if (stagingRepository.isManaged()) {
        if (!keep) {
          getLogger().error(
              " * Dropping failed staging repository with ID \"" + stagingRepository.getRepositoryId()
                  + "\" (" + msg + ").");
          stagingService.dropStagingRepositories(getParameters().getActionDescription(StagingAction.DROP)
              + " (" + msg + ").",
              stagingRepository.getRepositoryId());
        }
        else {
          getLogger().error(
              " * Not dropping failed staging repository with ID \"" + stagingRepository.getRepositoryId()
                  + "\" (" + msg + ").");
        }
      }
    }
  }

  protected String concat(String... paths) {
    final StringBuilder result = new StringBuilder();
    for (String path : paths) {
      while (path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      if (result.length() > 0 && !path.startsWith("/")) {
        result.append("/");
      }
      result.append(path);
    }
    return result.toString();
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

  protected ArtifactRepository getDeploymentArtifactRepositoryForNexusStagingRepository(
      final StagingRepository stagingRepository)
      throws InvalidRepositoryException
  {
    final ArtifactRepository result =
        createDeploymentArtifactRepository(getRemoteNexus().getServer().getId(), stagingRepository.getUrl());
    return result;
  }
}
