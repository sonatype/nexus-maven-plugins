package org.sonatype.nexus.maven.staging.deploy.strategy;

import org.apache.maven.execution.MavenSession;

import com.google.common.base.Preconditions;

public class AbstractDeployRequest
{
    private final MavenSession mavenSession;

    private final Parameters parameters;

    protected AbstractDeployRequest( final MavenSession mavenSession, final Parameters parameters )
    {
        this.mavenSession = Preconditions.checkNotNull( mavenSession );
        this.parameters = Preconditions.checkNotNull( parameters );
    }

    public MavenSession getMavenSession()
    {
        return mavenSession;
    }

    public Parameters getParameters()
    {
        return parameters;
    }
}
