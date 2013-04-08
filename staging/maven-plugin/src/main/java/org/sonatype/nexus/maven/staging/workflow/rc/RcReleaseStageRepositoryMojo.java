/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
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

import java.util.Arrays;

import com.sonatype.nexus.staging.api.dto.StagingActionDTO;
import com.sonatype.nexus.staging.client.StagingWorkflowV3Service;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.sonatype.nexus.maven.staging.StagingAction;

import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

/**
 * Releases a single closed Nexus staging repository into a permanent Nexus repository for general consumption.
 *
 * @author cstamas
 * @since 1.0
 */
@Mojo( name = "rc-release", requiresProject = false, requiresDirectInvocation = true, requiresOnline = true )
public class RcReleaseStageRepositoryMojo
    extends AbstractStagingRcActionMojo
{
    /**
     * Automatically drop repository after it has been successfully released.
     *
     * @since 1.4.3
     */
    @Parameter( property = "autoDropAfterRelease", defaultValue = "true")
    private boolean autoDropAfterRelease;

    @Override
    public void doExecute( final StagingWorkflowV2Service stagingWorkflow )
        throws MojoExecutionException, MojoFailureException
    {
        // FIXME: Some duplication here between RcReleaseStageRepositoryMojo and ReleaseStageRepositoryMojo

        getLog().info( "RC-Releasing staging repository with IDs=" + Arrays.toString( getStagingRepositoryIds() ) );

        String description = getDescriptionWithDefaultsForAction( StagingAction.RELEASE );

        if (stagingWorkflow instanceof StagingWorkflowV3Service) {
            StagingWorkflowV3Service v3 = (StagingWorkflowV3Service)stagingWorkflow;

            StagingActionDTO action = new StagingActionDTO();
            action.setDescription(description);
            action.setStagedRepositoryIds(Arrays.asList(getStagingRepositoryIds()));
            action.setAutoDropAfterRelease(autoDropAfterRelease);

            v3.releaseStagingRepositories(action);
        }
        else {
            stagingWorkflow.releaseStagingRepositories( description, getStagingRepositoryIds() );
        }

        getLog().info( "Released" );
    }
}
