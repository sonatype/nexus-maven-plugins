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
import java.util.concurrent.TimeUnit;

import org.sonatype.maven.mojo.execution.MojoExecution;
import org.sonatype.nexus.maven.staging.deploy.DeployMojo;
import org.sonatype.nexus.maven.staging.remote.Parameters;
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
   * default, staging directory will be looked for under {@code $}{{@code project.build.directory} {@code /nexus-staging}
   * folder of the first encountered module that has this Mojo defined for execution (Warning: this means, if top
   * level POM is an aggregator, it will be NOT in top level!).
   */
  @Parameter(property = "altStagingDirectory")
  private File altStagingDirectory;

  /**
   * The base URL for a Nexus Professional instance that includes the nexus-staging-plugin. For example, if Nexus is
   * mounted at context path {@code /nexus} and running on localhost at port 8081 ( the default install ), then this
   * value should be {@code http://localhost:8081/nexus/}. This is not the implicit profile matching URL such as
   * {@code http://localhost:8081/nexus/service/local/staging/deploy/maven2/} or a repository content URL such as
   * {@code http://localhost:8081/nexus/content/repositories/releases/}!
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

  /**
   * Should a staged (closed) repository automatically be released? Evaluated only if repository (or repositories) has
   * been successfully closed.
   *
   * @since 1.5
   */
  @Parameter(property = "autoReleaseAfterClose", defaultValue = "false")
  private boolean autoReleaseAfterClose;

  /**
   * Automatically drop repository after any release action has been successfully executed.
   *
   * @since 1.5
   */
  @Parameter(property = "autoDropAfterRelease", defaultValue = "true")
  private boolean autoDropAfterRelease;

  /**
   * Set the timeout in minutes for a staging operation.
   *
   * @since 1.5
   */
  @Parameter(property = "stagingProgressTimeoutMinutes", defaultValue = "5")
  private int stagingProgressTimeoutMinutes = 5;

  /**
   * Set the staging operation polling pause duration in seconds.
   *
   * @since 1.5
   */
  @Parameter(property = "stagingProgressPauseDurationSeconds", defaultValue = "3")
  private int stagingProgressPauseDurationSeconds = 3;

  /**
   * MAVEN 3+ ONLY. Automatically detect build failures. If {@code true} (default), any build failure
   * will prevent staging deployments. If {@code false}, build failures will not prevent staging deployments.
   * A {@code false} value combined with the Maven {@code -fae} "fail at end" fail strategy will allow staging
   * deployments despite a build failure, matching previous plugin default behavior. There is no reliable method for a
   * plugin to detect a previous build failure using Maven 2.
   *
   * @since 1.6.0
   */
  @Parameter(property = "detectBuildFailures", defaultValue = "true")
  private boolean detectBuildFailures;

  /**
   * Is SSL certificate check validation relaxed? If {@code true}, self signed certificates will be accepted too.
   *
   * @since 1.6.0
   */
  @Parameter(property = "maven.wagon.http.ssl.insecure", defaultValue = "false")
  private boolean sslInsecure;

  /**
   * Is SSL certificate X509 hostname validation disabled? If {@code true}, any hostname will be accepted.
   *
   * @since 1.6.0
   */
  @Parameter(property = "maven.wagon.http.ssl.allowall", defaultValue = "false")
  private boolean sslAllowAll;

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

  public boolean isAutoReleaseAfterClose() {
    return autoReleaseAfterClose;
  }

  public boolean isAutoDropAfterRelease() {
    return autoDropAfterRelease;
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

  protected int getStagingProgressTimeoutMinutes() {
    return stagingProgressTimeoutMinutes;
  }

  protected int getStagingProgressPauseDurationSeconds() {
    return stagingProgressPauseDurationSeconds;
  }

  protected boolean isSslInsecure() { return sslInsecure; }

  protected boolean isSslAllowAll() { return sslAllowAll; }

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
   * returns {@code true} if the current project is the last one being executed in this build. Also, what this method
   * returns depends on user parametrization, how this Mojo should behave when Maven is run with {@code -fae} "fail
   * at end" switch and there are failures happened previously in build.
   *
   * @return true if last project is being built.
   */
  protected boolean isThisLastProjectWithThisMojoInExecution() {
    boolean result;
    if ("default-cli".equals(mojoExecution.getExecutionId())) {
      result = MojoExecution.isCurrentTheLastProjectInExecution(mavenSession);
    }
    else {
      // method mojoExecution.getGoal() is added in maven3!
      result = MojoExecution.isCurrentTheLastProjectWithMojoInExecution(mavenSession, pluginGroupId, pluginArtifactId,
          mojoExecution.getMojoDescriptor().getGoal());
    }
    if (result) {
      try {
        // In case of parallel build, we need to ensure everything else is built
        waitForOtherProjectsIfNeeded();

        if (getMavenSession().getResult().hasExceptions()) {
          if (detectBuildFailures) {
            // log failures found and bail out
            getLog().info("Earlier build failures detected. Staging will not continue.");
            return false;
          }
          else if (!detectBuildFailures) {
            // just log and continue
            getLog().warn(
                "Earlier build failures detected. Staging is configured to not detect build failures, continuing...");
          }
        }
        // we are okay, this was last module and everything else is/was already built
        return true;
      }
      catch (NoSuchMethodError e) {
        // This is Maven2.x and last project, Maven 2x does not expose MavenExecutionResult over API
        getLog().info("Unable to detect build failures with Maven 2, continuing...");
        return true;
      }
    }
    else {
      getLog().info("Execution skipped to the last project...");
      return false;
    }
  }

  /**
   * Method that blocks current thread until every other project has build result. Naturally, this method will
   * never sleep and will immediately return in non-parallel builds (as "topologically last" project will be built as
   * last on single thread of execution). The importance of the method is in case of parallel builds, where
   * "topologically last" project might be built with other modules on same level. In this case, we do want to sleep
   * until every project is built (except "this" project).
   * <p/>Note: this method assumes Maven3 host, and should be called only if running within Maven3. Otherwise,
   * NoSuchMethodError will be thrown.
   */
  protected void waitForOtherProjectsIfNeeded() {
    final MavenProject currentProject = getMavenSession().getCurrentProject();
    // TODO: should we maximize how much to sleep?
    // There could be IT module running in parallel for hours for example
    // But this below will sleep indefinitely...
    while (true) {
      boolean done = true;
      for (MavenProject project : getMavenSession().getProjects()) {
        if (currentProject != project && getMavenSession().getResult().getBuildSummary(project) == null) {
          done = false;
          break;
        } else if (currentProject == project) {
          // we need to break, as "lastProjectWithThisMojo might != lastProjectInReactor"
          // and in that case we would block here indefinitely
          break;
        }
      }
      if (!done) {
        getLog().info("Waiting for other projects build to finish...");
        try {
          Thread.sleep(TimeUnit.SECONDS.toMillis(2l));
        }
        catch (InterruptedException e) {
          // nothing?
        }
      }
      else {
        return;
      }
    }
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

  /**
   * Builds the parameters instance.
   */
  protected Parameters buildParameters()
      throws MojoExecutionException
  {
    try {
      // this below does not validate, it merely passes the set configuration values (even those unused)
      // each strategy will properly validated parameters in their prepare method
      final Parameters parameters = new Parameters(getPluginGav(), getDeferredDirectoryRoot(),
          getStagingDirectoryRoot());
      parameters.setNexusUrl(getNexusUrl());
      parameters.setServerId(getServerId());
      parameters.setKeepStagingRepositoryOnCloseRuleFailure(isKeepStagingRepositoryOnCloseRuleFailure());
      parameters.setAutoReleaseAfterClose(isAutoReleaseAfterClose());
      parameters.setAutoDropAfterRelease(isAutoDropAfterRelease());
      parameters.setStagingActionMessages(getStagingActionMessages());
      parameters.setStagingProgressTimeoutMinutes(getStagingProgressTimeoutMinutes());
      parameters.setStagingProgressPauseDurationSeconds(getStagingProgressPauseDurationSeconds());
      parameters.setSslInsecure(isSslInsecure());
      parameters.setSslAllowAll(isSslAllowAll());
      if (getLog().isDebugEnabled()) {
        getLog().debug(parameters.toString());
      }
      return parameters;
    }
    catch (Exception e) {
      throw new MojoExecutionException("Bad configuration:" + e.getMessage(), e);
    }
  }
}
