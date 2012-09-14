package org.sonatype.nexus.maven.staging.deploy.strategy;

import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.nexus.maven.staging.deploy.DeployableArtifact;

@Component( role = DeployStrategy.class, hint = Strategies.DIRECT )
public class DirectDeployStrategy
    extends AbstractDeployStrategy
    implements DeployStrategy
{
    @Requirement
    private ArtifactDeployer artifactDeployer;

    @Override
    public void deployPerModule( final DeployPerModuleRequest request )
        throws ArtifactInstallationException, ArtifactDeploymentException, MojoExecutionException
    {
        getLogger().info( "Performing direct deploys (maven-deploy-plugin like)..." );
        final ArtifactRepository deploymentRepository = getDeploymentRepository( request.getMavenSession() );
        final ArtifactRepository localRepository = request.getMavenSession().getLocalRepository();
        for ( DeployableArtifact deployableArtifact : request.getDeployableArtifacts() )
        {
            artifactDeployer.deploy( deployableArtifact.getFile(), deployableArtifact.getArtifact(),
                deploymentRepository, localRepository );
        }
    }

    @Override
    public void finalizeDeploy( final FinalizeDeployRequest request )
        throws ArtifactDeploymentException, MojoExecutionException
    {
        // nothing, all is up already
    }
}
