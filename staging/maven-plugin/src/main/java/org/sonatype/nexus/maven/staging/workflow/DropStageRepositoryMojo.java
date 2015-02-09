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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Drops a Nexus staging repository that is either open or closed.
 *
 * @author cstamas
 * @since 1.0
 */
@Mojo(name = "drop", requiresOnline = true)
public class DropStageRepositoryMojo
    extends AbstractStagingBuildActionMojo
{
  @Override
  public void doExecute(final StagingWorkflowV2Service stagingWorkflow)
      throws MojoExecutionException, MojoFailureException
  {
    Map<String, String[]> stagingRepositories = getStagingRepositoryIds();
    for(Map.Entry<String, String[]> stagingRepos : stagingRepositories.entrySet())
    {
        getLog().info("Dropping staging repository with IDs=" + Arrays.toString(stagingRepos.getValue())
                + " nexusUrl=" + stagingRepos.getKey());
        StagingWorkflowV2Service stagingWorkflowV2Service = createStagingWorkflowService(stagingRepos.getKey());
        stagingWorkflowV2Service.dropStagingRepositories(getDescriptionWithDefaultsForAction(StagingAction.DROP),
                stagingRepos.getValue());
        getLog().info("Dropped");
    }
  }
}
