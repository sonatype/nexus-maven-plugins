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
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.sonatype.nexus.staging.api.dto.StagingActionDTO;
import com.sonatype.nexus.staging.client.Profile;
import com.sonatype.nexus.staging.client.StagingRuleFailuresException;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;
import com.sonatype.nexus.staging.client.StagingWorkflowV3Service;

import org.sonatype.nexus.client.core.exception.NexusClientAccessForbiddenException;
import org.sonatype.nexus.client.core.exception.NexusClientErrorResponseException;
import org.sonatype.nexus.client.core.exception.NexusClientNotFoundException;
import org.sonatype.nexus.maven.staging.ErrorDumper;
import org.sonatype.nexus.maven.staging.StagingAction;
import org.sonatype.nexus.maven.staging.deploy.DeployableArtifact;
import org.sonatype.nexus.maven.staging.deploy.StagingRepository;
import org.sonatype.nexus.maven.staging.remote.Parameters;
import org.sonatype.nexus.maven.staging.remote.RemoteNexus;

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
    log.info(
        "Performing local staging (local stagingDirectory=\"{}\")...",
        request.getParameters().getStagingDirectoryRoot().getAbsolutePath());
    if (!request.getDeployableArtifacts().isEmpty()) {
      // we match only for 1st in list!
      final RemoteNexus remoteNexus = createRemoteNexus(request.getMavenSession(), request.getParameters());
      request.setRemoteNexus(remoteNexus); // to reuse if this module is last and will perform finalizeDeploy too
      final String profileId =
          selectStagingProfile(request.getParameters(), remoteNexus,
              request.getDeployableArtifacts().get(0).getArtifact());

      // Store local nexus staging repositories in folder stagingDirectoryRoot/MD5OfNexusUrl/profileId
      // Because multiple nexus instances might contain the same profileId
      String stagingFolderName = generateMD5String(request.getParameters().getNexusUrl()) + File.separator + profileId;
      final File stagingDirectory =
          getStagingDirectory(request.getParameters().getStagingDirectoryRoot(), stagingFolderName);
      // deploys always to same stagingDirectory
      for (DeployableArtifact deployableArtifact : request.getDeployableArtifacts()) {
        final ArtifactRepository stagingRepository = getArtifactRepositoryForDirectory(stagingDirectory);
        install(deployableArtifact.getFile(), deployableArtifact.getArtifact(), stagingRepository,
            stagingDirectory, null);
      }
      storeNexusUrl(request, stagingDirectory.getParentFile());
    }
    else {
      log.info("Nothing to locally stage?");
    }
  }

  /**
   * Store nexusUrl in a separate file. It will be used when deploying to remote remote staging repository.
   *
   * @param request
   * @param stagingDirectory
   * @throws MojoExecutionException
   */
  private void storeNexusUrl(DeployPerModuleRequest request, File stagingDirectory) throws MojoExecutionException
  {
    try{
      final File nexusUrlFile = new File(stagingDirectory, ".nexusUrl");
      if(nexusUrlFile.exists()) {
        return;
      }
      Files.write(request.getParameters().getNexusUrl(), nexusUrlFile, Charset.forName("UTF-8"));
    } catch(IOException e) {
      throw new MojoExecutionException("Failed to store nexus url", e);
    }
  }

  private String readNexusUrl(File profileDirectory) throws MojoExecutionException
  {
    try
    {
      File nexusUrlFile = new File(profileDirectory, ".nexusUrl");
      return Files.readFirstLine(nexusUrlFile, Charset.forName("UTF-8"));
    } catch(IOException e) {
      throw new MojoExecutionException("Failed to read nexus url", e);
    }
  }

  /**
   * Performs Nexus staging of locally staged artifacts.
   */
  @Override
  public void finalizeDeploy(final FinalizeDeployRequest request)
      throws ArtifactDeploymentException, MojoExecutionException
  {
    log.info("Performing remote staging...");
    final File stageRoot = request.getParameters().getStagingDirectoryRoot();
    final File[] nexusUrlFolders = stageRoot.listFiles();
    if (nexusUrlFolders == null) {
      log.info("We have nothing locally staged, bailing out.");
      return;
    }
    if (request.getRemoteNexus() == null) {
      // this happens from stage-deployed mojo, where 1st pass already locally staged
      // but there is no client yet in 2nd invocation of maven
      request.setRemoteNexus(createRemoteNexus(request.getMavenSession(), request.getParameters()));
    }

    final Map<String, List<StagingRepository>> zappedStagingRepositories = Maps.newHashMap();
    for (File nexusUrlFolder : nexusUrlFolders)
    {
      if (!nexusUrlFolder.isDirectory())
      {
        continue;
      }

      String nexusUrl = readNexusUrl(nexusUrlFolder);
      request.getParameters().setNexusUrl(nexusUrl); // All nexus staging configurations are reused except nexus url
      RemoteNexus remoteNexus = createRemoteNexus(request.getMavenSession(), request.getParameters());
      zappedStagingRepositories.put(nexusUrl, Lists.<StagingRepository>newArrayList());
      log.info(" * Remote staging into staging nexus url \"{}\"", nexusUrl);
      final File[] localStagingRepositories = nexusUrlFolder.listFiles();
      for (File localStagingRepository : localStagingRepositories)
      {
        // .nexusUrl file is also in this folder
        if(!localStagingRepository.isDirectory()) {
          continue;
        }
        // we do remote staging
        final String profileId = localStagingRepository.getName();
        log.info("");
        log.info(" * Remote staging into staging profile ID \"{}\"", profileId);

        try
        {
          final Profile stagingProfile = remoteNexus.getStagingWorkflowV2Service().selectProfile(profileId);
          final StagingRepository stagingRepository = beforeUpload(request.getParameters(), remoteNexus, stagingProfile);
          zappedStagingRepositories.get(nexusUrl).add(stagingRepository);
          log.info(" * Uploading locally staged artifacts to profile {}", stagingProfile.name());
          deployUp(request.getMavenSession(), getStagingDirectory(nexusUrlFolder, profileId),
                  createDeploymentArtifactRepository(remoteNexus.getServer().getId(), stagingRepository.getUrl()));
          log.info(" * Upload of locally staged artifacts finished.");
          afterUpload(request.getParameters(), remoteNexus, stagingRepository);
        } catch (NexusClientNotFoundException e)
        {
          afterUploadFailure(request, zappedStagingRepositories, e);
          log.error("Remote staging finished with a failure: {}", e.getMessage());
          log.error("");
          log.error("Possible causes of 404 Not Found error:");
          log.error(
                  " * your local workspace is \"dirty\" with previous runs, that locally staged artifacts? Run \"mvn clean\"...");
          log.error(
                  " * remote Nexus got the profile with ID \"{}\" removed during this build? Get to Nexus admin...",
                  profileId);
          throw new ArtifactDeploymentException("Remote staging failed: " + e.getMessage(), e);
        } catch (NexusClientAccessForbiddenException e)
        {
          afterUploadFailure(request, zappedStagingRepositories, e);
          log.error("Remote staging finished with a failure: {}", e.getMessage());
          log.error("");
          log.error("Possible causes of 403 Forbidden:");
          log.error(
                  " * you have no permissions to stage against profile with ID \"{}\"? Get to Nexus admin...", profileId);
          throw new ArtifactDeploymentException("Remote staging failed: " + e.getMessage(), e);
        } catch (Exception e)
        {
          afterUploadFailure(request, zappedStagingRepositories, e);
          log.error("Remote staging finished with a failure: {}", e.getMessage());
          throw new ArtifactDeploymentException("Remote staging failed: " + e.getMessage(), e);
        }
      }
      log.info("Remote staged {} repositories to {}, finished with success.", zappedStagingRepositories.get(nexusUrl).size(), nexusUrl);
    }

    if (!request.getParameters().isSkipStagingRepositoryClose() && request.getParameters().isAutoReleaseAfterClose())
    {
      RemoteNexus remoteNexus;
      for(Map.Entry<String, List<StagingRepository>> reposOfEachNexusInstance : zappedStagingRepositories.entrySet())
      {
        request.getParameters().setNexusUrl(reposOfEachNexusInstance.getKey());
        remoteNexus = createRemoteNexus(request.getMavenSession(), request.getParameters());
        releaseAfterClose(request.getParameters(), remoteNexus, reposOfEachNexusInstance.getValue());
      }
    }
  }

  private void afterUploadFailure(FinalizeDeployRequest request, Map<String, List<StagingRepository>> zappedStagingRepositories, Exception e) throws MojoExecutionException
  {
    RemoteNexus remoteNexus;
    for(Map.Entry<String, List<StagingRepository>> reposOfEachNexusInstance : zappedStagingRepositories.entrySet())
    {
      request.getParameters().setNexusUrl(reposOfEachNexusInstance.getKey());
      remoteNexus = createRemoteNexus(request.getMavenSession(), request.getParameters());
      afterUploadFailure(request.getParameters(), remoteNexus, reposOfEachNexusInstance.getValue(), e);
    }
  }

  protected void releaseAfterClose(final Parameters parameters, final RemoteNexus remoteNexus,
                                   final List<StagingRepository> stagedRepositories)
      throws MojoExecutionException
  {
    log.info("Remote staging repositories are being released...");
    final List<String> stagedRepositoryIds = Lists.newArrayList(Collections2.transform(stagedRepositories,
        new Function<StagingRepository, String>()
        {
          @Override
          public String apply(StagingRepository input) {
            return input.getRepositoryId();
          }
        }));
    final StagingWorkflowV2Service stagingWorkflow = remoteNexus.getStagingWorkflowV2Service();
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
      ErrorDumper.dumpErrors(log, e);
      // fail the build
      throw new MojoExecutionException("Could not perform action: Nexus ErrorResponse received!", e);
    }
    catch (StagingRuleFailuresException e) {
      ErrorDumper.dumpErrors(log, e);
      // fail the build
      throw new MojoExecutionException("Could not perform action: there are failing staging rules!", e);
    }
    log.info("Remote staging repositories released.");
  }

}
