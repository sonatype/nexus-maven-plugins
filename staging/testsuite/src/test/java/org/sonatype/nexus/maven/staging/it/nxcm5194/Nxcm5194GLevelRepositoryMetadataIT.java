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
package org.sonatype.nexus.maven.staging.it.nxcm5194;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.maven.it.VerificationException;
import org.junit.Test;
import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.SimpleRoundtripMatrixSupport;

import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.hasSize;

/**
 * IT for https://issues.sonatype.org/browse/NXCM-5194
 * <p>
 * It verifies that G level repository metadata is properly deployed and contains the proper bits about the maven plugin
 * being built.
 * 
 * @author cstamas
 */
public class Nxcm5194GLevelRepositoryMetadataIT
    extends SimpleRoundtripMatrixSupport
{

    public Nxcm5194GLevelRepositoryMetadataIT( final String nexusBundleCoordinates )
    {
        super( nexusBundleCoordinates );
    }

    /**
     * Maven Plugin Project set up in m2-way with m2.
     * 
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM2ProjectUsingM2()
        throws VerificationException, IOException
    {
        roundtrip( createMavenVerifier( M2_VERSION, new File( getBasedir(),
            "target/test-classes/maven2-maven-plugin-project" ) ) );
    }

    /**
     * Maven Plugin Project set up in m2-way with m3.
     * 
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM2ProjectUsingM3()
        throws VerificationException, IOException
    {
        roundtrip( createMavenVerifier( M3_VERSION, new File( getBasedir(),
            "target/test-classes/maven2-maven-plugin-project" ) ) );
    }

    /**
     * Maven Plugin Project set up in m3-way using m3.
     * 
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM3ProjectUsingM3()
        throws VerificationException, IOException
    {
        roundtrip( createMavenVerifier( M3_VERSION, new File( getBasedir(),
            "target/test-classes/maven3-maven-plugin-project" ) ) );
    }

    // ==
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