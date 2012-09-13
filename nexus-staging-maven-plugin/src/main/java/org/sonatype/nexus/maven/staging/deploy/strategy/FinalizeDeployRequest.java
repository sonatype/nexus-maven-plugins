package org.sonatype.nexus.maven.staging.deploy.strategy;

import org.apache.maven.execution.MavenSession;

public class FinalizeDeployRequest
    extends AbstractDeployRequest
{
    public FinalizeDeployRequest( final MavenSession mavenSession, final Parameters parameters )
    {
        super( mavenSession, parameters );
    }
}
