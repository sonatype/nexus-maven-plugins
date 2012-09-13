package org.sonatype.nexus.maven.staging.deploy.strategy;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.sonatype.nexus.client.core.NexusClient;

import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

public interface Remoting
{
    Server getServer();

    Proxy getProxy();

    NexusClient getNexusClient()
        throws MojoExecutionException;

    StagingWorkflowV2Service getStagingWorkflowV2Service()
        throws MojoExecutionException;
}
