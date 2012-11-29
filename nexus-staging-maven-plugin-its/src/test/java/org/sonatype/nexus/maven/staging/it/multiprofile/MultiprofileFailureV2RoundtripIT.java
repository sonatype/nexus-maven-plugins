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
package org.sonatype.nexus.maven.staging.it.multiprofile;

import static org.sonatype.nexus.testsuite.support.NexusStartAndStopStrategy.Strategy.EACH_METHOD;
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
import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.StagingMavenPluginITSupport;
import org.sonatype.nexus.testsuite.support.NexusStartAndStopStrategy;
import org.sonatype.sisu.filetasks.FileTaskBuilder;

import com.sonatype.nexus.staging.client.StagingRepository;

/**
 * IT that Verifies multi profile build, where a subsequent staged repository close operation fails. In these cases -
 * set of staging repositories coming from ONE REACTOR - if build fails due to rule failure, all created staging
 * repositories should be dropped by same guidelines we drop the one repository that failed:
 * "user will start another build, and new ones will be created", but the ones not dropped will remain dangling. Failure
 * is achieved by activating a profile, that makes 2nd module produce an invalid build (lacking javadoc).
 * 
 * @author cstamas
 * @see https://docs.sonatype.com/display/Nexus/Staging+V2+Testing
 */
@NexusStartAndStopStrategy( EACH_METHOD )
public class MultiprofileFailureV2RoundtripIT
    extends StagingMavenPluginITSupport
{
    @Inject
    private FileTaskBuilder fileTaskBuilder;

    public MultiprofileFailureV2RoundtripIT( final String nexusBundleCoordinates )
    {
        super( nexusBundleCoordinates );
    }

    @Override
    protected NexusBundleConfiguration configureNexus( final NexusBundleConfiguration configuration )
    {
        // TODO: (cstamas) I promised to Alin to change this "old way of doing things" to use of REST API that would
        // configure Nexus properly once the Security and Staging Management Nexus Client subsystems are done.
        return super.configureNexus( configuration ).addOverlays(
            fileTaskBuilder.copy().directory( file( testData().resolveFile( "preset-nexus" ) ) ).to().directory(
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
                M3_VERSION, testData().resolveFile( "preset-nexus-maven-settings.xml" ), new File( getBasedir(),
                    "target/test-classes/maven3-multiprofile-project" ) );

        try
        {
            // skip javadoc, we want failing build but in m2
            verifier.addCliOption( "-Pmake-it-fail" );
            // v2 workflow
            verifier.executeGoals( Arrays.asList( "clean", "deploy" ) );
            // should fail
            verifier.verifyErrorFreeLog();
            // foolproof the failure
            Assert.fail( "We should not get here, close at the end of deploy should fail!" );
        }
        catch ( VerificationException e )
        {
            // good
        }

        // perform some checks
        {
            final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
            if ( !stagingRepositories.isEmpty() )
            {
                Assert.fail( "Nexus should have 0 staging repositories, but it has: " + stagingRepositories );
            }
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
                testData().resolveFile( "preset-nexus-maven-settings.xml" ), new File( getBasedir(),
                    "target/test-classes/maven3-multiprofile-project" ) );

        try
        {
            // skip javadoc, we want failing build but in m2
            verifier.addCliOption( "-Pmake-it-fail" );
            // we want to test the "close" build action here
            verifier.addCliOption( "-DskipStagingRepositoryClose=true" );
            // v2 workflow
            verifier.executeGoals( Arrays.asList( "clean", "deploy" ) );
            // should not fail
            verifier.verifyErrorFreeLog();
            // build action: close, will fail
            verifier.executeGoals( Arrays.asList( "nexus-staging:close" ) );
            // should fail
            verifier.verifyErrorFreeLog();
            // foolproof the failure
            Assert.fail( "We should not get here, close at the end of deploy should fail!" );
        }
        catch ( VerificationException e )
        {
            // good
        }

        // perform some checks
        {
            final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
            if ( !stagingRepositories.isEmpty() )
            {
                Assert.fail( "Nexus should have 0 staging repositories, but it has: " + stagingRepositories );
            }
        }
    }
}
