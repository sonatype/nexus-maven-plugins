package org.sonatype.nexus.maven.staging.deploy.strategy;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.maven.mojo.logback.LogbackUtils;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.core.NexusStatus;
import org.sonatype.nexus.maven.staging.deploy.StagingRepository;
import org.sonatype.nexus.maven.staging.zapper.Zapper;
import org.sonatype.nexus.maven.staging.zapper.ZapperRequest;

import ch.qos.logback.classic.Level;

import com.sonatype.nexus.staging.client.Profile;
import com.sonatype.nexus.staging.client.StagingRuleFailuresException;

@Component( role = DeployStrategy.class, hint = Strategies.IMAGE )
public class ImageDeployStrategy
    extends AbstractStagingDeployStrategy
    implements DeployStrategy
{
    /**
     * Zapper component.
     */
    @Requirement
    private Zapper zapper;

    @Override
    public void deployPerModule( final DeployPerModuleRequest request )
        throws ArtifactInstallationException, ArtifactDeploymentException, MojoExecutionException
    {
        // nothing
    }

    @Override
    public void finalizeDeploy( final FinalizeDeployRequest request )
        throws ArtifactDeploymentException, MojoExecutionException
    {
        getLogger().info( "Staging remotely locally deployed repository..." );
        initRemoting( request.getMavenSession(), request.getParameters() );

        final NexusClient nexusClient = getRemoting().getNexusClient();

        getLogger().info( " * Connecting to Nexus on URL " + nexusClient.getConnectionInfo().getBaseUrl() );
        final NexusStatus nexusStatus = nexusClient.getNexusStatus();
        getLogger().info(
            String.format( " * Remote Nexus reported itself as version %s and edition \"%s\"",
                nexusStatus.getVersion(), nexusStatus.getEditionLong() ) );

        final String profileId = request.getParameters().getStagingProfileId();
        final Profile stagingProfile = getRemoting().getStagingWorkflowV2Service().selectProfile( profileId );
        final StagingRepository stagingRepository = beforeUpload( request.getParameters(), stagingProfile );
        try
        {
            getLogger().info( " * Uploading locally staged artifacts to profile " + stagingProfile.getName() );
            zapUp( request.getParameters().getStagingDirectoryRoot(), stagingRepository.getUrl() );
            getLogger().info( " * Upload of locally staged artifacts finished." );
            afterUpload( request.getParameters(), stagingRepository );
        }
        catch ( StagingRuleFailuresException e )
        {
            afterUploadFailure( request.getParameters(), Collections.singletonList( stagingRepository ), e );
            getLogger().error( "Remote staging finished with a failure." );
            throw new ArtifactDeploymentException( "Remote staging failed: " + e.getMessage(), e );
        }
        catch ( IOException e )
        {
            afterUploadFailure( request.getParameters(), Collections.singletonList( stagingRepository ), e );
            getLogger().error( "Remote staging finished with a failure." );
            throw new ArtifactDeploymentException( "Remote staging failed: " + e.getMessage(), e );
        }
        getLogger().info( "Remote staging finished with success." );
    }

    /**
     * Uploads the {@code sourceDirectory} to the {@code deployUrl} as a "whole". This means, that the "image"
     * (sourceDirectory) should be already prepared, as there will be no transformations applied to them, content and
     * filenames will be deploy as-is.
     * 
     * @param sourceDirectory
     * @param deployUrl
     * @throws IOException
     */
    protected void zapUp( final File sourceDirectory, final String deployUrl )
        throws IOException
    {
        final ZapperRequest request = new ZapperRequest( sourceDirectory, deployUrl );

        final Server server = getRemoting().getServer();
        if ( server != null )
        {
            request.setRemoteUsername( server.getUsername() );
            request.setRemotePassword( server.getPassword() );
        }

        final Proxy proxy = getRemoting().getProxy();
        if ( proxy != null )
        {
            request.setProxyProtocol( proxy.getProtocol() );
            request.setProxyHost( proxy.getHost() );
            request.setProxyPort( proxy.getPort() );
            request.setProxyUsername( proxy.getUsername() );
            request.setProxyPassword( proxy.getPassword() );
        }

        // Zapper is a bit "chatty", if no Maven debug session is ongoing, then up logback to WARN
        if ( getLogger().isDebugEnabled() )
        {
            LogbackUtils.syncLogLevelWithLevel( Level.DEBUG );
        }
        else
        {
            LogbackUtils.syncLogLevelWithLevel( Level.WARN );
        }

        zapper.deployDirectory( request );

        if ( getLogger().isDebugEnabled() )
        {
            LogbackUtils.syncLogLevelWithLevel( Level.DEBUG );
        }
        else
        {
            LogbackUtils.syncLogLevelWithLevel( Level.INFO );
        }
    }

}
