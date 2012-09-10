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