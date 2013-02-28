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
package org.sonatype.nexus.maven.staging.deploy.strategy;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import com.sonatype.nexus.staging.client.StagingWorkflowV3Service;
import com.sonatype.nexus.staging.client.rest.JerseyStagingWorkflowV3SubsystemFactory;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.maven.mojo.settings.MavenSettings;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.rest.BaseUrl;
import org.sonatype.nexus.client.rest.ConnectionInfo;
import org.sonatype.nexus.client.rest.Protocol;
import org.sonatype.nexus.client.rest.ProxyInfo;
import org.sonatype.nexus.client.rest.UsernamePasswordAuthenticationInfo;
import org.sonatype.nexus.client.rest.jersey.JerseyNexusClientFactory;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;
import com.google.common.base.Preconditions;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;
import com.sonatype.nexus.staging.client.rest.JerseyStagingWorkflowV2SubsystemFactory;
import com.sun.jersey.api.client.UniformInterfaceException;

public class RemotingImpl
    implements Remoting
{
    private static final Logger log = LoggerFactory.getLogger(RemotingImpl.class);

    private final MavenSession mavenSession;

    private final StagingParameters parameters;

    private final SecDispatcher secDispatcher;

    private Server server;

    private Proxy proxy;

    private NexusClient nexusClient;

    public RemotingImpl( final MavenSession mavenSession, final StagingParameters parameters,
                            final SecDispatcher secDispatcher )
        throws MojoExecutionException
    {
        this.mavenSession = Preconditions.checkNotNull( mavenSession );
        this.parameters = Preconditions.checkNotNull( parameters );
        this.secDispatcher = Preconditions.checkNotNull( secDispatcher );
        init( getMavenSession(), getParameters() );
    }

    protected MavenSession getMavenSession()
    {
        return mavenSession;
    }

    protected StagingParameters getParameters()
    {
        return parameters;
    }

    protected SecDispatcher getSecDispatcher()
    {
        return secDispatcher;
    }

    protected void init( MavenSession mavenSession, StagingParameters parameters )
        throws MojoExecutionException
    {
        if ( StringUtils.isBlank( parameters.getNexusUrl() ) )
        {
            throw new MojoExecutionException(
                "The URL against which transport should be established is not defined! (use \"-DnexusUrl=http://host/nexus\" on CLI or configure it in POM)" );
        }

        try
        {
            if ( !StringUtils.isBlank( parameters.getServerId() ) )
            {
                final Server server =
                    MavenSettings.selectServer( getMavenSession().getSettings(), parameters.getServerId() );
                if ( server != null )
                {
                    this.server = MavenSettings.decrypt( getSecDispatcher(), server );
                }
                else
                {
                    throw new MojoExecutionException( "Server credentials with ID \"" + parameters.getServerId()
                        + "\" not found!" );
                }
            }
            else
            {
                throw new MojoExecutionException(
                    "Server credentials to use in transport are not defined! (use \"-DserverId=someServerId\" on CLI or configure it in POM)" );
            }

            final Proxy proxy = MavenSettings.selectProxy( getMavenSession().getSettings(), parameters.getNexusUrl() );
            if ( proxy != null )
            {
                this.proxy = MavenSettings.decrypt( getSecDispatcher(), proxy );
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

    @Override
    public Server getServer()
    {
        return server;
    }

    @Override
    public Proxy getProxy()
    {
        return proxy;
    }

    @Override
    public synchronized NexusClient getNexusClient()
        throws MojoExecutionException
    {
        if ( nexusClient == null )
        {
            createNexusClient();
        }
        return nexusClient;
    }

    // FIXME: This is duplicated in AbstractStagingActionMojo

    private StagingWorkflowV2Service workflowService;

    public StagingWorkflowV2Service getStagingWorkflowV2Service()
        throws MojoExecutionException
    {
        NexusClient nexusClient = getNexusClient();

        if (workflowService == null) {
            // First try v3
            try {
                workflowService = nexusClient.getSubsystem( StagingWorkflowV3Service.class );
            }
            catch (Exception e) {
                log.debug("Unable to resolve staging v3 service; falling back to v2", e);
            }

            if (workflowService == null) {
                // fallback to v2 if v3 not available
                try
                {
                    workflowService = nexusClient.getSubsystem( StagingWorkflowV2Service.class );
                }
                catch ( IllegalArgumentException e )
                {
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

    protected void createNexusClient()
        throws MojoExecutionException
    {
        try
        {
            final BaseUrl baseUrl = BaseUrl.baseUrlFrom( getParameters().getNexusUrl() );
            final UsernamePasswordAuthenticationInfo authenticationInfo;
            final Map<Protocol, ProxyInfo> proxyInfos = new HashMap<Protocol, ProxyInfo>( 1 );

            if ( server != null && server.getUsername() != null )
            {
                authenticationInfo =
                    new UsernamePasswordAuthenticationInfo( server.getUsername(), server.getPassword() );
            }
            else
            {
                authenticationInfo = null;
            }

            if ( proxy != null )
            {
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
}
