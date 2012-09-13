package org.sonatype.nexus.maven.staging.deploy.strategy;

import java.io.File;
import java.io.IOException;

import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.component.annotations.Component;
import org.sonatype.nexus.maven.staging.deploy.DeployableArtifact;

@Component( role = DeployStrategy.class, hint = Strategies.DEFERRED )
public class DeferredDeployStrategy
    extends AbstractStagingDeployStrategy
    implements DeployStrategy
{
    private static final String DEFERRED_UPLOAD = "deferred";

    @Override
    public void deployPerModule( final DeployPerModuleRequest request )
        throws ArtifactInstallationException, ArtifactDeploymentException, MojoExecutionException
    {
        getLog().info(
            "Performing deferred deploys (local stagingDirectory=\""
                + request.getParameters().getStagingDirectoryRoot().getAbsolutePath() + "\")..." );
        if ( !request.getDeployableArtifacts().isEmpty() )
        {
            // deploys always to same stagingDirectory
            final File stagingDirectory =
                getStagingDirectory( request.getParameters().getStagingDirectoryRoot(), DEFERRED_UPLOAD );
            final ArtifactRepository stagingRepository = getArtifactRepositoryForDirectory( stagingDirectory );
            for ( DeployableArtifact deployableArtifact : request.getDeployableArtifacts() )
            {
                install( deployableArtifact.getFile(), deployableArtifact.getArtifact(), stagingRepository,
                    stagingDirectory );
            }
        }
        else
        {
            getLog().info( "Nothing to locally stage?" );
        }
    }

    @Override
    public void finalizeDeploy( final FinalizeDeployRequest request )
        throws ArtifactDeploymentException, MojoExecutionException
    {
        getLog().info( "Deploying remotely..." );
        final File stagingDirectory =
            getStagingDirectory( request.getParameters().getStagingDirectoryRoot(), DEFERRED_UPLOAD );
        if ( !stagingDirectory.isDirectory() )
        {
            getLog().warn(
                "Nothing to deploy, directory \"" + stagingDirectory.getAbsolutePath() + "\" does not exists!" );
            return;
        }

        // we do direct upload
        getLog().info( "Bulk deploying locally gathered artifacts from directory: " );
        try
        {
            // prepare the local staging directory
            // we have normal deploy
            final ArtifactRepository deploymentRepository = getDeploymentRepository( request.getMavenSession() );
            getLog().info(
                " * Bulk deploying locally gathered snapshot artifacts to URL " + deploymentRepository.getUrl() );
            deployUp( request.getMavenSession(), stagingDirectory, deploymentRepository );
            getLog().info( " * Bulk deploy of locally gathered snapshot artifacts finished." );
        }
        catch ( IOException e )
        {
            getLog().error( "Upload of locally staged directory finished with a failure." );
            throw new ArtifactDeploymentException( "Remote staging failed: " + e.getMessage(), e );
        }

        getLog().info( "Remote deploy finished with success." );
    }
}
