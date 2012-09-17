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

/**
 * Image deploy strategy, that deploys the locally present directory structure to remote in "as is" form.
 * 
 * @author cstamas
 * @since 1.1
 */
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

    /**
     * This method is actually unused in this strategy, as the "image" to be deployed is prepared by something else. For
     * example, it might be prepared with maven-deploy-plugin using altDeploymentRepository switch pointing to local
     * file system.
     */
    @Override
    public void deployPerModule( final DeployPerModuleRequest request )
        throws ArtifactInstallationException, ArtifactDeploymentException, MojoExecutionException
    {
        // nothing
    }

    /**
     * Remote deploys the "image", using {@link #zapUp(File, String)}.
     */
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
