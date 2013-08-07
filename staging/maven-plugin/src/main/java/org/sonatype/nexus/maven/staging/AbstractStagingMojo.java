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

package org.sonatype.nexus.maven.staging;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.sonatype.maven.mojo.execution.MojoExecution;
import org.sonatype.nexus.maven.staging.deploy.DeployMojo;
import org.sonatype.nexus.maven.staging.workflow.CloseStageRepositoryMojo;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Base class for nexus-staging-maven-plugin mojos, gathering the most common parameters.
 *
 * @author cstamas
 */
public abstract class AbstractStagingMojo
    extends AbstractMojo
{
  // Maven sourced stuff

  /**
   * Maven Session.
   */
  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession mavenSession;

  /**
   * Plugin groupId. While these are accessible from {@link #mojoExecution}, the methods are present only in Maven
   * 3+.
   * Requirement is to work in Maven2 also, hence, we use a "compatible way" to obtain these values instead.
   */
  @Parameter(defaultValue = "${plugin.groupId}", readonly = true, required = true)
  private String pluginGroupId;

  /**
   * Plugin artifactId. While these are accessible from {@link #mojoExecution}, the methods are present only in Maven
   * 3+. Requirement is to work in Maven2 also, hence, we use a "compatible way" to obtain these values instead.
   */
  @Parameter(defaultValue = "${plugin.artifactId}", readonly = true, required = true)
  private String pluginArtifactId;

  /**
   * Plugin version. While these are accessible from {@link #mojoExecution}, the methods are present only in Maven
   * 3+.
   * Requirement is to work in Maven2 also, hence, we use a "compatible way" to obtain these values instead.
   */
  @Parameter(defaultValue = "${plugin.version}", readonly = true, required = true)
  private String pluginVersion;

  /**
   * Mojo execution.
   */
  @Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
  private org.apache.maven.plugin.MojoExecution mojoExecution;

  /**
   * Flag whether Maven is currently in online/offline mode.
   */
  @Parameter(defaultValue = "${settings.offline}", readonly = true, required = true)
  private boolean offline;

  /**
   * Sec Dispatcher.
   */
  @Component
  private SecDispatcher secDispatcher;

  // user supplied parameters (staging related)

  /**
   * Specifies an alternative staging directory to which the project artifacts should be "locally staged". By
   * default,
   * staging directory will be looked for under {@code $}{{@code project.build.directory} {@code /nexus-staging}
   * folder of the first encountered module that has this Mojo defined for execution (Warning: this means, if top
   * level POM is an aggregator, it will be NOT in top level!).
   */
  @Parameter(property = "altStagingDirectory")
  private File altStagingDirectory;

  /**
   * The base URL for a Nexus Professional instance that includes the nexus-staging-plugin. For example, if Nexus is
   * mounted at the server context of {@code /nexus} and running on localhost at port 8081 ( the default install ),
   * then this value should be {@code http://localhost:8081/nexus/}.
   */
  @Parameter(property = "nexusUrl")
  private String nexusUrl;

  /**
   * The ID of the server entry in the Maven settings.xml from which to pick credentials to contact remote Nexus.
   */
  @Parameter(property = "serverId")
  private String serverId;

  /**
   * The repository "description" to pass to Nexus when repository staging workflow step is made. This property is
   * meant for direct CLI invocations mostly, as it "takes it all", this description if given will override any other
   * set in the POM.
   */
  @Parameter(property = "stagingDescription")
  private String stagingDescription;

  /**
   * The key-value pairs to map staging steps to some meaningful descriptions. If no key mapped, description will be
   * defaulted to {@link #getRootProjectGav()} (which is {@code groupId:artifactId:version} of parent POM in
   * reactor).
   * Keys to be mapped are members of the {@link StagingAction} enum (case insensitive), and they are:
   * <ul>
   * <li>START- to be used when staging repository is created (staging is started).</li>
   * <li>FINISH - to be used when staging repository is being sealed/closed (staging upload finished).</li>
   * <li>DROP - to be used when staging repository is being dropped (staging repository removed).</li>
   * <li>RELEASE - to be used when staging repository is being released</li>
   * <li>PROMOTE - to be used when staging repository is being promoted</li>
   * </ul>
   */
  @Parameter
  private Map<String, String> stagingDescriptions;

  /**
   * Controls whether the staging repository is kept or not (it will be dropped) in case of staging rule failure when
   * "close" action is performed against it. This is applied in both cases, {@link DeployMojo} and
   * {@link CloseStageRepositoryMojo} invocations.
   */
  @Parameter(property = "keepStagingRepositoryOnCloseRuleFailure")
  private boolean keepStagingRepositoryOnCloseRuleFailure;

  // == getters for stuff above

  protected String getNexusUrl() {
    return nexusUrl;
  }

  protected String getServerId() {
    return serverId;
  }

  protected StagingActionMessages getStagingActionMessages()
      throws MojoExecutionException
  {
    final HashMap<StagingAction, String> messages = new HashMap<StagingAction, String>();
    if (stagingDescriptions != null) {
      for (Map.Entry<String, String> entry : stagingDescriptions.entrySet()) {
        try {
          // this will IllegalArgumentEx if key is not within allowed value set
          // so catch it, and emit MojoExecutionEx as that means clearly user error, misconfiguration
          messages.put(StagingAction.valueOf(entry.getKey().toUpperCase()), entry.getValue());
        }
        catch (IllegalArgumentException e) {
          throw new MojoExecutionException("stagingDescriptions map contains unmappable key: "
              + entry.getKey());
        }

      }
    }
    return new StagingActionMessages(stagingDescription, messages, getRootProjectGav());
  }

  public boolean isKeepStagingRepositoryOnCloseRuleFailure() {
    return keepStagingRepositoryOnCloseRuleFailure;
  }

  protected MavenSession getMavenSession() {
    return mavenSession;
  }

  protected SecDispatcher getSecDispatcher() {
    return secDispatcher;
  }

  protected String getPluginGav() {
    return pluginGroupId + ":" + pluginArtifactId + ":" + pluginVersion;
  }

  protected String getRootProjectGav() {
    final MavenProject rootProject = getFirstProjectWithThisPluginDefined();
    if (rootProject != null) {
      return rootProject.getGroupId() + ":" + rootProject.getArtifactId() + ":" + rootProject.getVersion();
    }
    else {
      return "unknown";
    }
  }

  // == common methods

  /**
   * Throws {@link MojoFailureException} if Maven is invoked offline, as nexus-staging-maven-plugin MUST WORK online.
   *
   * @throws MojoFailureException if Maven is invoked offline.
   */
  protected void failIfOffline()
      throws MojoFailureException
  {
    if (offline) {
      throw new MojoFailureException(
          "Cannot use Staging features in Offline mode, as REST Requests are needed to be made against Nexus even while locally staging only.");
    }
  }

  /**
   * Returns the first project in reactor that has this plugin defined.
   */
  protected MavenProject getFirstProjectWithThisPluginDefined() {
    return MojoExecution.getFirstProjectWithMojoInExecution(mavenSession, pluginGroupId, pluginArtifactId, null);
  }

  /**
   * In case of "ordinary" (reactor) build, it returns {@code true} if the current project is the last one being
   * executed in this build that has this Mojo defined. In case of direct invocation of this Mojo over CLI, it
   * returns
   * {@code true} if the current project is the last one being executed in this build.
   *
   * @return true if last project is being built.
   */
  protected boolean isThisLastProjectWithThisMojoInExecution() {
    if ("default-cli".equals(mojoExecution.getExecutionId())) {
      return MojoExecution.isCurrentTheLastProjectInExecution(mavenSession);
    }
    else {
      // method mojoExecution.getGoal() is added in maven3!
      return isThisLastProjectWithMojoInExecution(mojoExecution.getMojoDescriptor().getGoal());
    }
  }

  /**
   * Returns true if the current project is the last one being executed in this build that has passed in goal
   * execution defined.
   *
   * @return true if last project is being built.
   */
  protected boolean isThisLastProjectWithMojoInExecution(final String goal) {
    return MojoExecution.isCurrentTheLastProjectWithMojoInExecution(mavenSession, pluginGroupId, pluginArtifactId,
        goal);
  }

  /**
   * Returns the working directory root (the one containing all the staged and deferred deploys), that is either set
   * explicitly by user in plugin configuration (see {@link #altStagingDirectory} parameter), or it's location is
   * calculated taking as base the first project in this reactor that will/was executing this plugin.
   */
  protected File getWorkDirectoryRoot() {
    if (altStagingDirectory != null) {
      return altStagingDirectory;
    }
    else {
      final MavenProject firstWithThisMojo = getFirstProjectWithThisPluginDefined();
      if (firstWithThisMojo != null) {
        final File firstWithThisMojoBuildDir;
        if (firstWithThisMojo.getBuild() != null && firstWithThisMojo.getBuild().getDirectory() != null) {
          firstWithThisMojoBuildDir =
              new File(firstWithThisMojo.getBuild().getDirectory()).getAbsoluteFile();
        }
        else {
          firstWithThisMojoBuildDir = new File(firstWithThisMojo.getBasedir().getAbsoluteFile(), "target");
        }
        return new File(firstWithThisMojoBuildDir, "nexus-staging");
      }
      else {
        // top level (invocation place with some sensible defaults)
        // TODO: what can we do here? Do we have MavenProject at all?
        return new File(getMavenSession().getExecutionRootDirectory() + "/target/nexus-staging");
      }
    }
  }

  /**
   * Returns the staging directory root (directory containing the locally staged artifacts), that is either set
   * explicitly by user in plugin configuration (see {@link #altStagingDirectory} parameter), or it's location is
   * calculated taking as base the first project in this reactor that will/was executing this plugin.
   */
  protected File getStagingDirectoryRoot() {
    return new File(getWorkDirectoryRoot(), "staging");
  }

  /**
   * Returns the deferred directory root (directory containing the accumulated artifacts for deferred deploy), that
   * is
   * either set explicitly by user in plugin configuration (see {@link #altStagingDirectory} parameter), or it's
   * location is calculated taking as base the first project in this reactor that will/was executing this plugin.
   */
  protected File getDeferredDirectoryRoot() {
    return new File(getWorkDirectoryRoot(), "deferred");
  }
}
