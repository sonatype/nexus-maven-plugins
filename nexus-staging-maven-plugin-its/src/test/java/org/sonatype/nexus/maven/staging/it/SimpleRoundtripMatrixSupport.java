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
package org.sonatype.nexus.maven.staging.it;

import static org.sonatype.sisu.filetasks.builder.FileRef.file;
import static org.sonatype.sisu.filetasks.builder.FileRef.path;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.maven.it.VerificationException;
import org.sonatype.nexus.bundle.launcher.NexusBundleConfiguration;
import org.sonatype.sisu.filetasks.FileTaskBuilder;

/**
 * IT support clas that "implements" the "matrix". No reusable code (like some helper) should be added here, but into
 * this parent class, as the matrix here is rather naive, and probably will be reimplemented.
 *
 * @author cstamas
 * @see <a href="https://docs.sonatype.com/display/Nexus/Staging+V2+Testing">Staging V2 Testing</a>
 */
public abstract class SimpleRoundtripMatrixSupport
    extends StagingMavenPluginITSupport
{
    @Inject
    private FileTaskBuilder fileTaskBuilder;

    public SimpleRoundtripMatrixSupport(final String nexusBundleCoordinates)
    {
        super(nexusBundleCoordinates);
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

    /**
     * Configures Nexus side if needed.
     */
    protected void prepareNexus( final PreparedVerifier verifier )
    {
        // nexus lives
        // create profile
        // adapt permissions
        // TODO: see #configureNexus above
        // once "staging management" and "security management" clients are done, we should stop "spoofing" config and
        // do the preparation from here
    }

    /**
     * Cleans up Nexus side if needed.
     */
    protected void cleanupNexus( final PreparedVerifier verifier )
    {
        // TODO: see #configureNexus above
        // once "staging management" and "security management" clients are done, we should stop "spoofing" config and
        // do the preparation from here
    }

    /**
     * Validates nexus side of affairs before maven invocations.
     */
    protected abstract void preNexusAssertions( final PreparedVerifier verifier );

    /**
     * Validates nexus side of affairs post maven invocations.
     */
    protected abstract void postNexusAssertions( final PreparedVerifier verifier );

    /**
     * Simulates separate invocation of commands. Deploy then release.
     *
     * @param verifier
     * @throws VerificationException
     */
    protected void roundtrip( final PreparedVerifier verifier )
        throws VerificationException
    {
        // prepare nexus
        prepareNexus( verifier );
        // check pre-state
        preNexusAssertions( verifier );
        // invoke maven
        invokeMaven( verifier );
        // check post-state
        postNexusAssertions( verifier );
        // cleanup nexus
        cleanupNexus( verifier );
    }

    protected abstract void invokeMaven( final PreparedVerifier verifier )
        throws VerificationException;

    protected PreparedVerifier createMavenVerifier( final String mavenVersion, final File projectDirectory )
        throws VerificationException, IOException
    {
        return createMavenVerifier( getClass().getSimpleName(), mavenVersion,
                                    testData().resolveFile( "preset-nexus-maven-settings.xml" ), projectDirectory );
    }
}
