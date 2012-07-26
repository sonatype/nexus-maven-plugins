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
package org.sonatype.nexus.maven.staging.it.multiprofile;

import static org.sonatype.sisu.filetasks.builder.FileRef.file;
import static org.sonatype.sisu.filetasks.builder.FileRef.path;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import junit.framework.Assert;

import org.apache.maven.it.VerificationException;
import org.junit.Test;
import org.sonatype.nexus.bundle.launcher.NexusBundleConfiguration;
import org.sonatype.nexus.bundle.launcher.NexusStartAndStopStrategy;
import org.sonatype.nexus.bundle.launcher.NexusStartAndStopStrategy.Strategy;
import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.StagingMavenPluginITSupport;
import org.sonatype.sisu.filetasks.FileTaskBuilder;

import com.sonatype.nexus.staging.client.StagingRepository;

/**
 * IT that "implements" the Staging V2 testing guide's "multi profile" scenario followed by the "release" Post Staging
 * Steps section.
 * 
 * @author cstamas
 * @see https://docs.sonatype.com/display/Nexus/Staging+V2+Testing
 */
@NexusStartAndStopStrategy( Strategy.EACH_METHOD )
public class MultiprofileV2RoundtripIT
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

    @Override
    protected List<String> getMavenVersions()
    {
        return Arrays.asList( M3_VERSION );
    }

    /**
     * Using "deploy".
     * 
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM3MultiprofileProjectUsingM3Deploy()
        throws VerificationException, IOException
    {
        final PreparedVerifier verifier =
            createMavenVerifier( getClass().getSimpleName() + "_roundtripWithM3MultiprofileProjectUsingM3Deploy",
                M3_VERSION, resolveTestFile( "preset-nexus-maven-settings.xml" ), new File( getBasedir(),
                    "target/test-classes/maven3-multiprofile-project" ) );

        // v2 workflow
        verifier.getVerifier().executeGoals( Arrays.asList( "clean", "deploy" ) );
        // should not fail
        verifier.getVerifier().verifyErrorFreeLog();

        // perform some checks
        {
            final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
            if ( stagingRepositories.isEmpty() )
            {
                Assert.fail( "Nexus should have 2 staging repositories, but it has none!" );
            }
            Assert.assertEquals( "Nexus should have 2 staging repository, the ones of the current build", 2,
                stagingRepositories.size() );
            Assert.assertEquals( "Staging repository should be closed!", StagingRepository.State.CLOSED,
                stagingRepositories.get( 0 ).getState() );
            Assert.assertEquals( "Staging repository should be closed!", StagingRepository.State.CLOSED,
                stagingRepositories.get( 1 ).getState() );
        }

        // v2 release
        verifier.getVerifier().executeGoals( Arrays.asList( "nexus-staging:release" ) );
        // should not fail
        verifier.getVerifier().verifyErrorFreeLog();

        // post execution Nexus side asserts
        {
            final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
            if ( !stagingRepositories.isEmpty() )
            {
                Assert.fail( "Nexus should not have staging repositories, but it has: " + stagingRepositories );
            }
            // stuff we staged are released and found by indexer
            // TODO: this "assertion" is disabled for now as it shows as highly unreliable
            // final SearchResponse searchResponse =
            // searchThreeTimesForGAV( verifier.getProjectGroupId(), "m1", verifier.getProjectVersion(), "sources",
            // "jar", "releases" );
            // if ( searchResponse.getHits().isEmpty() )
            // {
            // Assert.fail( String.format(
            // "Nexus should have staged artifact in releases repository with GAV=%s:%s:%s but those are not found on index!",
            // verifier.getProjectGroupId(), verifier.getProjectArtifactId(), verifier.getProjectVersion() ) );
            // }
            // Assert.assertEquals( "We deployed 1 module of this GAV but none or more was found!", 1,
            // searchResponse.getHits().size() );
        }
    }

    /**
     * Using "close" build action.
     * 
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM3MultiprofileProjectUsingM3BuildActionClose()
        throws VerificationException, IOException
    {
        final PreparedVerifier verifier =
            createMavenVerifier( getClass().getSimpleName()
                + "_roundtripWithM3MultiprofileProjectUsingM3BuildActionClose", M3_VERSION,
                resolveTestFile( "preset-nexus-maven-settings.xml" ), new File( getBasedir(),
                    "target/test-classes/maven3-multiprofile-project" ) );

        // we want to test the "close" build action here
        verifier.getVerifier().addCliOption( "-DskipStagingRepositoryClose=true" );
        // v2 workflow
        verifier.getVerifier().executeGoals( Arrays.asList( "clean", "deploy" ) );
        // should not fail
        verifier.getVerifier().verifyErrorFreeLog();
        // build action: close, should not fail
        verifier.getVerifier().executeGoals( Arrays.asList( "nexus-staging:close" ) );
        // should not fail
        verifier.getVerifier().verifyErrorFreeLog();

        // perform some checks
        {
            final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
            if ( stagingRepositories.isEmpty() )
            {
                Assert.fail( "Nexus should have 2 staging repositories, but it has none!" );
            }
            Assert.assertEquals( "Nexus should have 2 staging repository, the ones of the current build", 2,
                stagingRepositories.size() );
            Assert.assertEquals( "Staging repository should be closed!", StagingRepository.State.CLOSED,
                stagingRepositories.get( 0 ).getState() );
            Assert.assertEquals( "Staging repository should be closed!", StagingRepository.State.CLOSED,
                stagingRepositories.get( 1 ).getState() );
        }

        // v2 release
        verifier.getVerifier().executeGoals( Arrays.asList( "nexus-staging:release" ) );
        // should not fail
        verifier.getVerifier().verifyErrorFreeLog();

        // post execution Nexus side asserts
        {
            final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
            if ( !stagingRepositories.isEmpty() )
            {
                Assert.fail( "Nexus should not have staging repositories, but it has: " + stagingRepositories );
            }
            // stuff we staged are released and found by indexer
            // TODO: this "assertion" is disabled for now as it shows as highly unreliable
            // final SearchResponse searchResponse =
            // searchThreeTimesForGAV( verifier.getProjectGroupId(), "m1", verifier.getProjectVersion(), "sources",
            // "jar", "releases" );
            // if ( searchResponse.getHits().isEmpty() )
            // {
            // Assert.fail( String.format(
            // "Nexus should have staged artifact in releases repository with GAV=%s:%s:%s but those are not found on index!",
            // verifier.getProjectGroupId(), verifier.getProjectArtifactId(), verifier.getProjectVersion() ) );
            // }
            // Assert.assertEquals( "We deployed 1 module of this GAV but none or more was found!", 1,
            // searchResponse.getHits().size() );
        }
    }
}
