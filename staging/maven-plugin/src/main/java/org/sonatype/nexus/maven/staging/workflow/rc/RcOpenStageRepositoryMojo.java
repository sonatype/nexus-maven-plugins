/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2015 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */

package org.sonatype.nexus.maven.staging.workflow.rc;

import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

import org.sonatype.nexus.maven.staging.StagingAction;
import org.sonatype.nexus.maven.staging.workflow.AbstractStagingActionMojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Opens a new Nexus staging repository.
 *
 * @author dashirov
 * @since 1.6.7
 */
@Mojo(name = "rc-open", requiresProject = false, requiresDirectInvocation = true, requiresOnline = true)
public class RcOpenStageRepositoryMojo
    extends AbstractStagingActionMojo
{
  @Parameter(property = "stagingProfileId", required = true)
  private String stagingProfileId;

  /* This method creates a new repository who's ID would otherwise not be known.
   *  It is important to allow end-users to output it's name in the format suitable to them.
   *  We provide a sensible default here
  **/
  @Parameter(property = "openedRepositoryMessageFormat", required = false, defaultValue = "RC-Opening staging repository with ID=%s")
  private String openedRepositoryMessageFormat;

  @Override
  public void doExecute(final StagingWorkflowV2Service stagingWorkflow)
      throws MojoExecutionException, MojoFailureException
  {
    getLog().info("RC-Opening staging repository using staging profile ID=" + stagingProfileId);
    final String stagingRepositoryId = stagingWorkflow.startStaging(
        stagingWorkflow.selectProfile(stagingProfileId),
        getStagingActionMessages().getMessageForAction(StagingAction.START),
        null
    );
    getLog().info(String.format(openedRepositoryMessageFormat, stagingRepositoryId));
  }
}