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

import org.apache.maven.it.VerificationException;
import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.mindexer.client.SearchResponse;

import com.sonatype.nexus.staging.client.StagingRepository;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

/**
 * See NXCM-4527, this IT implements it's verification part for Nexus Staging Maven Plugin side when the defaults are
 * overridden. Similar to {@link NXCM4527DropOnCloseRuleFailureIT} IT, but here we assert that staging repository is NOT
 * dropped, it should still exists.
 * 
 * @author cstamas
 */
public class NXCM4527DropOnCloseRuleFailureOverrideIT
    extends NXCM4527DropOnCloseRuleFailureIT
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
     * Validates nexus side of affairs post maven invocations.
     */
    @Override
    protected void postNexusAssertions( final PreparedVerifier verifier )
    {
        // there are staging repositories as we did not drop them (on rule failure), we override defaults
        final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
        if ( stagingRepositories.isEmpty() )
        {
            Assert.fail( "Nexus should have staging repositories, but it has none!" );
        }
        Assert.assertEquals( "Nexus should have 1 staging repository, the one of the current build", 1,
            stagingRepositories.size() );

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

    @Override
    protected void invokeMaven( final PreparedVerifier verifier )
        throws VerificationException
    {
        verifier.getVerifier().addCliOption( "-DkeepStagingRepositoryOnCloseRuleFailure=true" );
        super.invokeMaven( verifier );
    }
}