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

package org.sonatype.nexus.maven.staging.lightweight;

import java.util.Collections;

import com.sonatype.nexus.staging.api.dto.StagingActionDTO;
import com.sonatype.nexus.staging.client.Profile;
import com.sonatype.nexus.staging.client.ProfileMatchingParameters;
import com.sonatype.nexus.staging.client.StagingRuleFailures;
import com.sonatype.nexus.staging.client.StagingRuleFailures.RuleFailure;
import com.sonatype.nexus.staging.client.StagingRuleFailuresException;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;
import com.sonatype.nexus.staging.client.StagingWorkflowV3Service;

import org.sonatype.nexus.client.core.exception.NexusClientAccessForbiddenException;
import org.sonatype.nexus.client.core.exception.NexusClientErrorResponseException;
import org.sonatype.nexus.client.core.exception.NexusClientNotFoundException;
import org.sonatype.nexus.maven.staging.remote.Parameters;
import org.sonatype.nexus.maven.staging.remote.RemoteNexus;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import com.google.common.base.Strings;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Repository;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle participant that is meant to perform "lightweight" (simpler) staging workflow that the
 * nexus-staging-plugin
 * does.
 */
@Component(role = AbstractMavenLifecycleParticipant.class,
    hint = "org.sonatype.nexus.maven.staging.lightweight.LightweightStagingLifecycleParticipant")
public class LightweightStagingLifecycleParticipant
    extends AbstractMavenLifecycleParticipant
{
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(LightweightStagingLifecycleParticipant.class);

  @Requirement
  private SecDispatcher secDispatcher;

  private String topLevelProjectGav;

  private Parameters parameters;

  private RemoteNexus remoteNexus;

  private Profile profile;

  private String stagingRepositoryId;

  private String stagingRepositoryUrl;

  /**
   * Method invoked after all the projects have be read up by Maven. Here, NexusClient is being built, and
   * staging profile is determined, and staging is started for given profile. The repository URLs are then
   * "redirected" to deploy into newly created staging repository.
   */
  @Override
  public void afterProjectsRead(final MavenSession session)
      throws MavenExecutionException
  {
    if (session.getTopLevelProject().getVersion().contains("SNAPSHOT") || !session.getGoals().contains("deploy")) {
      // bail out of snapshot or no deploy goal invoked
      return;
    }
    topLevelProjectGav = String
        .format("%s:%s:%s", session.getTopLevelProject().getGroupId(), session.getTopLevelProject().getArtifactId(),
            session.getTopLevelProject().getVersion());
    log.info("Using lightweight-staging to perform deploy");
    try {
      parameters = createParameters(session);
      remoteNexus = new RemoteNexus(log, session, secDispatcher, parameters);
      profile = fetchProfile(session, parameters, remoteNexus);
    }
    catch (IllegalArgumentException e) {
      log.info("Illegal staging configuration: {}", e.getMessage());
      throw new MavenExecutionException("Illegal staging configuration: " + e.getMessage(), e);
    }
    catch (NexusClientAccessForbiddenException e) {
      log.error("Lack of permission to access staging on Nexus");
      throw new MavenExecutionException("Lack of permission to access staging", e);
    }
    catch (NexusClientNotFoundException e) {
      log.info("Illegal staging configuration: profile does not exists or no access to it");
      throw new MavenExecutionException("Illegal staging configuration: profile does not exists or no access to it", e);
    }

    try {
      final String startMessage = topLevelProjectGav;
      stagingRepositoryId = remoteNexus.getStagingWorkflowService().startStaging(profile, startMessage, null);
      stagingRepositoryUrl = remoteNexus.getStagingWorkflowService()
          .startedRepositoryBaseUrl(profile, stagingRepositoryId);
      log.info(" * Nexus staging repository {} created...", stagingRepositoryId);
    }
    catch (NexusClientNotFoundException e) {
      log.error("Unexpected Nexus response during staging start", e);
      throw new MavenExecutionException("Remote staging failed: " + e.getMessage(), e);
    }
    catch (NexusClientAccessForbiddenException e) {
      log.error("Lack of permission to access staging", e);
      throw new MavenExecutionException("Remote staging failed: " + e.getMessage(), e);
    }
    catch (Exception e) {
      log.error("Error during staging start", e);
      throw new MavenExecutionException("Remote staging failed: " + e.getMessage(), e);
    }

    log.info(" * Deploy will use URL: {}", stagingRepositoryUrl);
    // set the deploy URLs
    for (MavenProject project : session.getProjects()) {
      if (project.getDistributionManagement() != null && project.getDistributionManagement().getRepository() != null) {
        final Repository repository = project.getDistributionManagement().getRepository();
        // redirect release repository to staging repository and force auth and layout
        repository.setId(parameters.getServerId());
        repository.setUrl(stagingRepositoryUrl);
        repository.setLayout("default");
      }
    }
  }

  /**
   * Method invoked at the build end. On successful build outcome, it will close the staging repository, and if needed,
   * release it too. In case of build failure, if required, repository will be dropped.
   */
  @Override
  public void afterSessionEnd(final MavenSession session)
      throws MavenExecutionException
  {
    if (parameters == null) {
      // bail out, as we did not even kick in
      return;
    }
    final StagingWorkflowV2Service stagingWorkflow = remoteNexus.getStagingWorkflowService();
    final String endMessage = topLevelProjectGav;
    if (session.getResult().hasExceptions()) {
      if (!parameters.isKeepStagingRepositoryOnFailure()) {
        log.info(" * Dropping staging repository {} due to build failure...", stagingRepositoryId);
        stagingWorkflow.dropStagingRepositories(stagingRepositoryId);
      }
    }
    else {
      if (!parameters.isSkipStagingRepositoryClose()) {
        try {
          log.info(" * Closing staging repository {}...", stagingRepositoryId);
          stagingWorkflow.finishStaging(profile, stagingRepositoryId, topLevelProjectGav);
          if (!parameters.isAutoReleaseAfterClose()) {
            log.info(" * Build artifacts are now accessible from URL: {}",
                remoteNexus.getNexusClient().getConnectionInfo().getBaseUrl() + "content/repositories/" +
                    stagingRepositoryId);
          }
          else {
            log.info(" * Releasing staging repository {}...", stagingRepositoryId);
            try {
              if (stagingWorkflow instanceof StagingWorkflowV3Service) {
                final StagingWorkflowV3Service v3 = (StagingWorkflowV3Service) stagingWorkflow;
                StagingActionDTO action = new StagingActionDTO();
                action.setDescription(endMessage);
                action.setStagedRepositoryIds(Collections.singletonList(stagingRepositoryId));
                action.setAutoDropAfterRelease(parameters.isAutoDropAfterRelease());
                v3.releaseStagingRepositories(action);
              }
              else {
                stagingWorkflow.releaseStagingRepositories(endMessage, stagingRepositoryId);
              }
            }
            catch (StagingRuleFailuresException e) {
              dumpErrors(e);
              if (!parameters.isKeepStagingRepositoryOnCloseRuleFailure()) {
                stagingWorkflow.dropStagingRepositories(stagingRepositoryId);
              }
              throw new MavenExecutionException(
                  "Could not release staging repository: there are failing staging rules!", e);
            }
            catch (NexusClientErrorResponseException e) {
              dumpErrors(e);
              // fail the build, this is some communication error?
              throw new MavenExecutionException("Could not release staging repository: Nexus ErrorResponse received!",
                  e);
            }
          }
        }
        catch (StagingRuleFailuresException e) {
          dumpErrors(e);
          if (!parameters.isKeepStagingRepositoryOnCloseRuleFailure()) {
            stagingWorkflow.dropStagingRepositories(stagingRepositoryId);
          }
          throw new MavenExecutionException("Could not close staging repository: there are failing staging rules!", e);
        }
        catch (NexusClientErrorResponseException e) {
          dumpErrors(e);
          // fail the build, this is some communication error?
          throw new MavenExecutionException("Could not close staging repository: Nexus ErrorResponse received!", e);
        }
      }
    }
  }

  // ======

  /**
   * Extract parameters from various sources. Currently, it is TLP properties.
   */
  private Parameters createParameters(final MavenSession session) {
    final String nexusUrl = session.getTopLevelProject().getProperties().getProperty("staging.nexusUrl");
    final String serverId = session.getTopLevelProject().getProperties().getProperty("staging.serverId");
    final String profileId = session.getTopLevelProject().getProperties().getProperty("staging.profileId");

    log.debug("nexusUrl={}. serverId={}, profileId={}", nexusUrl, serverId, profileId);

    return new Parameters(nexusUrl, serverId, profileId);
  }

  /**
   * Fetches {@link Profile} to be used for staging. It's is either explicitly given by user in parameters, see {@link
   * #createParameters(MavenSession)}, or if not, TLP is matched on remote server side for profile.
   */
  private Profile fetchProfile(final MavenSession session, final Parameters parameters, final RemoteNexus remoteNexus) {
    Profile stagingProfile;
    if (Strings.isNullOrEmpty(parameters.getStagingProfileId())) {
      final ProfileMatchingParameters params =
          new ProfileMatchingParameters(
              session.getTopLevelProject().getGroupId(),
              session.getTopLevelProject().getArtifactId(),
              session.getTopLevelProject().getVersion());
      stagingProfile = remoteNexus.getStagingWorkflowService().matchProfile(params);
      log.info(
          " * Using staging profile \"" + stagingProfile.name() + "\" (matched by Nexus against TLP).");
    }
    else {
      stagingProfile = remoteNexus.getStagingWorkflowService().selectProfile(parameters.getStagingProfileId());
      log.info(
          " * Using staging profile \"" + stagingProfile.name() + "\" (set by user).");
    }
    return stagingProfile;
  }

  /**
   * Format staging rule failures for console output.
   */
  private void dumpErrors(final StagingRuleFailuresException e) {
    log.error("");
    log.error("Nexus Staging Rules Failure Report");
    log.error("==================================");
    log.error("");
    for (StagingRuleFailures failure : e.getFailures()) {
      log.error("Repository \"{}\" failures", failure.getRepositoryId());
      for (RuleFailure ruleFailure : failure.getFailures()) {
        log.error("  Rule \"{}\" failures", ruleFailure.getRuleName());
        for (String message : ruleFailure.getMessages()) {
          log.error("    * {}", shave(message));
        }
      }
      log.error("");
    }
    log.error("");
  }

  /**
   * Format Nexus Client error response for console output.
   */
  private void dumpErrors(final NexusClientErrorResponseException e) {
    log.error("");
    log.error("Nexus Error Response: {} - {}", e.getResponseCode(), e.getReasonPhrase());
    for (NexusClientErrorResponseException.ErrorMessage errorEntry : e.errors()) {
      log.error("  {} - {}", shave(errorEntry.getId()),
          shave(errorEntry.getMessage()));
    }
    log.error("");
  }

  /**
   * Shave off some constructs being ugly on console.
   */
  private String shave(final String str) {
    if (str != null) {
      return str.replace("&quot;", "").replace("&lt;b&gt;", "").replace("&lt;/b&gt;", "");
    }
    return null;
  }

}
