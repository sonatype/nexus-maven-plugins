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
package org.sonatype.nexus.maven.staging.it.simplev2roundtrip;

import java.util.Arrays;
import java.util.List;

import junit.framework.Assert;

import org.apache.maven.it.VerificationException;
import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.SimpleRountripMatrixSupport;
import org.sonatype.nexus.mindexer.client.SearchResponse;

import com.sonatype.nexus.staging.client.StagingRepository;

/**
 * IT that "implements" the Staging V2 testing guide's "One Shot" scenario followed by the "release" Post Staging Steps
 * section. It also "verifies" that a "matrix" of projects (set up in m2 or m3 way) and maven runtimes (m2 and m3) all
 * work as expected.
 * 
 * @author cstamas
 * @see https://docs.sonatype.com/display/Nexus/Staging+V2+Testing
 */
public class SimpleV2RoundtripIT
    extends SimpleRountripMatrixSupport
{
    /**
     * Nothing to validate before hand.
     */
    @Override
    protected void preNexusAssertions( final PreparedVerifier verifier )
    {
    }

    /**
     * Validates nexus side of affairs post maven invocations.
     */
    @Override
    protected void postNexusAssertions( final PreparedVerifier verifier )
    {
        // there are no staging repositories
        final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
        if ( !stagingRepositories.isEmpty() )
        {
            Assert.fail( "Nexus should not have staging repositories, but it has: " + stagingRepositories );
        }

        // stuff we staged are released and found by indexer
        final SearchResponse searchResponse =
            searchThreeTimesForGAV( verifier.getProjectGroupId(), verifier.getProjectArtifactId(),
                verifier.getProjectVersion(), null, null, "releases" );
        if ( searchResponse.getHits().isEmpty() )
        {
            Assert.fail( String.format(
                "Nexus should have staged artifact in releases repository with GAV=%s:%s:%s but those are not found on index!",
                verifier.getProjectGroupId(), verifier.getProjectArtifactId(), verifier.getProjectVersion() ) );
        }
    }

    /**
     * Simulates separate invocation of commands. Deploy then release.
     * 
     * @param verifier
     * @throws VerificationException
     */
    @Override
    protected void invokeMaven( final PreparedVerifier verifier )
        throws VerificationException
    {
        // v2 workflow
        verifier.getVerifier().executeGoals( Arrays.asList( "clean", "deploy" ) );
        // should not fail
        verifier.getVerifier().verifyErrorFreeLog();
        // v2 release
        verifier.getVerifier().executeGoals( Arrays.asList( "nexus-staging:release" ) );
        // should not fail
        verifier.getVerifier().verifyErrorFreeLog();
    }
}
