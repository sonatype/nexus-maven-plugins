package org.sonatype.nexus.maven.staging.deploy.strategy;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.logging.AbstractLogEnabled;

public abstract class AbstractDeployStrategy
    extends AbstractLogEnabled
{
    /**
     * Returns the ArtifactRepository created from passed in maven session's current project's distribution management.
     * 
     * @param mavenSession
     * @return
     * @throws MojoExecutionException
     */
    protected ArtifactRepository getDeploymentRepository( final MavenSession mavenSession )
        throws MojoExecutionException
    {
        final ArtifactRepository repo = mavenSession.getCurrentProject().getDistributionManagementArtifactRepository();
        if ( repo == null )
        {
            String msg =
                "Deployment failed: repository element was not specified in the POM inside"
                    + " distributionManagement element or in -DaltDeploymentRepository=id::layout::url parameter";

            throw new MojoExecutionException( msg );
        }
        return repo;
    }
}
