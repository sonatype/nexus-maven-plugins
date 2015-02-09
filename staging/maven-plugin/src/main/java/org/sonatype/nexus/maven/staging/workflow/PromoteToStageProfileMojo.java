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

import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

import org.sonatype.nexus.maven.staging.StagingAction;

import com.google.common.base.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Promotes a closed Nexus staging repository into a Nexus Build Promotion Profile.
 *
 * @author cstamas
 * @since 1.0
 */
@Mojo(name = "promote", requiresOnline = true)
public class PromoteToStageProfileMojo
    extends AbstractStagingBuildActionMojo
{

  /**
   * Specifies the staging build promotion profile ID on remote Nexus where to promotion happens. If not specified,
   * goal will fail.
   */
  @Parameter(property = "buildPromotionProfileId", required = true)
  private String buildPromotionProfileId;

  protected String getBuildPromotionProfileId()
      throws MojoExecutionException
  {
    if (buildPromotionProfileId == null) {
      throw new MojoExecutionException(
          "The staging staging build promotion profile ID to promote to is not defined! (use \"-DbuildPromotionProfileId=foo\" on CLI)");
    }

    return buildPromotionProfileId;
  }

  @Override
  public void doExecute(final StagingWorkflowV2Service stagingWorkflow)
      throws MojoExecutionException, MojoFailureException
  {
    Map<String, String[]> stagingRepositoryIds = getStagingRepositoryIds();
    for(Map.Entry<String, String[]> stagingRepo : stagingRepositoryIds.entrySet())
    {
        getLog().info(
                "Promoting staging repository with IDs=" + Arrays.toString(stagingRepo.getValue())
                        + " to build profile ID=\"" + getBuildPromotionProfileId() + "\""
                        + ", nexusUrl=\"" + stagingRepo.getKey() + "\"");
        StagingWorkflowV2Service stagingWorkflowV2Service = createStagingWorkflowService(stagingRepo.getKey());
        final String promotionGroupId =
                stagingWorkflowV2Service.promoteStagingRepositories(getDescriptionWithDefaultsForAction(StagingAction.PROMOTE),
                        getBuildPromotionProfileId(), stagingRepo.getValue());
        if (Strings.isNullOrEmpty(promotionGroupId))
        {
            getLog().info("Promoted, but created promotion group ID unknown (needs Nexus Pro version 2.4+).");
        } else
        {
            getLog().info("Promoted, created promotion group with ID " + promotionGroupId);
        }
    }
  }
}
