package org.sonatype.nexus.maven.staging.deploy.strategy;

import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.plugin.MojoExecutionException;

public interface DeployStrategy
{
    void deployPerModule( final DeployPerModuleRequest request )
        throws ArtifactInstallationException, ArtifactDeploymentException, MojoExecutionException;

    void finalizeDeploy( final FinalizeDeployRequest request )
        throws ArtifactDeploymentException, MojoExecutionException;
}
