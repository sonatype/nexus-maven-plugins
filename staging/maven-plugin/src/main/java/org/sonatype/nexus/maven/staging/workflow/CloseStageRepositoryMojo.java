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

package org.sonatype.nexus.maven.staging.workflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.sonatype.nexus.staging.client.StagingRuleFailures;
import com.sonatype.nexus.staging.client.StagingRuleFailuresException;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

import org.sonatype.nexus.maven.staging.ErrorDumper;
import org.sonatype.nexus.maven.staging.StagingAction;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.sonatype.nexus.maven.staging.remote.Parameters;
import org.sonatype.nexus.maven.staging.remote.RemoteNexus;

/**
 * Closes a Nexus staging repository.
 *
 * @author cstamas
 * @since 1.0
 */
@Mojo(name = "close", requiresOnline = true)
public class CloseStageRepositoryMojo
    extends AbstractStagingBuildActionMojo
{
  @Override
  public void doExecute(final StagingWorkflowV2Service stagingWorkflow)
      throws MojoExecutionException, MojoFailureException
  {
    final Map<String, String[]> stagingRepositories = getStagingRepositoryIds();

    for(Map.Entry<String, String[]> stagingRepo : stagingRepositories.entrySet())
    {
      StagingWorkflowV2Service stagingWorkflowV2Service = null;
      try {
        stagingWorkflowV2Service = createStagingWorkflowService(getNexusUrl());
        getLog().info("Closing staging repository with IDs=" + Arrays.toString(stagingRepo.getValue()) + ", nexus url: " + stagingRepo.getKey());
        stagingWorkflowV2Service.finishStagingRepositories(getDescriptionWithDefaultsForAction(StagingAction.FINISH),
                stagingRepo.getValue());
        getLog().info("Closed");
      } catch (StagingRuleFailuresException e) {
        // report staging repository failures
        ErrorDumper.dumpErrors(getLog(), e);

        // drop the repository (this will break exception chain if there's new failure, like network)
        if (!isKeepStagingRepositoryOnCloseRuleFailure()) {
          final List<String> failedRepositories = new ArrayList<String>();
          for (StagingRuleFailures failures : e.getFailures()) {
            failedRepositories.add(failures.getRepositoryId());
          }
          final String msg = "Rule failure during close of staging repositories: " + failedRepositories;

          getLog().error("Cleaning up remote stage repositories after a " + msg);

          dropStagingRepos(getDescriptionWithDefaultsForAction(StagingAction.DROP) + " ("
                  + msg + ").", stagingRepositories);
        }
        // fail the build
        throw new MojoExecutionException("Could not perform action: there are failing staging rules!", e);
      }
    }

  }

  private void dropStagingRepos(String msg, Map<String, String[]> stagingRepositories) throws MojoExecutionException{
    for(Map.Entry<String, String[]> stagingRepos : stagingRepositories.entrySet()) {
      StagingWorkflowV2Service stagingWorkflowV2Service = createStagingWorkflowService(stagingRepos.getKey());
      stagingWorkflowV2Service.dropStagingRepositories(msg, stagingRepos.getValue());
    }

  }
}
