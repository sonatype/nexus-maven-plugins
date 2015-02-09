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

import java.util.Arrays;
import java.util.Map;

import com.sonatype.nexus.staging.api.dto.StagingActionDTO;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;
import com.sonatype.nexus.staging.client.StagingWorkflowV3Service;

import org.sonatype.nexus.maven.staging.StagingAction;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Releases a single closed Nexus staging repository into a permanent Nexus repository for general consumption.
 *
 * @author cstamas
 * @since 1.0
 */
@Mojo(name = "release", requiresOnline = true)
public class ReleaseStageRepositoryMojo
    extends AbstractStagingBuildActionMojo
{
  @Override
  public void doExecute(final StagingWorkflowV2Service stagingWorkflow)
      throws MojoExecutionException, MojoFailureException
  {
    Map<String, String[]> stagingRepositoryIds = getStagingRepositoryIds();
    for(Map.Entry<String, String[]> stagingRepos : stagingRepositoryIds.entrySet())
    {
      // FIXME: Some duplication here between RcReleaseStageRepositoryMojo and ReleaseStageRepositoryMojo

      getLog().info("Releasing staging repository with IDs=" + Arrays.toString(stagingRepos.getValue())
                    + ", nexusUrl=" + stagingRepos.getKey());

      String description = getDescriptionWithDefaultsForAction(StagingAction.RELEASE);
      StagingWorkflowV2Service stagingWorkflowV2Service = createStagingWorkflowService(stagingRepos.getKey());
      if (stagingWorkflowV2Service instanceof StagingWorkflowV3Service)
      {
        StagingWorkflowV3Service v3 = (StagingWorkflowV3Service) stagingWorkflow;

        StagingActionDTO action = new StagingActionDTO();
        action.setDescription(description);
        action.setStagedRepositoryIds(Arrays.asList(stagingRepos.getValue()));
        action.setAutoDropAfterRelease(isAutoDropAfterRelease());

        v3.releaseStagingRepositories(action);
      } else
      {
        stagingWorkflowV2Service.releaseStagingRepositories(description, stagingRepos.getValue());
      }
      getLog().info("Released");
    }

  }
}
