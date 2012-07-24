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

import static org.sonatype.sisu.filetasks.builder.FileRef.file;
import static org.sonatype.sisu.filetasks.builder.FileRef.path;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Test;
import org.sonatype.nexus.bundle.launcher.NexusBundleConfiguration;
import org.sonatype.nexus.maven.staging.it.StagingMavenPluginITSupport;
import org.sonatype.nexus.mindexer.client.SearchResponse;
import org.sonatype.sisu.filetasks.FileTaskBuilder;

import com.google.common.base.Throwables;
import com.sonatype.nexus.staging.client.Profile;
import com.sonatype.nexus.staging.client.StagingRepository;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

/**
 * IT that "implements" the Staging V2 testing guide's "One Shot" scenario followed by the "release" Post Staging Steps
 * section. It also "verifies" that a "matrix" of projects (set up in m2 or m3 way) and maven runtimes (m2 and m3) all
 * work as expected.
 * 
 * @author cstamas
 * @see https://docs.sonatype.com/display/Nexus/Staging+V2+Testing
 */
public class SimpleV2RoundtripIT
    extends StagingMavenPluginITSupport
{
    @Inject
    private FileTaskBuilder fileTaskBuilder;

    @Override
    protected NexusBundleConfiguration configureNexus( final NexusBundleConfiguration configuration )
    {
        // TODO: (cstamas) I promised to Alin to change this "old way of doing things" to use of REST API that would
        // configure Nexus properly once the Security and Staging Management Nexus Client subsystems are done.
        return super.configureNexus( configuration ).addOverlays(
            fileTaskBuilder.copy().directory( file( resolveTestFile( "preset-nexus" ) ) ).to().directory(
                path( "sonatype-work/nexus/conf" ) ) );
    }

    /**
     * Validates nexus side of affairs before maven invocations.
     */
    protected void preNexusChecklist()
    {
        // nexus lives
        // create profile
        // adapt permissions
        // TODO: see #configureNexus above
        // once "staging management" and "security management" clients are done, we should stop "spoofing" config and
        // do the preparation from here
    }

    /**
     * Validates nexus side of affairs post maven invocations.
     */
    protected void postNexusChecklist()
    {
        // there are no staging repositories
        final StagingWorkflowV2Service stagingWorkflow = getStagingWorkflowV2Service();
        for ( Profile profile : stagingWorkflow.listProfiles() )
        {
            List<StagingRepository> stagingRepositories = stagingWorkflow.listStagingRepositories( profile.getId() );
            if ( !stagingRepositories.isEmpty() )
            {
                throw new IllegalStateException( "Nexus should not have staging repositories, but it has: "
                    + stagingRepositories );
            }
        }
        // stuff we staged are released
        for ( int i = 0; i < 3; i++ )
        {
            final SearchResponse response =
                getMavenIndexer().searchByGAV( "org.nexusit", null, "1.0", null, null, "releases" );
            if ( response.getHits().isEmpty() )
            {
                // to warm up indexes, as initial hit is not reliable, so we try the search 3 times before with yell
                // "foul"
                if ( i == 2 )
                {
                    throw new IllegalStateException(
                        "Nexus should have staged artifact in releases repository but it has not!" );
                }
                // sleep some before next retry
                try
                {
                    Thread.sleep( 200 );
                }
                catch ( InterruptedException e )
                {
                    Throwables.propagate( e );
                }
            }
        }
    }

    /**
     * Simulates separate invocation of commands. Deploy then release.
     * 
     * @param verifier
     * @throws VerificationException
     */
    protected void roundtrip( final Verifier verifier )
        throws VerificationException
    {
        // prepare nexus
        preNexusChecklist();
        // v2 workflow
        verifier.executeGoals( Arrays.asList( "clean", "deploy" ) );
        // should not fail
        verifier.verifyErrorFreeLog();
        // v2 release
        verifier.executeGoals( Arrays.asList( "nexus-staging:release" ) );
        // should not fail
        verifier.verifyErrorFreeLog();
        // verify nexus side
        postNexusChecklist();
    }
    
    // ==

    /**
     * Project set up in m2-way with m2.
     * 
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM2ProjectUsingM2()
        throws VerificationException, IOException
    {
        final Verifier verifier =
            createMavenVerifier( "SimpleV2RoundtripIT", M2_VERSION,
                resolveTestFile( "preset-nexus-maven-settings.xml" ), new File( getBasedir(),
                    "target/test-classes/maven2-project" ) );
        roundtrip( verifier );
    }

    /**
     * Project set up in m2-way with m3.
     * 
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM2ProjectUsingM3()
        throws VerificationException, IOException
    {
        final Verifier verifier =
            createMavenVerifier( "SimpleV2RoundtripIT", M3_VERSION,
                resolveTestFile( "preset-nexus-maven-settings.xml" ), new File( getBasedir(),
                    "target/test-classes/maven2-project" ) );
        roundtrip( verifier );
    }

    /**
     * Project set up in m3-way using m3.
     * 
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM3()
        throws VerificationException, IOException
    {
        final Verifier verifier =
            createMavenVerifier( "SimpleV2RoundtripIT", M3_VERSION,
                resolveTestFile( "preset-nexus-maven-settings.xml" ), new File( getBasedir(),
                    "target/test-classes/maven3-project" ) );
        roundtrip( verifier );
    }
}
