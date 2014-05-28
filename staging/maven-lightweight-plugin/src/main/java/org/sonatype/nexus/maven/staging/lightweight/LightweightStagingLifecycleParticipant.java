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
      remoteNexus = createRemoteNexus(session, secDispatcher, parameters);
      profile = remoteNexus.getStagingWorkflowService().selectProfile(parameters.getStagingProfileId());
      stagingRepositoryId = remoteNexus.getStagingWorkflowService().startStaging(profile, topLevelProjectGav, null);
      log.info("Nexus staging repository {} created to receive deploy of {}...", stagingRepositoryId,
          topLevelProjectGav);
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

    final String stagingRepositoryUrl = remoteNexus.getStagingWorkflowService()
        .startedRepositoryBaseUrl(profile, stagingRepositoryId);
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

  @Override
  public void afterSessionEnd(final MavenSession session)
      throws MavenExecutionException
  {
    if (parameters == null) {
      // bail out
      return;
    }
    if (session.getResult().hasExceptions()) {
      if (!parameters.isKeepStagingRepositoryOnFailure()) {
        log.info("Dropping staging repository {} created for {} due to build failure...", stagingRepositoryId,
            topLevelProjectGav);
        remoteNexus.getStagingWorkflowService().dropStagingRepositories(stagingRepositoryId);
      }
    }
    else {
      try {
        log.info("Closing staging repository {} created for {}...", stagingRepositoryId, topLevelProjectGav);
        remoteNexus.getStagingWorkflowService().finishStaging(profile, stagingRepositoryId, topLevelProjectGav);
        if (!parameters.isSkipStagingRepositoryClose() && parameters.isAutoReleaseAfterClose()) {
          log.info("Releasing staging repository {} created for {}...", stagingRepositoryId, topLevelProjectGav);
          releaseAfterClose();
        }
      }
      catch (StagingRuleFailuresException e) {
        dumpErrors(e);
        if (!parameters.isKeepStagingRepositoryOnCloseRuleFailure()) {
          remoteNexus.getStagingWorkflowService().dropStagingRepositories(stagingRepositoryId);
        }
        throw new MavenExecutionException("Remote staging failed: " + e.getMessage(), e);
      }
    }
  }

  // ==

  private Parameters createParameters(final MavenSession session) {
    final String nexusUrl = session.getTopLevelProject().getProperties().getProperty("staging.nexusUrl");
    final String serverId = session.getTopLevelProject().getProperties().getProperty("staging.serverId");
    final String profileId = session.getTopLevelProject().getProperties().getProperty("staging.profileId");

    return new Parameters(nexusUrl, serverId, profileId);
  }

  private RemoteNexus createRemoteNexus(final MavenSession session,
                                        final SecDispatcher secDispatcher,
                                        final Parameters parameters)
  {
    return new RemoteNexus(log, session, secDispatcher, parameters);
  }

  protected void releaseAfterClose()
      throws MavenExecutionException
  {
    final StagingWorkflowV2Service stagingWorkflow = remoteNexus.getStagingWorkflowService();
    final String message = topLevelProjectGav;
    try {
      if (stagingWorkflow instanceof StagingWorkflowV3Service) {
        final StagingWorkflowV3Service v3 = (StagingWorkflowV3Service) stagingWorkflow;
        StagingActionDTO action = new StagingActionDTO();
        action.setDescription(message);
        action.setStagedRepositoryIds(Collections.singletonList(stagingRepositoryId));
        action.setAutoDropAfterRelease(parameters.isAutoDropAfterRelease());
        v3.releaseStagingRepositories(action);
      }
      else {
        stagingWorkflow.releaseStagingRepositories(message, stagingRepositoryId);
      }
    }
    catch (NexusClientErrorResponseException e) {
      dumpErrors(e);
      // fail the build
      throw new MavenExecutionException("Could not perform action: Nexus ErrorResponse received!", e);
    }
    catch (StagingRuleFailuresException e) {
      dumpErrors(e);
      // fail the build
      throw new MavenExecutionException("Could not perform action: there are failing staging rules!", e);
    }
  }

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
          log.error("    * {}", unfick(message));
        }
      }
      log.error("");
    }
    log.error("");
  }

  private void dumpErrors(final NexusClientErrorResponseException e) {
    log.error("");
    log.error("Nexus Error Response: {} - {}", e.getResponseCode(), e.getReasonPhrase());
    for (NexusClientErrorResponseException.ErrorMessage errorEntry : e.errors()) {
      log.error("  {} - {}", unfick(errorEntry.getId()),
          unfick(errorEntry.getMessage()));
    }
    log.error("");
  }

  private String unfick(final String str) {
    if (str != null) {
      return str.replace("&quot;", "").replace("&lt;b&gt;", "").replace("&lt;/b&gt;", "");
    }
    return null;
  }

}
