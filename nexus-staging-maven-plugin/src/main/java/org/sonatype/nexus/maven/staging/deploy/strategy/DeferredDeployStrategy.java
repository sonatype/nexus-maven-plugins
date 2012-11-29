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

import java.io.File;
import java.io.IOException;

import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.component.annotations.Component;
import org.sonatype.nexus.maven.staging.deploy.DeployableArtifact;

/**
 * Deferred deploy strategy, that locally installs the stuff to be deployed (together with maintaining an "index" of
 * deployable artifacts). At the reactor build end, remote deploys happens driven by "index".
 * 
 * @author cstamas
 * @since 1.1
 */
@Component( role = DeployStrategy.class, hint = Strategies.DEFERRED )
public class DeferredDeployStrategy
    extends AbstractDeployStrategy
{
    private static final String DEFERRED_UPLOAD = "deferred";

    /**
     * Performs local install plus maintains the index file, that contains needed informations needed to perform remote
     * deploys.
     */
    @Override
    public void deployPerModule( final DeployPerModuleRequest request )
        throws ArtifactInstallationException, ArtifactDeploymentException, MojoExecutionException
    {
        getLogger().info(
            "Performing deferred deploys (local stagingDirectory=\""
                + request.getParameters().getStagingDirectoryRoot().getAbsolutePath() + "\")..." );
        if ( !request.getDeployableArtifacts().isEmpty() )
        {
            // deploys always to same stagingDirectory
            final File stagingDirectory =
                getStagingDirectory( request.getParameters().getStagingDirectoryRoot(), DEFERRED_UPLOAD );
            final ArtifactRepository stagingRepository = getArtifactRepositoryForDirectory( stagingDirectory );
            for ( DeployableArtifact deployableArtifact : request.getDeployableArtifacts() )
            {
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
     * Performs "bulk" remote deploy, or locally installed artifacts, and is driven by "index" file.
     */
    @Override
    public void finalizeDeploy( final FinalizeDeployRequest request )
        throws ArtifactDeploymentException, MojoExecutionException
    {
        getLogger().info( "Deploying remotely..." );
        final File stagingDirectory =
            getStagingDirectory( request.getParameters().getStagingDirectoryRoot(), DEFERRED_UPLOAD );
        if ( !stagingDirectory.isDirectory() )
        {
            getLogger().warn(
                "Nothing to deploy, directory \"" + stagingDirectory.getAbsolutePath() + "\" does not exists!" );
            return;
        }

        // we do direct upload
        getLogger().info( "Bulk deploying locally gathered artifacts from directory: " );
        try
        {
            // prepare the local staging directory
            // we have normal deploy
            final ArtifactRepository deploymentRepository = getDeploymentRepository( request.getMavenSession() );
            getLogger().info(
                " * Bulk deploying locally gathered snapshot artifacts to URL " + deploymentRepository.getUrl() );
            deployUp( request.getMavenSession(), stagingDirectory, deploymentRepository );
            getLogger().info( " * Bulk deploy of locally gathered snapshot artifacts finished." );
        }
        catch ( IOException e )
        {
            getLogger().error( "Upload of locally staged directory finished with a failure." );
            throw new ArtifactDeploymentException( "Remote staging failed: " + e.getMessage(), e );
        }

        getLogger().info( "Remote deploy finished with success." );
    }
}
