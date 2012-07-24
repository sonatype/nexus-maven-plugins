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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.sonatype.nexus.maven.staging.ErrorDumper;

import com.sonatype.nexus.staging.client.StagingRuleFailures;
import com.sonatype.nexus.staging.client.StagingRuleFailuresException;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

/**
 * Closes a Nexus staging repository.
 * 
 * @author cstamas
 * @since 2.1
 * @goal close
 */
public class CloseStageRepositoryMojo
    extends AbstractStagingBuildActionMojo
{
    @Override
    public void doExecute( final StagingWorkflowV2Service stagingWorkflow )
        throws MojoExecutionException, MojoFailureException
    {
        final String[] stagingRepositoryIds = getStagingRepositoryIds();
        try
        {
            getLog().info( "Closing staging repository with IDs=" + Arrays.toString( getStagingRepositoryIds() ) );
            stagingWorkflow.finishStagingRepositories( getDescriptionWithDefaultsForAction( "Closed" ),
                stagingRepositoryIds );
        }
        catch ( StagingRuleFailuresException e )
        {
            // report staging repository failures
            ErrorDumper.dumpErrors( getLog(), e );

            // drop the repository (this will break exception chain if there's new failure, like network)
            if ( !isKeepStagingRepositoryOnCloseRuleFailure() )
            {
                final List<String> failedRepositories = new ArrayList<String>();
                for ( StagingRuleFailures failures : e.getFailures() )
                {
                    failedRepositories.add( failures.getRepositoryName() + "(id=" + failures.getRepositoryId() + ")" );
                }
                final String msg = "Rule failure during close of staging repositories: " + failedRepositories;

                getLog().error( "Cleaning up remote stage repositories after a " + msg );
                stagingWorkflow.dropStagingRepositories( getDefaultDescriptionForAction( "Dropped" ) + " ("
                                + msg + ").", stagingRepositoryIds );
            }
            // fail the build
            throw new MojoExecutionException( "Could not perform action: there are failing staging rules!", e );
        }
    }
}
