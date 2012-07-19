package org.sonatype.nexus.maven.staging.it.simplev2roundtrip;

import static org.sonatype.sisu.filetasks.builder.FileRef.file;
import static org.sonatype.sisu.filetasks.builder.FileRef.path;

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

import com.sonatype.nexus.staging.client.Profile;
import com.sonatype.nexus.staging.client.StagingRepository;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

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

    protected void preNexusChecklist()
    {
        // nexus lives
        // create profile
        // adapt permissions
        // TODO: see #configureNexus above
    }

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
        final SearchResponse response =
            getMavenIndexer().searchByGAV( "org.nexusit", "artifact", "1.0", null, null, "releases" );
        if ( response.getHits().isEmpty() )
        {
            throw new IllegalStateException( "Nexus should have staged artifact in releases repository but it has not!" );
        }

    }

    protected void roundtrip( final Verifier verifier )
        throws VerificationException
    {
        // prepare nexus
        preNexusChecklist();
        // v2 workflow
        verifier.executeGoals( Arrays.asList( "clean", "deploy" ) );
        // should not fail
        verifier.verifyErrorFreeLog();
        // and do the release
        verifier.executeGoals( Arrays.asList( "nexus-staging:release" ) );
        // should not fail
        verifier.verifyErrorFreeLog();
        // verify nexus side
        postNexusChecklist();
    }

    @Test
    public void roundtripWithM2()
        throws VerificationException, IOException
    {
        // maven2 needs a project that has explicitly set and bound nexus-staging-maven-plugin goals
        final Verifier m2Verifier =
            createMavenVerifier( "SimpleV2RoundtripIT", "2.2.1", resolveTestFile( "preset-nexus-maven-settings.xml" ),
                resolveTestFile( "maven2-project" ) );
        roundtrip( m2Verifier );
    }

    @Test
    public void roundtripWithM3()
        throws VerificationException, IOException
    {
        // maven3 needs a project has lifecycle-participant + extension set
        final Verifier m3Verifier =
            createMavenVerifier( "SimpleV2RoundtripIT", "3.0.4", resolveTestFile( "preset-nexus-maven-settings.xml" ),
                resolveTestFile( "maven3-project" ) );
        roundtrip( m3Verifier );
    }
}
