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
package org.sonatype.nexus.maven.staging.it.nxcm4641;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.maven.it.VerificationException;
import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.SimpleRoundtripMatrixBaseTests;

/**
 * Test that verifies how SNAPSHOT builds works with nexus-staging-maven-plugin. As per issue NXCM-4641, they should
 * "pass thru" and simply be deployed as with vanilla maven-deploy-plugin. As for now, this behaviour in plugin
 * "automatically" activates when the module being built is snapshot, altough, same flag is controlled by
 * -DskipLocalStaging=true flag, we do the latter to ensure once we implement proper local staging for snapshots this
 * plugin does not break.
 * 
 * @author cstamas
 */
public class Nxcm4641SnapshotPassthruIT
    extends SimpleRoundtripMatrixBaseTests
{

    public Nxcm4641SnapshotPassthruIT( final String nexusBundleCoordinates )
    {
        super( nexusBundleCoordinates );
    }

    // ==

    protected PreparedVerifier createMavenVerifier( final String mavenVersion, final File projectDirectory )
        throws VerificationException, IOException
    {
        return createMavenVerifier( getClass().getSimpleName(), mavenVersion,
            testData().resolveFile( "preset-nexus-maven-settings.xml" ), projectDirectory, "1.0-SNAPSHOT" );
    }
    
    // ==

    @Override
    protected void preNexusAssertions( final PreparedVerifier verifier )
    {
        assertThat( getAllStagingRepositories().toString(), getAllStagingRepositories(), hasSize( 0 ) );
    }

    @Override
    protected void postNexusAssertions( final PreparedVerifier verifier )
    {
        assertThat( getAllStagingRepositories().toString(), getAllStagingRepositories(), hasSize( 0 ) );
    }

    @Override
    protected void invokeMaven( final PreparedVerifier verifier )
        throws VerificationException
    {
        // the workflow
        verifier.executeGoals( Arrays.asList( "clean", "deploy" ) );
        // should not fail
        verifier.verifyErrorFreeLog();
    }
}