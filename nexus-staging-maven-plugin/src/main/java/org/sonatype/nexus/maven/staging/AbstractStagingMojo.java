/**
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
package org.sonatype.nexus.maven.staging;

import java.io.File;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.maven.mojo.execution.MojoExecution;
import org.sonatype.maven.mojo.logback.LogbackUtils;
import org.sonatype.maven.mojo.settings.MavenSettings;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.rest.BaseUrl;
import org.sonatype.nexus.client.rest.ConnectionInfo;
import org.sonatype.nexus.client.rest.Protocol;
import org.sonatype.nexus.client.rest.ProxyInfo;
import org.sonatype.nexus.client.rest.UsernamePasswordAuthenticationInfo;
import org.sonatype.nexus.client.rest.jersey.JerseyNexusClientFactory;
import org.sonatype.nexus.maven.staging.deploy.DeployMojo;
import org.sonatype.nexus.maven.staging.workflow.CloseStageRepositoryMojo;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;
import com.sonatype.nexus.staging.client.rest.JerseyStagingWorkflowV2SubsystemFactory;
import com.sun.jersey.api.client.UniformInterfaceException;

public abstract class AbstractStagingMojo
    extends AbstractMojo
{
    // Maven sourced stuff

    /**
     * Maven Session.
     * 
     * @parameter default-value="${session}"
     * @required
     * @readonly
     */
    private MavenSession mavenSession;

    /**
     * Plugin groupId. While these are accessible from {@link #mojoExecution}, the methods are present only in Maven 3+.
     * Requirement is to work in Maven2 also, hence, we use a "compatible way" to obtain these values instead.
     * 
     * @parameter default-value="${plugin.groupId}"
     * @readonly
     */
    private String pluginGroupId;

    /**
     * Plugin artifactId. While these are accessible from {@link #mojoExecution}, the methods are present only in Maven
     * 3+. Requirement is to work in Maven2 also, hence, we use a "compatible way" to obtain these values instead.
     * 
     * @parameter default-value="${plugin.artifactId}"
     * @readonly
     */
    private String pluginArtifactId;

    /**
     * Plugin version. While these are accessible from {@link #mojoExecution}, the methods are present only in Maven 3+.
     * Requirement is to work in Maven2 also, hence, we use a "compatible way" to obtain these values instead.
     * 
     * @parameter default-value="${plugin.version}"
     * @readonly
     */
    private String pluginVersion;

    /**
     * Mojo execution.
     * 
     * @parameter default-value="${mojoExecution}"
     * @required
     * @readonly
     */
    private org.apache.maven.plugin.MojoExecution mojoExecution;

    /**
     * Sec Dispatcher.
     * 
     * @component role="org.sonatype.plexus.components.sec.dispatcher.SecDispatcher" hint="default"
     */
    private SecDispatcher secDispatcher;

    // user supplied parameters (from maven)

    /**
     * Flag whether Maven is currently in online/offline mode.
     * 
     * @parameter default-value="${settings.offline}"
     * @readonly
     */
    private boolean offline;

    // user supplied parameters (staging related)

    /**
     * Specifies an alternative staging directory to which the project artifacts should be "locally staged". By default,
     * staging directory will be looked for under {@code $}{{@code project.build.directory} {@code /nexus-staging}
     * folder of the first encountered module that has this Mojo defined for execution (Warning: this means, if top
     * level POM is an aggregator, it will be NOT in top level!).
     * 
     * @parameter expression="${altStagingDirectory}"
     */
    private File altStagingDirectory;

    /**
     * The base URL for a Nexus Professional instance that includes the nexus-staging-plugin.
     * 
     * @parameter expression="${nexusUrl}"
     * @required
     */
    private String nexusUrl;

    /**
     * The ID of the server entry in the Maven settings.xml from which to pick credentials to contact remote Nexus.
     * 
     * @parameter expression="${serverId}"
     * @required
     */
    private String serverId = "nexus";

    /**
     * The repository "description" to pass to Nexus when repository staging workflow step is made. If none passed in,
     * plugin defaults are applied.
     * 
     * @parameter expression="${description}"
     */
    private String description;

    /**
     * Controls whether the staging repository is kept or not (it will be dropped) in case of staging rule failure when
     * "close" action is performed against it. This is applied in both cases, {@link DeployMojo} and
     * {@link CloseStageRepositoryMojo} invocations.
     * 
     * @parameter expression="${keepStagingRepositoryOnCloseRuleFailure}"
     */
    private boolean keepStagingRepositoryOnCloseRuleFailure = false;

    // == getters for stuff above

    protected String getNexusUrl()
    {
        return nexusUrl;
    }

    protected String getServerId()
    {
        return serverId;
    }

    protected String getDescription()
    {
        return description;
    }

    public boolean isKeepStagingRepositoryOnCloseRuleFailure()
    {
        return keepStagingRepositoryOnCloseRuleFailure;
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

    protected MavenSession getMavenSession()
    {
        return mavenSession;
    }

    protected SecDispatcher getSecDispatcher()
    {
        return secDispatcher;
    }

    protected String getPluginGroupId()
    {
        return pluginGroupId;
    }

    protected String getPluginArtifactId()
    {
        return pluginArtifactId;
    }

    protected String getPluginVersion()
    {
        return pluginVersion;
    }

    protected String getPluginGav()
    {
        return getPluginGroupId() + ":" + getPluginArtifactId() + ":" + getPluginVersion();
    }

    // == common methods

    /**
     * Fails if Maven is invoked offline.
     * 
     * @throws MojoFailureException if Maven is invoked offline.
     */
    protected void failIfOffline()
        throws MojoFailureException
    {
        if ( offline )
        {
            throw new MojoFailureException( "Cannot deploy artifacts when Maven is in offline mode" );
        }
    }

    /**
     * Returns the first project in reactor that has this plugin defined.
     * 
     * @return
     */
    protected MavenProject getFirstProjectWithThisPluginDefined()
    {
        return MojoExecution.getFirstProjectWithMojoInExecution( mavenSession, pluginGroupId, pluginArtifactId, null );
    }

    /**
     * In case of ordinary build, it returns {@code true} if the current project is the last one being executed in this
     * build that has this Mojo defined. In case of direct invocation of this Mojo over CLI, it returns {@code true} if
     * the current project is the last one being executed in this build.
     * 
     * @return true if last project is being built.
     */
    protected boolean isThisLastProjectWithThisMojoInExecution()
    {
        if ( "default-cli".equals( mojoExecution.getExecutionId() ) )
        {
            return MojoExecution.isCurrentTheLastProjectInExecution( mavenSession );
        }
        else
        {
            // method mojoExecution.getGoal() is added in maven3!
            return isThisLastProjectWithMojoInExecution( mojoExecution.getMojoDescriptor().getGoal() );
        }
    }

    /**
     * Returns true if the current project is the last one being executed in this build that has passed in goal
     * execution defined.
     * 
     * @return true if last project is being built.
     */
    protected boolean isThisLastProjectWithMojoInExecution( final String goal )
    {
        return MojoExecution.isCurrentTheLastProjectWithMojoInExecution( mavenSession, pluginGroupId, pluginArtifactId,
            goal );
    }

    protected File getStagingDirectoryRoot()
    {
        if ( altStagingDirectory != null )
        {
            return altStagingDirectory;
        }
        else
        {
            final MavenProject firstWithThisMojo = getFirstProjectWithThisPluginDefined();
            if ( firstWithThisMojo != null )
            {
                final File firstWithThisMojoBuildDir;
                if ( firstWithThisMojo.getBuild() != null && firstWithThisMojo.getBuild().getDirectory() != null )
                {
                    firstWithThisMojoBuildDir =
                        new File( firstWithThisMojo.getBuild().getDirectory() ).getAbsoluteFile();
                }
                else
                {
                    firstWithThisMojoBuildDir = new File( firstWithThisMojo.getBasedir().getAbsoluteFile(), "target" );
                }
                return new File( firstWithThisMojoBuildDir, "nexus-staging" );
            }
            else
            {
                // top level (invocation place with some sensible defaults)
                // TODO: what can we do here? Do we have MavenProject at all?
                return new File( getMavenSession().getExecutionRootDirectory() + "/target/nexus-staging" );
            }
        }
    }

    protected static final String DIRECT_UPLOAD = "NONE";

    protected File getStagingDirectory( final String profileId )
        throws MojoExecutionException
    {
        final File root = getStagingDirectoryRoot();
        if ( StringUtils.isBlank( profileId ) )
        {
            throw new MojoExecutionException(
                "Internal bug: passed in profileId must be non-null and non-empty string!" );
        }
        return new File( root, profileId );
    }

    // == TRANSPORT

    /**
     * The user selected server to get credentials from.
     */
    private Server server;

    /**
     * The maven configured HTTP proxy to use, if any (or null).
     */
    private Proxy proxy;

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

        try
        {
            if ( getServerId() != null )
            {
                final Server server = MavenSettings.selectServer( getMavenSession().getSettings(), getServerId() );
                if ( server != null )
                {
                    this.server = MavenSettings.decrypt( getSecDispatcher(), server );
                    getLog().info( "Using server credentials with ID=\"" + getServerId() + "\" from Maven settings." );
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

            final Proxy proxy = MavenSettings.selectProxy( getMavenSession().getSettings(), deployUrl );
            if ( proxy != null )
            {
                this.proxy = MavenSettings.decrypt( getSecDispatcher(), proxy );
                getLog().info(
                    "Using " + proxy.getProtocol().toUpperCase() + " Proxy with ID=\"" + proxy.getId()
                        + "\" from Maven settings." );
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

    /**
     * The NexusClient instance.
     */
    private NexusClient nexusClient;

    /**
     * Creates an instance of Nexus Client using generally set server and proxy.
     * 
     * @throws ArtifactDeploymentException
     */
    protected void createNexusClient()
        throws MojoExecutionException
    {
        createNexusClient( getServer(), getProxy() );
    }

    /**
     * Creates an instance of Nexus Client using specific server and proxy.
     * 
     * @throws ArtifactDeploymentException
     */
    protected void createNexusClient( final Server server, final Proxy proxy )
        throws MojoExecutionException
    {
        try
        {
            final BaseUrl baseUrl = BaseUrl.baseUrlFrom( getNexusUrl() );
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
            LogbackUtils.syncLogLevelWithMaven( getLog() );
            this.nexusClient =
                new JerseyNexusClientFactory( new JerseyStagingWorkflowV2SubsystemFactory() ).createFor( connectionInfo );
            getLog().debug( "NexusClient created aginst Nexus instance on URL: " + baseUrl.toString() + "." );
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

    protected Server getServer()
    {
        return server;
    }

    protected Proxy getProxy()
    {
        return proxy;
    }

    protected NexusClient getNexusClient()
    {
        return nexusClient;
    }

    protected StagingWorkflowV2Service getStagingWorkflowService()
        throws MojoExecutionException
    {
        try
        {
            return getNexusClient().getSubsystem( StagingWorkflowV2Service.class );
        }
        catch ( IllegalArgumentException e )
        {
            throw new MojoExecutionException( "Nexus instance at base URL "
                + getNexusClient().getConnectionInfo().getBaseUrl().toString()
                + " does not support Staging V2 Reported status: " + getNexusClient().getNexusStatus() + ", reason:"
                + e.getMessage(), e );
        }
    }
}
