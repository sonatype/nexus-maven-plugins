package org.sonatype.nexus.maven.staging.deploy.strategy;

import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.sonatype.nexus.maven.staging.deploy.DeployableArtifact;

import com.google.common.base.Preconditions;

public class DeployPerModuleRequest
    extends AbstractDeployRequest
{
    private final List<DeployableArtifact> deployableArtifacts;

    public DeployPerModuleRequest( final MavenSession mavenSession, final Parameters parameters,
                                   final List<DeployableArtifact> deployableArtifacts )
    {
        super( mavenSession, parameters );
        this.deployableArtifacts = Preconditions.checkNotNull( deployableArtifacts );
    }

    public List<DeployableArtifact> getDeployableArtifacts()
    {
        return deployableArtifacts;
    }
}
