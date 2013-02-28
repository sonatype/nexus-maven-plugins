/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.maven.staging.workflow;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import com.sonatype.nexus.staging.client.StagingWorkflowV3Service;
import com.sonatype.nexus.staging.client.rest.JerseyStagingWorkflowV3SubsystemFactory;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.maven.mojo.settings.MavenSettings;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.core.exception.NexusClientErrorResponseException;
import org.sonatype.nexus.client.rest.BaseUrl;
import org.sonatype.nexus.client.rest.ConnectionInfo;
import org.sonatype.nexus.client.rest.Protocol;
import org.sonatype.nexus.client.rest.ProxyInfo;
import org.sonatype.nexus.client.rest.UsernamePasswordAuthenticationInfo;
import org.sonatype.nexus.client.rest.jersey.JerseyNexusClientFactory;
import org.sonatype.nexus.maven.staging.AbstractStagingMojo;
import org.sonatype.nexus.maven.staging.ErrorDumper;
import org.sonatype.nexus.maven.staging.ProgressMonitorImpl;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import com.sonatype.nexus.staging.client.StagingRuleFailuresException;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;
import com.sonatype.nexus.staging.client.rest.JerseyStagingWorkflowV2SubsystemFactory;
import com.sun.jersey.api.client.UniformInterfaceException;

/**
 * Super class of non-RC Actions. These mojos are "plain" non-aggregator ones, and will use the property file from
 * staged repository to get the repository ID they need. This way, you can integrate these mojos in your build directly
 * (ie. to release or promote even).
 * 
 * @author cstamas
 */
public abstract class AbstractStagingActionMojo
    extends AbstractStagingMojo
{
    /**
     * The NexusClient instance.
     */
    private NexusClient nexusClient;

    @Override
    public final void execute()
        throws MojoExecutionException, MojoFailureException
    {
        // all these RC or build actions cannot work in offline mode, as we perform remote REST calls
        failIfOffline();

        if ( shouldExecute() )
        {
            getLog().info( "Connecting to Nexus..." );
            createTransport( getNexusUrl() );
            final StagingWorkflowV2Service stagingWorkflow = getStagingWorkflowService();

            try
            {
                doExecute( stagingWorkflow );
            }
            catch ( NexusClientErrorResponseException e )
            {
                ErrorDumper.dumpErrors( getLog(), e );
                // fail the build
                throw new MojoExecutionException( "Could not perform action: Nexus ErrorResponse received!", e );
            }
            catch ( StagingRuleFailuresException e )
            {
                ErrorDumper.dumpErrors( getLog(), e );
                // fail the build
                throw new MojoExecutionException( "Could not perform action: there are failing staging rules!", e );
            }
        }
    }

    protected String getDefaultDescriptionForAction( final String action )
    {
        return action + " by " + getPluginGav();
    }

    protected String getDescriptionWithDefaultsForAction( final String action )
    {
        String result = getDescription();
        if ( StringUtils.isBlank( result ) )
        {
            result = getDefaultDescriptionForAction( action );
        }
        return result;
    }

    protected boolean shouldExecute()
    {
        return true;
    }

    protected abstract void doExecute( final StagingWorkflowV2Service stagingWorkflow )
        throws MojoExecutionException, MojoFailureException;

    // == TRANSPORT

    /**
     * Initialized stuff needed for transport, stuff like: Server, Proxy and NexusClient.
     * 
     * @throws ArtifactDeploymentException
     */
    protected void createTransport( final String deployUrl )
        throws MojoExecutionException
    {
        if ( deployUrl == null )
        {
            throw new MojoExecutionException(
                "The URL against which transport should be established is not defined! (use \"-DnexusUrl=http://host/nexus\" on CLI or configure it in POM)" );
        }

        final Server server;
        final Proxy proxy;

        try
        {
            if ( getServerId() != null )
            {
                final Server selectedServer =
                    MavenSettings.selectServer( getMavenSession().getSettings(), getServerId() );
                if ( selectedServer != null )
                {
                    server = MavenSettings.decrypt( getSecDispatcher(), selectedServer );
                }
                else
                {
                    throw new MojoExecutionException( "Server credentials with ID \"" + getServerId() + "\" not found!" );
                }
            }
            else
            {
                throw new MojoExecutionException(
                    "Server credentials to use in transport are not defined! (use \"-DserverId=someServerId\" on CLI or configure it in POM)" );
            }

            final Proxy selectedProxy = MavenSettings.selectProxy( getMavenSession().getSettings(), deployUrl );
            if ( selectedProxy != null )
            {
                proxy = MavenSettings.decrypt( getSecDispatcher(), selectedProxy );
            }
            else
            {
                proxy = null;
            }

            try
            {
                final BaseUrl baseUrl = BaseUrl.baseUrlFrom( getNexusUrl() );
                final UsernamePasswordAuthenticationInfo authenticationInfo;
                final Map<Protocol, ProxyInfo> proxyInfos = new HashMap<Protocol, ProxyInfo>( 1 );

                if ( server != null && server.getUsername() != null )
                {
                    getLog().info( "Using server credentials with ID=\"" + server.getId() + "\" from Maven settings." );
                    authenticationInfo =
                        new UsernamePasswordAuthenticationInfo( server.getUsername(), server.getPassword() );
                }
                else
                {
                    authenticationInfo = null;
                }

                if ( proxy != null )
                {
                    getLog().info(
                        "Using " + proxy.getProtocol().toUpperCase() + " Proxy with ID=\"" + proxy.getId()
                            + "\" from Maven settings." );
                    final UsernamePasswordAuthenticationInfo proxyAuthentication;
                    if ( proxy.getUsername() != null )
                    {
                        proxyAuthentication =
                            new UsernamePasswordAuthenticationInfo( proxy.getUsername(), proxy.getPassword() );
                    }
                    else
                    {
                        proxyAuthentication = null;
                    }
                    final ProxyInfo zProxy =
                        new ProxyInfo( Protocol.valueOf( proxy.getProtocol().toUpperCase() ), proxy.getHost(),
                            proxy.getPort(), proxyAuthentication );
                    proxyInfos.put( zProxy.getProxyProtocol(), zProxy );
                }

                final ConnectionInfo connectionInfo = new ConnectionInfo( baseUrl, authenticationInfo, proxyInfos );

                this.nexusClient = new JerseyNexusClientFactory(
                    // support v2 and v3
                    new JerseyStagingWorkflowV2SubsystemFactory(),
                    new JerseyStagingWorkflowV3SubsystemFactory()
                )
                .createFor(connectionInfo);

                getLog().debug( "NexusClient created against Nexus instance on URL: " + baseUrl.toString() + "." );
            }
            catch ( MalformedURLException e )
            {
                throw new MojoExecutionException( "Malformed Nexus base URL!", e );
            }
            catch ( UniformInterfaceException e )
            {
                throw new MojoExecutionException( "Nexus base URL does not point to a valid Nexus location: "
                    + e.getMessage(), e );
            }
            catch ( Exception e )
            {
                throw new MojoExecutionException( "Nexus connection problem: " + e.getMessage(), e );
            }
        }
        catch ( SecDispatcherException e )
        {
            throw new MojoExecutionException( "Cannot decipher credentials to be used with Nexus!", e );
        }
        catch ( MalformedURLException e )
        {
            throw new MojoExecutionException( "Malformed Nexus base URL!", e );
        }
    }

    // FIXME: This is duplicated in RemotingImpl

    private StagingWorkflowV2Service workflowService;

    protected StagingWorkflowV2Service getStagingWorkflowService()
        throws MojoExecutionException
    {
        if (workflowService == null) {
            // First try v3
            try {
                StagingWorkflowV3Service service = nexusClient.getSubsystem(StagingWorkflowV3Service.class);

                getLog().debug("Using staging v3 service");

                // configure progress monitor
                service.setProgressMonitor(new ProgressMonitorImpl(getLog()));

                // TODO: Configure these bits
                //service.setProgressTimeoutMinutes();
                //service.setProgressPauseDurationSeconds();

                workflowService = service;
            }
            catch (Exception e) {
                getLog().debug("Unable to resolve staging v3 service; falling back to v2", e);
            }

            if (workflowService == null) {
                // fallback to v2 if v3 not available
                try {
                    workflowService = nexusClient.getSubsystem(StagingWorkflowV2Service.class);
                    getLog().debug("Using staging v2 service");
                }
                catch (IllegalArgumentException e) {
                    throw new MojoExecutionException(
                        String.format("Nexus instance at base URL %s does not support staging v2; reported status: %s, reason:%s",
                            nexusClient.getConnectionInfo().getBaseUrl(),
                            nexusClient.getNexusStatus(),
                            e.getMessage()),
                        e);
                }
            }
        }

        return workflowService;
    }
}
