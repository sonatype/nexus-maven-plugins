package org.sonatype.nexus.plugin;

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
import org.sonatype.maven.mojo.execution.MojoExecution;
import org.sonatype.maven.mojo.logback.LogbackUtils;
import org.sonatype.maven.mojo.settings.MavenSettings;
import org.sonatype.nexus.client.BaseUrl;
import org.sonatype.nexus.client.ConnectionInfo;
import org.sonatype.nexus.client.NexusClient;
import org.sonatype.nexus.client.Protocol;
import org.sonatype.nexus.client.ProxyInfo;
import org.sonatype.nexus.client.UsernamePasswordAuthenticationInfo;
import org.sonatype.nexus.client.internal.JerseyNexusClientFactory;
import org.sonatype.nexus.client.srv.staging.StagingWorkflowV2Service;
import org.sonatype.nexus.client.srv.staging.internal.StagingFeatures;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

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
     * Specifies an alternative staging directory to which the project artifacts should be deployed. By default, staging
     * will happen under {@code /target} folder of the top level module (from where Maven was invoked).
     * 
     * @parameter expression="${altStagingDirectory}"
     */
    private File altStagingDirectory;

    /**
     * The base URL for a Nexus Professional instance that includes the nexus-staging-plugin.
     * 
     * @parameter expression="${nexusUrl}"
     */
    private String nexusUrl;

    /**
     * The ID of the server entry in the Maven settings.xml from which to pick credentials to contact the Insight
     * service.
     * 
     * @parameter expression="${serverId}"
     */
    private String serverId = "nexus";

    /**
     * The repository "description" to pass to Nexus when repository staging workflow step is made. If none passed a
     * server side defaults are applied.
     * 
     * @parameter expression="${description}"
     */
    private String description = "Closed by nexus-staging-maven-plugin";

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

    protected MavenSession getMavenSession()
    {
        return mavenSession;
    }

    protected SecDispatcher getSecDispatcher()
    {
        return secDispatcher;
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
        return MojoExecution.getFirstProjectWithMojoInExecution( mavenSession, mojoExecution.getGroupId(),
            mojoExecution.getArtifactId(), null );
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
            return isThisLastProjectWithMojoInExecution( mojoExecution.getGoal() );
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
        return MojoExecution.isCurrentTheLastProjectWithMojoInExecution( mavenSession, mojoExecution.getGroupId(),
            mojoExecution.getArtifactId(), goal );
    }

    protected File getStagingDirectory()
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
                // the target of 1st project having this mojo defined
                return new File( firstWithThisMojo.getBasedir().getAbsolutePath(), "target/nexus-staging" );
            }
            else
            {
                // top level (invocation place)
                return new File( getMavenSession().getExecutionRootDirectory() + "/target/nexus-staging" );
            }
        }
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
                    getLog().debug( "Using server credentials with ID \"" + getServerId() + "\"." );
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
                getLog().info( "Using configured Proxy from Maven settings." );
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
            final BaseUrl baseUrl = BaseUrl.create( getNexusUrl() );
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
                    new ProxyInfo( Protocol.valueOf( proxy.getProtocol().toLowerCase() ), proxy.getHost(),
                        proxy.getPort(), proxyAuthentication );
                proxyInfos.put( zProxy.getProxyProtocol(), zProxy );
            }

            final ConnectionInfo connectionInfo = new ConnectionInfo( baseUrl, authenticationInfo, proxyInfos );
            LogbackUtils.syncLogLevelWithMaven( getLog() );
            this.nexusClient = new JerseyNexusClientFactory( StagingFeatures.defaults() ).createFor( connectionInfo );
            getLog().debug( "NexusClient created aginst Nexus instance on URL: " + baseUrl.toString() + "." );
        }
        catch ( MalformedURLException e )
        {
            throw new MojoExecutionException( "Malformed Nexus base URL!", e );
        }
        catch ( UniformInterfaceException e )
        {
            throw new MojoExecutionException(
                "Malformed Nexus base URL or it does not points to a valid Nexus location !", e );
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
        final StagingWorkflowV2Service stagingService = getNexusClient().getSubsystem( StagingWorkflowV2Service.class );

        if ( stagingService == null )
        {
            throw new MojoExecutionException(
                "Nexus instance at base URL "
                    + getNexusClient().getConnectionInfo().getBaseUrl().toString()
                    + " does not support Staging V2 (wrong edition, wrong version or nexus-staging-plugin is not installed)! Reported status: "
                    + getNexusClient().getConnectionStatus() );
        }

        return stagingService;
    }
}
