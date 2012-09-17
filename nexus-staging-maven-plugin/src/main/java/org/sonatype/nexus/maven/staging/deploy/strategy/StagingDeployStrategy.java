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
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.component.annotations.Component;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.core.NexusStatus;
import org.sonatype.nexus.maven.staging.deploy.DeployableArtifact;
import org.sonatype.nexus.maven.staging.deploy.StagingRepository;

import com.sonatype.nexus.staging.client.Profile;
import com.sonatype.nexus.staging.client.StagingRuleFailuresException;

/**
 * Full staging V2 deploy strategy. It perform local staging and remote staging (on remote Nexus).
 * 
 * @author cstamas
 * @since 1.1
 */
@Component( role = DeployStrategy.class, hint = Strategies.STAGING )
public class StagingDeployStrategy
    extends AbstractStagingDeployStrategy
    implements DeployStrategy
{
    /**
     * Performs local staging, but obeying the matched staging profile (to keep locally staged artifacts separated, as
     * they will end up in Nexus). For this, several REST calls are made against Nexus, to perform staging profile match
     * if needed.
     */
    @Override
    public void deployPerModule( final DeployPerModuleRequest request )
        throws ArtifactInstallationException, ArtifactDeploymentException, MojoExecutionException
    {
        getLogger().info(
            "Performing local staging (local stagingDirectory=\""
                + request.getParameters().getStagingDirectoryRoot().getAbsolutePath() + "\")..." );
        initRemoting( request.getMavenSession(), request.getParameters() );
        if ( !request.getDeployableArtifacts().isEmpty() )
        {
            // we match only for 1st in list!
            final String profileId =
                selectStagingProfile( request.getParameters(), request.getDeployableArtifacts().get( 0 ).getArtifact() );
            final File stagingDirectory =
                getStagingDirectory( request.getParameters().getStagingDirectoryRoot(), profileId );
            // deploys always to same stagingDirectory
            for ( DeployableArtifact deployableArtifact : request.getDeployableArtifacts() )
            {
                final ArtifactRepository stagingRepository = getArtifactRepositoryForDirectory( stagingDirectory );
                install( deployableArtifact.getFile(), deployableArtifact.getArtifact(), stagingRepository,
                    stagingDirectory );
            }
        }
        else
        {
            getLogger().info( "Nothing to locally stage?" );
        }
    }

    /**
     * Performs Nexus staging of locally staged artifacts.
     */
    @Override
    public void finalizeDeploy( final FinalizeDeployRequest request )
        throws ArtifactDeploymentException, MojoExecutionException
    {
        getLogger().info( "Performing remote staging..." );
        initRemoting( request.getMavenSession(), request.getParameters() );
        final File stageRoot = request.getParameters().getStagingDirectoryRoot();
        final File[] localStageRepositories = stageRoot.listFiles();
        if ( localStageRepositories == null )
        {
            getLogger().info( "We have nothing locally staged, bailing out." );
            return;
        }
        final List<StagingRepository> zappedStagingRepositories = new ArrayList<StagingRepository>();
        for ( File profileDirectory : localStageRepositories )
        {
            if ( !profileDirectory.isDirectory() )
            {
                continue;
            }

            // we do staging
            final String profileId = profileDirectory.getName();
            getLogger().info( "Remote staging locally staged directory: " + profileId );

            final NexusClient nexusClient = getRemoting().getNexusClient();

            getLogger().info( " * Connecting to Nexus on URL " + nexusClient.getConnectionInfo().getBaseUrl() );
            final NexusStatus nexusStatus = nexusClient.getNexusStatus();
            getLogger().info(
                String.format( " * Remote Nexus reported itself as version %s and edition \"%s\"",
                    nexusStatus.getVersion(), nexusStatus.getEditionLong() ) );

            final Profile stagingProfile = getRemoting().getStagingWorkflowV2Service().selectProfile( profileId );
            final StagingRepository stagingRepository = beforeUpload( request.getParameters(), stagingProfile );
            zappedStagingRepositories.add( stagingRepository );
            try
            {
                getLogger().info( " * Uploading locally staged artifacts to profile " + stagingProfile.getName() );
                deployUp( request.getMavenSession(),
                    getStagingDirectory( request.getParameters().getStagingDirectoryRoot(), profileId ),
                    getArtifactRepositoryForNexus( stagingRepository ) );
                getLogger().info( " * Upload of locally staged artifacts finished." );
                afterUpload( request.getParameters(), stagingRepository );
            }
            catch ( StagingRuleFailuresException e )
            {
                afterUploadFailure( request.getParameters(), zappedStagingRepositories, e );
                getLogger().error( "Remote staging finished with a failure." );
                throw new ArtifactDeploymentException( "Remote staging failed: " + e.getMessage(), e );
            }
            catch ( InvalidRepositoryException e )
            {
                afterUploadFailure( request.getParameters(), zappedStagingRepositories, e );
                getLogger().error( "Remote staging finished with a failure." );
                throw new ArtifactDeploymentException( "Remote staging failed: " + e.getMessage(), e );
            }
            catch ( IOException e )
            {
                afterUploadFailure( request.getParameters(), zappedStagingRepositories, e );
                getLogger().error( "Remote staging finished with a failure." );
                throw new ArtifactDeploymentException( "Remote staging failed: " + e.getMessage(), e );
            }
        }
        getLogger().info( "Remote staged " + zappedStagingRepositories.size() + " repositories, finished with success." );
    }
}
