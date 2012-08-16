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
package org.sonatype.nexus.maven.staging.it.nxcm4548;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.SimpleRoundtripMatrixBaseTests;
import org.sonatype.nexus.maven.staging.it.SimpleRoundtripMatrixSupport;

/**
 * Test for 'standalone staging', mvn nexus-staging:deploy-staged
 */
public class Nxcm4548StandaloneDeployStagedIT
    extends SimpleRoundtripMatrixSupport
{

    private File tmpDir;

    public Nxcm4548StandaloneDeployStagedIT(final String nexusBundleCoordinates)
    {
        super(nexusBundleCoordinates);
    }

    @Before
    public void setupTmpDir()
        throws IOException
    {
        tmpDir = new File(util.getTmpDir(), String.valueOf(hashCode()));
        tmpDir.mkdirs();
    }

    @After
    public void cleanup()
        throws IOException
    {
        FileUtils.deleteDirectory( tmpDir );
    }

    @Override
    protected void preNexusAssertions( final PreparedVerifier verifier )
    {
        assertThat( getAllStagingRepositories().toString(), getAllStagingRepositories(), hasSize( 0 ) );
    }

    @Override
    protected void postNexusAssertions( final PreparedVerifier verifier )
    {
        assertThat( getAllStagingRepositories().toString(), getAllStagingRepositories(), hasSize( 1 ) );
    }

    @Override
    protected void invokeMaven( final PreparedVerifier verifier )
        throws VerificationException
    {
        final File localStagingDir = new File( tmpDir, "12a2439c79f79c6f" );

        verifier.getVerifier().addCliOption( "-DaltStagingDirectory=" + tmpDir.getAbsolutePath() );
        verifier.getVerifier().addCliOption( "-DserverId=local-nexus" );
        verifier.getVerifier().addCliOption( "-DaltDeploymentRepository=dummy::default::" + localStagingDir.toURI() );

        verifier.getVerifier().addCliOption(
            "-DnexusUrl=" + nexus().getUrl().toExternalForm()
                    // verifier replaces "//" with "/", which will make nexus-client unable to parse the URL
                    .replace( "http://", "http:///" )
        );


        verifier.getVerifier().executeGoals( Lists.newArrayList( "clean", "deploy" ) );
        verifier.getVerifier().verifyErrorFreeLog();
        verifier.getVerifier().verifyTextInLog( "12a2439c79f79c6f" );

        verifier.getVerifier().executeGoal( "nexus-staging:deploy-staged" );

        verifier.getVerifier().verifyErrorFreeLog();
    }

    @Test
    public void testDeployStaged()
        throws VerificationException, IOException
    {
        roundtrip( createMavenVerifier( M3_VERSION, new File( getBasedir(), "target/test-classes/plain-project" ) ) );
    }
}
