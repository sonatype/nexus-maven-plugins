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
import java.util.ArrayList;
import java.util.List;

import com.sonatype.nexus.staging.api.dto.StagingActionDTO;
import com.sonatype.nexus.staging.client.Profile;
import com.sonatype.nexus.staging.client.StagingRuleFailuresException;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;
import com.sonatype.nexus.staging.client.StagingWorkflowV3Service;

import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.core.NexusStatus;
import org.sonatype.nexus.client.core.exception.NexusClientAccessForbiddenException;
import org.sonatype.nexus.client.core.exception.NexusClientErrorResponseException;
import org.sonatype.nexus.client.core.exception.NexusClientNotFoundException;
import org.sonatype.nexus.maven.staging.ErrorDumper;
import org.sonatype.nexus.maven.staging.StagingAction;
import org.sonatype.nexus.maven.staging.deploy.DeployableArtifact;
import org.sonatype.nexus.maven.staging.deploy.StagingRepository;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.component.annotations.Component;

/**
 * Full staging V2 deploy strategy. It perform local staging and remote staging (on remote Nexus).
 *
 * @author cstamas
 * @since 1.1
 */
@Component(role = DeployStrategy.class, hint = Strategies.STAGING)
public class StagingDeployStrategy
    extends AbstractStagingDeployStrategy
{

  /**
   * Performs local staging, but obeying the matched staging profile (to keep locally staged artifacts separated, as
   * they will end up in Nexus). For this, several REST calls are made against Nexus, to perform staging profile
   * match
   * if needed.
   */
  @Override
  public void deployPerModule(final DeployPerModuleRequest request)
      throws ArtifactInstallationException, ArtifactDeploymentException, MojoExecutionException
  {
    getLogger().info(
        "Performing local staging (local stagingDirectory=\""
            + request.getParameters().getStagingDirectoryRoot().getAbsolutePath() + "\")...");
    final StagingParameters parameters = getAsStagingParameters(request.getParameters());
    initRemoting(request.getMavenSession(), parameters);
    if (!request.getDeployableArtifacts().isEmpty()) {
      // we match only for 1st in list!
      final String profileId =
          selectStagingProfile(parameters,
              request.getDeployableArtifacts().get(0).getArtifact());
      final File stagingDirectory =
          getStagingDirectory(request.getParameters().getStagingDirectoryRoot(), profileId);
      // deploys always to same stagingDirectory
      for (DeployableArtifact deployableArtifact : request.getDeployableArtifacts()) {
        final ArtifactRepository stagingRepository = getArtifactRepositoryForDirectory(stagingDirectory);
        install(deployableArtifact.getFile(), deployableArtifact.getArtifact(), stagingRepository,
            stagingDirectory);
      }
    }
    else {
      getLogger().info("Nothing to locally stage?");
    }
  }

  /**
   * Performs Nexus staging of locally staged artifacts.
   */
  @Override
  public void finalizeDeploy(final FinalizeDeployRequest request)
      throws ArtifactDeploymentException, MojoExecutionException
  {
    getLogger().info("Performing remote staging...");
    final StagingParameters parameters = getAsStagingParameters(request.getParameters());
    initRemoting(request.getMavenSession(), parameters);
    final File stageRoot = request.getParameters().getStagingDirectoryRoot();
    final File[] localStageRepositories = stageRoot.listFiles();
    if (localStageRepositories == null) {
      getLogger().info("We have nothing locally staged, bailing out.");
      return;
    }
    final NexusClient nexusClient = getRemoting().getNexusClient();
    final NexusStatus nexusStatus = nexusClient.getNexusStatus();
    getLogger().info(
        String.format(" * Connected to Nexus at %s, is version %s and edition \"%s\"",
            nexusClient.getConnectionInfo().getBaseUrl(), nexusStatus.getVersion(), nexusStatus.getEditionLong()));

    final List<StagingRepository> zappedStagingRepositories = new ArrayList<StagingRepository>();
    for (File profileDirectory : localStageRepositories) {
      if (!profileDirectory.isDirectory()) {
        continue;
      }

      // we do remote staging
      final String profileId = profileDirectory.getName();
      getLogger().info("");
      getLogger().info(" * Remote staging into staging profile ID \"" + profileId + "\"");

      try {
        final Profile stagingProfile = getRemoting().getStagingWorkflowV2Service().selectProfile(profileId);
        final StagingRepository stagingRepository = beforeUpload(parameters, stagingProfile);
        zappedStagingRepositories.add(stagingRepository);
        getLogger().info(" * Uploading locally staged artifacts to profile " + stagingProfile.getName());
        deployUp(request.getMavenSession(),
            getStagingDirectory(request.getParameters().getStagingDirectoryRoot(), profileId),
            getDeploymentArtifactRepositoryForNexusStagingRepository(stagingRepository));
        getLogger().info(" * Upload of locally staged artifacts finished.");
        afterUpload(parameters, stagingRepository);
      }
      catch (NexusClientNotFoundException e) {
        afterUploadFailure(parameters, zappedStagingRepositories, e);
        getLogger().error("Remote staging finished with a failure: " + e.getMessage());
        getLogger().error("");
        getLogger().error("Possible causes of 404 Not Found error:");
        getLogger().error(
            " * your local workspace is \"dirty\" with previous runs, that locally staged artifacts? Run \"mvn clean\"...");
        getLogger().error(
            " * remote Nexus got the profile with ID \"" + profileId
                + "\" removed during this build? Get to Nexus admin...");
        throw new ArtifactDeploymentException("Remote staging failed: " + e.getMessage(), e);
      }
      catch (NexusClientAccessForbiddenException e) {
        afterUploadFailure(parameters, zappedStagingRepositories, e);
        getLogger().error("Remote staging finished with a failure: " + e.getMessage());
        getLogger().error("");
        getLogger().error("Possible causes of 403 Forbidden:");
        getLogger().error(
            " * you have no permissions to stage against profile with ID \"" + profileId
                + "\"? Get to Nexus admin...");
        throw new ArtifactDeploymentException("Remote staging failed: " + e.getMessage(), e);
      }
      catch (Exception e) {
        afterUploadFailure(parameters, zappedStagingRepositories, e);
        getLogger().error("Remote staging finished with a failure: " + e.getMessage());
        throw new ArtifactDeploymentException("Remote staging failed: " + e.getMessage(), e);
      }
    }
    getLogger().info(
        "Remote staged " + zappedStagingRepositories.size() + " repositories, finished with success.");
    
    if (!parameters.isSkipStagingRepositoryClose() && parameters.isReleaseAfterClose()) {
      releaseAfterClose(parameters, zappedStagingRepositories);
    }
  }
  
  protected void releaseAfterClose(final StagingParameters parameters, final List<StagingRepository> stagedRepositories)
      throws MojoExecutionException
  {
    getLogger().info("Remote staging repositories are being released...");
    final List<String> stagedRepositoryIds = Lists.newArrayList(Collections2.transform(stagedRepositories,
        new Function<StagingRepository, String>()
        {
          @Override
          public String apply(StagingRepository input) {
            return input.getRepositoryId();
          }
        }));
    final StagingWorkflowV2Service stagingWorkflow = getRemoting().getStagingWorkflowV2Service();
    try {
      if (stagingWorkflow instanceof StagingWorkflowV3Service) {
        final StagingWorkflowV3Service v3 = (StagingWorkflowV3Service) stagingWorkflow;
        StagingActionDTO action = new StagingActionDTO();
        action.setDescription(parameters.getActionDescription(StagingAction.RELEASE));
        action.setStagedRepositoryIds(stagedRepositoryIds);
        action.setAutoDropAfterRelease(parameters.isAutoDropAfterRelease());
        v3.releaseStagingRepositories(action);
      }
      else {
        stagingWorkflow.releaseStagingRepositories(parameters.getActionDescription(StagingAction.RELEASE),
            stagedRepositoryIds.toArray(new String[stagedRepositoryIds.size()]));
      }
    }
    catch (NexusClientErrorResponseException e) {
      ErrorDumper.dumpErrors(getLogger(), e);
      // fail the build
      throw new MojoExecutionException("Could not perform action: Nexus ErrorResponse received!", e);
    }
    catch (StagingRuleFailuresException e) {
      ErrorDumper.dumpErrors(getLogger(), e);
      // fail the build
      throw new MojoExecutionException("Could not perform action: there are failing staging rules!", e);
    }
    getLogger().info("Remote staging repositories released.");
  }

}
