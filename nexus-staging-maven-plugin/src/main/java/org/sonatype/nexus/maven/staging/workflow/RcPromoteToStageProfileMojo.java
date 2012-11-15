/**
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
package org.sonatype.nexus.maven.staging.workflow;

import java.util.Arrays;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

/**
 * Promotes a closed Nexus staging repository into a Nexus Build Promotion Profile.
 *
 * @author cstamas
 * @since 1.0
 */
@Mojo( name = "rc-promote", requiresProject = false, requiresDirectInvocation = true, requiresOnline = true )
public class RcPromoteToStageProfileMojo
    extends AbstractStagingRcActionMojo
{

    /**
     * Specifies the staging build promotion profile ID on remote Nexus where to promotion happens. If not specified,
     * goal will fail.
     */
    @Parameter( property = "buildPromotionProfileId", required = true )
    private String buildPromotionProfileId;

    protected String getBuildPromotionProfileId()
        throws MojoExecutionException
    {
        if ( buildPromotionProfileId == null )
        {
            throw new MojoExecutionException(
                "The staging staging build promotion profile ID to promote to is not defined! (use \"-DbuildPromotionProfileId=foo\" on CLI)" );
        }

        return buildPromotionProfileId;
    }

    @Override
    public void doExecute( final StagingWorkflowV2Service stagingWorkflow )
        throws MojoExecutionException, MojoFailureException
    {
        getLog().info(
            "RC-Promoting staging repository with IDs=" + Arrays.toString( getStagingRepositoryIds() )
                + " to build profile ID=\""
                + getBuildPromotionProfileId() + "\"" );
        stagingWorkflow.promoteStagingRepositories( getDescriptionWithDefaultsForAction( "RC-Promoted" ),
                                                    getBuildPromotionProfileId(), getStagingRepositoryIds() );
    }
}
