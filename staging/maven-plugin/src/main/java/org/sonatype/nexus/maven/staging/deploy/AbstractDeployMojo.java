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

package org.sonatype.nexus.maven.staging.deploy;

import java.util.Map;

import org.sonatype.nexus.maven.staging.AbstractStagingMojo;
import org.sonatype.nexus.maven.staging.deploy.strategy.DeployStrategy;
import org.sonatype.nexus.maven.staging.remote.Parameters;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Abstract class for deploy related mojos.
 *
 * @author cstamas
 * @since 1.0
 */
public abstract class AbstractDeployMojo
    extends AbstractStagingMojo
{

  /**
   * Deploy strategies.
   */
  @Component(role = DeployStrategy.class)
  private Map<String, DeployStrategy> deployStrategies;

  // User configurable parameters

  /**
   * Specifies the profile ID on remote Nexus against which staging should happen. If not given, Nexus will be asked
   * to perform a "match" and that profile will be used.
   */
  @Parameter(property = "stagingProfileId")
  private String stagingProfileId;

  /**
   * Specifies the (opened) staging repository ID on remote Nexus against which staging should happen. If not given,
   * Nexus will be asked to create one for us and that will be used.
   */
  @Parameter(property = "stagingRepositoryId")
  private String stagingRepositoryId;

  /**
   * The key-value pairs to "tag" the staging repository.
   */
  @Parameter
  private Map<String, String> tags;

  /**
   * Controls whether the plugin remove or keep the staging repository that performed an IO exception during upload,
   * hence, it's contents are partial Defaults to {{false}}. If {{true}}, even in case of upload failure, the staging
   * repository (with partial content) will be left as is, left to the user to do whatever he wants.
   */
  @Parameter(property = "keepStagingRepositoryOnFailure")
  private boolean keepStagingRepositoryOnFailure;

  /**
   * Set this to {@code true} to bypass staging repository closing at the workflow end.
   */
  @Parameter(property = "skipStagingRepositoryClose")
  private boolean skipStagingRepositoryClose;

  /**
   * Set this to {@code true} to bypass staging features, and use deferred deploy features only.
   */
  @Parameter(property = "skipStaging")
  private boolean skipStaging;

  // ==

  /**
   * Returns the deploy strategy by key (plexus component hint). If no given strategy found,
   * {@link MojoExecutionException} is thrown.
   */
  protected DeployStrategy getDeployStrategy(final String key)
      throws MojoExecutionException
  {
    final DeployStrategy deployStrategy = deployStrategies.get(key);
    if (deployStrategy == null) {
      throw new MojoExecutionException("DeployStrategy " + key + " not found!");
    }
    return deployStrategy;
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
      parameters.setKeepStagingRepositoryOnFailure(isKeepStagingRepositoryOnFailure());
      parameters.setSkipStagingRepositoryClose(isSkipStagingRepositoryClose());
      parameters.setAutoReleaseAfterClose(isAutoReleaseAfterClose());
      parameters.setAutoDropAfterRelease(isAutoDropAfterRelease());
      parameters.setStagingProfileId(getStagingProfileId());
      parameters.setStagingRepositoryId(getStagingRepositoryId());
      parameters.setStagingActionMessages(getStagingActionMessages());
      parameters.setTags(getTags());
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

  // ==

  protected String getStagingProfileId() {
    return stagingProfileId;
  }

  protected String getStagingRepositoryId() {
    return stagingRepositoryId;
  }

  protected Map<String, String> getTags() {
    return tags;
  }

  protected boolean isKeepStagingRepositoryOnFailure() {
    return keepStagingRepositoryOnFailure;
  }

  protected boolean isSkipStagingRepositoryClose() {
    return skipStagingRepositoryClose;
  }

  protected boolean isSkipStaging() {
    return skipStaging;
  }
}