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
package org.sonatype.nexus.maven.staging.it.nxcm4527;

import java.util.List;

import junit.framework.Assert;

import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.SimpleRountripMatrixSupport;
import org.sonatype.nexus.mindexer.client.SearchResponse;

import com.sonatype.nexus.staging.client.StagingRepository;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

/**
 * Parent for NXCM-4527 ITs as they share a lot of common things.
 * 
 * @author cstamas
 */
public abstract class NXCM4527Support
    extends SimpleRountripMatrixSupport
{
    /**
     * Drop left-behind staging repositories to interfere with subsequent assertions (we share nexus instance, it is
     * "rebooted" per class).
     */
    @Override
    protected void cleanupNexus( final PreparedVerifier verifier )
    {
        final StagingWorkflowV2Service stagingWorkflow = getStagingWorkflowV2Service();
        List<StagingRepository> stagingRepositories = getAllStagingRepositories();
        for ( StagingRepository stagingRepository : stagingRepositories )
        {
            stagingWorkflow.dropStagingRepositories( "cleanupNexus()", stagingRepository.getId() );
        }
    }

    /**
     * no pre-invocation assertions
     */
    @Override
    protected void preNexusAssertions( final PreparedVerifier verifier )
    {
    }

    /**
     * Validates the defaults: staging repository created during staging is dropped and it's contents is not released.
     */
    protected void assertDefaults( final PreparedVerifier verifier )
    {
        // there are no staging repositories as we dropped them (on rule failure)
        final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
        if ( !stagingRepositories.isEmpty() )
        {
            Assert.fail( "Nexus should not have staging repositories, but it has: " + stagingRepositories );
        }

        // stuff we staged should not be released and not found by indexer
        final SearchResponse searchResponse =
            searchThreeTimesForGAV( verifier.getProjectGroupId(), verifier.getProjectArtifactId(),
                verifier.getProjectVersion(), null, null, "releases" );
        if ( !searchResponse.getHits().isEmpty() )
        {
            Assert.fail( String.format(
                "Nexus should NOT have staged artifact in releases repository with GAV=%s:%s:%s but those are not found on index!",
                verifier.getProjectGroupId(), verifier.getProjectArtifactId(), verifier.getProjectVersion() ) );
        }
    }

    /**
     * Validates the overridden defaults: staging repository is left dangling as open and it's contents is not released.
     */
    protected void assertOverrides( final PreparedVerifier verifier )
    {
        // there are staging repositories as we did not drop them (on rule failure), we override defaults
        final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
        if ( stagingRepositories.isEmpty() )
        {
            Assert.fail( "Nexus should have staging repositories, but it has none!" );
        }
        Assert.assertEquals( "Nexus should have 1 staging repository, the one of the current build", 1,
            stagingRepositories.size() );
        Assert.assertEquals( "Staging repository should be left open!", StagingRepository.State.OPEN,
            stagingRepositories.get( 0 ).getState() );

        // stuff we staged should not be released and not found by indexer
        final SearchResponse searchResponse =
            searchThreeTimesForGAV( verifier.getProjectGroupId(), verifier.getProjectArtifactId(),
                verifier.getProjectVersion(), null, null, "releases" );
        if ( !searchResponse.getHits().isEmpty() )
        {
            Assert.fail( String.format(
                "Nexus should NOT have staged artifact in releases repository with GAV=%s:%s:%s but those are not found on index!",
                verifier.getProjectGroupId(), verifier.getProjectArtifactId(), verifier.getProjectVersion() ) );
        }
    }
}