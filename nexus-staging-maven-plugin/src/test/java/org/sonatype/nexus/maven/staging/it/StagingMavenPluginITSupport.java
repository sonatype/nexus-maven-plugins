package org.sonatype.nexus.maven.staging.it;

import static org.sonatype.nexus.client.rest.BaseUrl.baseUrlFrom;
import static org.sonatype.sisu.filetasks.builder.FileRef.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.bundle.launcher.NexusBundleConfiguration;
import org.sonatype.nexus.bundle.launcher.NexusRunningITSupport;
import org.sonatype.nexus.bundle.launcher.NexusStartAndStopStrategy;
import org.sonatype.nexus.bundle.launcher.NexusStartAndStopStrategy.Strategy;
import org.sonatype.nexus.bundle.launcher.support.NexusBundleResolver;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.rest.NexusClientFactory;
import org.sonatype.nexus.client.rest.UsernamePasswordAuthenticationInfo;
import org.sonatype.nexus.mindexer.client.MavenIndexer;
import org.sonatype.sisu.bl.support.resolver.BundleResolver;
import org.sonatype.sisu.filetasks.FileTaskBuilder;
import org.sonatype.sisu.goodies.common.Time;

import com.google.inject.Binder;
import com.google.inject.name.Names;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

@NexusStartAndStopStrategy( Strategy.EACH_TEST )
public abstract class StagingMavenPluginITSupport
    extends NexusRunningITSupport
{
    private final Logger logger = LoggerFactory.getLogger( StagingMavenPluginITSupport.class );

    @Rule
    public final Timeout defaultTimeout = new Timeout( Time.minutes( 5 ).toMillisI() );

    @Inject
    private NexusClientFactory nexusClientFactory;

    @Inject
    private FileTaskBuilder fileTaskBuilder;

    private NexusClient nexusDeploymentClient;

    @Override
    protected NexusBundleConfiguration configureNexus( final NexusBundleConfiguration configuration )
    {
        return configuration.setSystemProperty( "nexus.createTrialLicense", Boolean.TRUE.toString() );
    }

    @Override
    public void configure( final Binder binder )
    {
        super.configure( binder );
        binder.bind( BundleResolver.class ).annotatedWith(
            Names.named( NexusBundleResolver.FALLBACK_NEXUS_BUNDLE_RESOLVER ) ).toInstance( new BundleResolver()
        {
            @Override
            public File resolve()
            {
                return resolveFromDependencyManagement( "com.sonatype.nexus", "nexus-professional", null, null, null,
                    null );
            }
        } );
    }

    private final String MAVEN_G = "org.apache.maven";

    private final String MAVEN_A = "apache-maven";

    private final Map<String, File> mavenHomes = new LinkedHashMap<String, File>();

    /**
     * Override this method to have other than default versions involved.
     * 
     * @return
     */
    protected List<String> getMavenVersions()
    {
        return Arrays.asList( "2.2.1", "3.0.4" );
    }

    // {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>} formatted string.
    @Before
    public void resolveMavens()
        throws IOException
    {
        logger.info( "Setting up Mavens..." );
        final File mavenHomesBase = new File( getBasedir(), "target/maven" );
        for ( String mavenVersion : getMavenVersions() )
        {
            final File mavenHome = new File( mavenHomesBase, MAVEN_A + "-" + mavenVersion );
            logger.info( "  Setting up Maven " + mavenVersion + "..." );
            final File maven2bundle = resolveArtifact( MAVEN_G + ":" + MAVEN_A + ":zip:bin:" + mavenVersion );
            fileTaskBuilder.expand( file( maven2bundle ) ).to().directory( file( mavenHomesBase ) ).run();
            fileTaskBuilder.chmod( file( new File( mavenHome, "bin" ) ) ).include( "mvn" ).permissions( "755" ).run();
            mavenHomes.put( mavenVersion, mavenHome );
        }
    }

    @Before
    public void createClient()
    {
        nexusDeploymentClient =
            nexusClientFactory.createFor( baseUrlFrom( nexus().getUrl() ), new UsernamePasswordAuthenticationInfo(
                "deployment", "deployment123" ) );
    }

    public MavenIndexer getMavenIndexer()
    {
        return nexusDeploymentClient.getSubsystem( MavenIndexer.class );
    }

    public StagingWorkflowV2Service getStagingWorkflowV2Service()
    {
        return nexusDeploymentClient.getSubsystem( StagingWorkflowV2Service.class );
    }

    public Verifier createMavenVerifier( final String testId, final String mavenVersion, final File mavenSettings,
                                         final File baseDir )
        throws VerificationException, IOException
    {
        final File mavenHome = mavenHomes.get( mavenVersion );
        if ( mavenHome == null || !mavenHome.isDirectory() )
        {
            throw new IllegalArgumentException( "Maven version " + mavenVersion + " was not prepared!" );
        }

        final String logname = "logs/maven-execution/" + testId + "/" + baseDir.getName() + "-maven2.log";
        final String localRepoName =
            "target/maven-execution/" + testId + "/" + baseDir.getName() + "-local-repository/";
        final File logFile = new File( getBasedir(), logname );
        final File localRepoFile = new File( getBasedir(), localRepoName );
        logFile.getParentFile().mkdirs();
        final File filteredSettings = new File( getBasedir(), "target/settings.xml" );
        fileTaskBuilder.copy().file( file( mavenSettings ) ).filterUsing( "nexus.port",
            String.valueOf( nexus().getPort() ) ).to().file( file( filteredSettings ) ).run();

        System.setProperty( "maven.home", mavenHome.getAbsolutePath() );
        Verifier verifier = new Verifier( baseDir.getAbsolutePath(), false );
        verifier.setLogFileName( "log.txt" );
        verifier.setLocalRepo( localRepoFile.getAbsolutePath() );
        verifier.resetStreams();
        List<String> options = new ArrayList<String>();
        // options.add( "-X" );
        options.add( "-Dmaven.repo.local=" + localRepoFile.getAbsolutePath() );
        options.add( "-s " + filteredSettings.getAbsolutePath() );
        verifier.setCliOptions( options );
        return verifier;
    }
}
