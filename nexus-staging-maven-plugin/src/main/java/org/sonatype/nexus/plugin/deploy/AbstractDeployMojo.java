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
package org.sonatype.nexus.plugin.deploy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.maven.mojo.logback.LogbackUtils;
import org.sonatype.nexus.client.core.NexusStatus;
import org.sonatype.nexus.plugin.AbstractStagingMojo;

import ch.qos.logback.classic.Level;

import com.sonatype.nexus.staging.client.Profile;
import com.sonatype.nexus.staging.client.ProfileMatchingParameters;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;
import com.sun.jersey.api.client.UniformInterfaceException;

/**
 * Abstract class for deploy related mojos.
 * 
 * @author cstamas
 * @since 2.1
 */
public abstract class AbstractDeployMojo
    extends AbstractStagingMojo
{
    public static final String STAGING_REPOSITORY_PROPERTY_FILE_NAME_SUFFIX = ".properties";

    public static final String STAGING_REPOSITORY_ID = "stagingRepository.id";

    public static final String STAGING_REPOSITORY_PROFILE_ID = "stagingRepository.profileId";

    public static final String STAGING_REPOSITORY_URL = "stagingRepository.url";

    public static final String STAGING_REPOSITORY_MANAGED = "stagingRepository.managed";

    // Components

    /**
     * @component
     */
    private ArtifactDeployer deployer;

    /**
     * Component used to create an artifact.
     * 
     * @component
     */
    private ArtifactFactory artifactFactory;

    /**
     * Component used to create a repository.
     * 
     * @component
     */
    private ArtifactRepositoryFactory repositoryFactory;

    /**
     * Map that contains the layouts.
     * 
     * @component role="org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout" hint="default"
     */
    private ArtifactRepositoryLayout defaultArtifactRepositoryLayout;

    /**
     * Map that contains the layouts.
     * 
     * @component role="org.sonatype.nexus.plugin.deploy.Zapper" hint="default"
     */
    private Zapper zapper;

    /**
     * @parameter default-value="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository localRepository;

    // User configurable parameters

    /**
     * Specifies the profile ID on remote Nexus against which staging should happen. If not given, Nexus will be asked
     * to perform a "match" and that profile will be used.
     * 
     * @parameter expression="${stagingProfileId}"
     */
    private String stagingProfileId;

    /**
     * Specifies the (opened) staging repository ID on remote Nexus against which staging should happen. If not given,
     * Nexus will be asked to create one for us and that will be used.
     * 
     * @parameter expression="${stagingRepositoryId}"
     */
    private String stagingRepositoryId;

    /**
     * Specifies the URL of remote Nexus to deploy to. If specified, no Staging V2 kicks in, just an "ordinary" deploy
     * will happen, deploying the locally staged artifacts (still, deferred deploy happens, they will be uploaded
     * together).
     * 
     * @parameter expression="${deployUrl}"
     */
    private String deployUrl;

    /**
     * The key-value pairs to "tag" the staging repository.
     * 
     * @parameter
     */
    private Map<String, String> tags;

    /**
     * Controls whether the plugin remove or keep the staging repository that performed an IO exception during upload,
     * hence, it's contents are partial Defaults to {{false}}. If {{true}}, even in case of upload failure, the staging
     * repository (with partial content) will be left as is, left to the user to do whatever he wants.
     * 
     * @parameter expression="${keepStagingRepositoryOnFailure}"
     */
    private boolean keepStagingRepositoryOnFailure = false;

    /**
     * Set this to {@code true} to bypass staging repository closing at the workflow end.
     * 
     * @parameter expression="${skipStagingRepositoryClose}"
     */
    private boolean skipStagingRepositoryClose = false;

    // getters

    protected ArtifactRepository getLocalRepository()
    {
        return localRepository;
    }

    protected ArtifactFactory getArtifactFactory()
    {
        return artifactFactory;
    }

    // methods

    /**
     * Stages an artifact from a particular file locally.
     * 
     * @param source the file to stage
     * @param artifact the artifact definition
     * @param stagingRepository the repository to stage to
     * @param localRepository the local repository to install into
     * @throws ArtifactDeploymentException if an error occurred deploying the artifact
     */
    protected void stageLocally( File source, Artifact artifact, ArtifactRepository localRepository,
                                 final File stagingDirectory )
        throws ArtifactDeploymentException, MojoExecutionException
    {
        deployer.deploy( source, artifact, getStagingRepositoryFor( stagingDirectory ), localRepository );
    }

    /**
     * Stages remotely.
     * 
     * @param source the file to stage
     * @param artifact the artifact definition
     * @param stagingRepository the repository to stage to
     * @param localRepository the local repository to install into
     * @throws ArtifactDeploymentException if an error occurred deploying the artifact
     */
    protected void stageRemotely()
        throws ArtifactDeploymentException, MojoExecutionException
    {
        final File stageRoot = getStagingDirectoryRoot();
        final File[] localStageRepositories = stageRoot.listFiles();
        if ( localStageRepositories == null )
        {
            getLog().info( "We have nothing locally staged, bailing out." );
            return;
        }
        for ( File profileDirectory : localStageRepositories )
        {
            if ( !profileDirectory.isDirectory() )
            {
                continue;
            }

            final String profileId = profileDirectory.getName();

            getLog().info( "Uploading locally staged directory: " + profileId );

            if ( DIRECT_UPLOAD.equals( profileId ) )
            {
                try
                {
                    // we have normal deploy
                    zapUp( getStagingDirectory( profileId ), deployUrl );
                }
                catch ( IOException e )
                {
                    throw new ArtifactDeploymentException( "Cannot deploy!", e );
                }
            }
            else
            {
                // we do staging
                getLog().info( "Performing staging against Nexus on URL " + getNexusUrl() );
                final NexusStatus nexusStatus = getNexusClient().getNexusStatus();
                getLog().info(
                    String.format( " * Remote Nexus reported itself as version %s and edition \"%s\"",
                        nexusStatus.getVersion(), nexusStatus.getEditionLong() ) );

                boolean successful = false;
                final Profile stagingProfile = selectStagingProfileById( profileId );
                final StagingRepository stagingRepository = beforeUpload( stagingProfile );
                try
                {
                    final String deployUrl = calculateUploadUrl( stagingRepository );
                    zapUp( getStagingDirectory( profileId ), deployUrl );
                    successful = true;
                }
                catch ( IOException e )
                {
                    throw new ArtifactDeploymentException( "Cannot deploy!", e );
                }
                finally
                {
                    afterUpload( stagingRepository, skipStagingRepositoryClose, successful );
                }
            }
        }
    }

    protected void zapUp( final File sourceDirectory, final String deployUrl )
        throws IOException
    {
        final ZapperRequest request = new ZapperRequest( sourceDirectory, deployUrl );

        final Server server = getServer();
        if ( server != null )
        {
            request.setRemoteUsername( server.getUsername() );
            request.setRemotePassword( server.getPassword() );
        }

        final Proxy proxy = getProxy();
        if ( proxy != null )
        {
            request.setProxyProtocol( proxy.getProtocol() );
            request.setProxyHost( proxy.getHost() );
            request.setProxyPort( proxy.getPort() );
            request.setProxyUsername( proxy.getUsername() );
            request.setProxyPassword( proxy.getPassword() );
        }

        getLog().info( " * Uploading locally staged artifacts to: " + deployUrl );
        // Zapper is a bit "chatty", if no Maven debug session is ongoing, then up logback to WARN
        if ( getLog().isDebugEnabled() )
        {
            LogbackUtils.syncLogLevelWithMaven( getLog() );
        }
        else
        {
            LogbackUtils.syncLogLevelWithLevel( Level.WARN );
        }
        zapper.deployDirectory( request );
        LogbackUtils.syncLogLevelWithMaven( getLog() );
        getLog().info( " * Upload of locally staged artifacts done." );
    }

    // ==

    protected String selectStagingProfile()
        throws MojoExecutionException
    {
        if ( deployUrl != null )
        {
            getLog().info( "Preparing normal deploy against URL: " + deployUrl );
            createTransport( deployUrl );
            return DIRECT_UPLOAD;
        }
        else if ( getNexusUrl() != null )
        {
            try
            {
                createTransport( getNexusUrl() );
                createNexusClient( getServer(), getProxy() );

                getLog().info( "Preparing staging against Nexus on URL " + getNexusUrl() );
                final NexusStatus nexusStatus = getNexusClient().getNexusStatus();
                getLog().info(
                    String.format( " * Remote Nexus reported itself as version %s and edition \"%s\"",
                        nexusStatus.getVersion(), nexusStatus.getEditionLong() ) );
                final StagingWorkflowV2Service stagingService = getStagingWorkflowService();

                final MavenProject currentProject = getMavenSession().getCurrentProject();
                Profile stagingProfile;
                // if profile is not "targeted", perform a match and save the result
                if ( StringUtils.isBlank( stagingProfileId ) )
                {
                    final ProfileMatchingParameters params =
                        new ProfileMatchingParameters( currentProject.getGroupId(), currentProject.getArtifactId(),
                            currentProject.getVersion() );
                    stagingProfile = stagingService.matchProfile( params );
                    stagingProfileId = stagingProfile.getId();
                    getLog().info( " * Using staging profile ID \"" + stagingProfileId + "\" (matched by Nexus)." );
                }
                else
                {
                    stagingProfile = stagingService.selectProfile( stagingProfileId );
                    getLog().info( " * Using staging profile ID \"" + stagingProfileId + "\" (configured by user)." );
                }
                return stagingProfileId;
            }
            catch ( UniformInterfaceException e )
            {
                throw new MojoExecutionException( "Staging workflow failure!", e );
            }
        }
        else
        {
            throw new MojoExecutionException( "No deploy URL set, nor Nexus BaseURL given!" );
        }
    }

    /**
     * Returns the Profile instance selected by ID on remote Nexus.
     * 
     * @param stagingProfileId
     * @return
     * @throws MojoExecutionException
     */
    protected Profile selectStagingProfileById( final String stagingProfileId )
        throws MojoExecutionException
    {
        final StagingWorkflowV2Service stagingService = getStagingWorkflowService();
        return stagingService.selectProfile( stagingProfileId );
    }

    protected StagingRepository beforeUpload( final Profile stagingProfile )
        throws MojoExecutionException
    {
        try
        {
            final StagingWorkflowV2Service stagingService = getStagingWorkflowService();
            if ( StringUtils.isBlank( stagingRepositoryId ) )
            {
                String createdStagingRepositoryId =
                    stagingService.startStaging( stagingProfile, getDescriptionWithDefaultsForAction( "Started" ), tags );
                // store the one just created for us, as it means we need to "babysit" it (close or drop, depending
                // on outcome)
                if ( tags != null && !tags.isEmpty() )
                {
                    getLog().info(
                        " * Created staging repository with ID \"" + createdStagingRepositoryId + "\", applied tags: "
                            + tags );
                }
                else
                {
                    getLog().info( " * Created staging repository with ID \"" + createdStagingRepositoryId + "\"." );
                }
                return new StagingRepository( stagingProfile, createdStagingRepositoryId, true );
            }
            else
            {
                getLog().info(
                    " * Using non-managed staging repository with ID \"" + stagingRepositoryId
                        + "\" (we are NOT managing it)." ); // we will not close it! This might be created by some
                                                            // other automated component
                return new StagingRepository( stagingProfile, stagingRepositoryId, false );
            }
        }
        catch ( UniformInterfaceException e )
        {
            throw new MojoExecutionException( "Staging workflow failure!", e );
        }
    }

    /**
     * Returns the URL where upload should happen to stage into given profile-repositoryID combination.
     * 
     * @param stagingProfile
     * @param stagingRepositoryId
     * @return
     * @throws MojoExecutionException
     */
    protected String calculateUploadUrl( final StagingRepository stagingRepository )
        throws MojoExecutionException
    {
        final StagingWorkflowV2Service stagingService = getStagingWorkflowService();
        return stagingService.startedRepositoryBaseUrl( stagingRepository.getProfile(),
            stagingRepository.getRepositoryId() );
    }

    /**
     * Performs various cleanup after one staging repository is uploaded. Is not called when "normal deploy" is done.
     * 
     * @param stagingProfile
     * @param stagingRepositoryId
     * @param managed
     * @param skipClose
     * @param successful
     * @throws ArtifactDeploymentException
     * @throws MojoExecutionException
     */
    protected void afterUpload( final StagingRepository stagingRepository, final boolean skipClose,
                                final boolean successful )
        throws ArtifactDeploymentException, MojoExecutionException
    {
        // in any other case nothing happens
        // by having stagingRepositoryId string non-empty, it means we created it, hence, we are managing it too
        if ( stagingRepository.isManaged() )
        {
            final StagingWorkflowV2Service stagingService = getStagingWorkflowService();
            try
            {
                if ( !skipClose )
                {
                    if ( successful )
                    {
                        getLog().info(
                            " * Closing staging repository with ID \"" + stagingRepository.getRepositoryId() + "\"." );
                        stagingService.finishStaging( stagingRepository.getProfile(),
                            stagingRepository.getRepositoryId(), getDescriptionWithDefaultsForAction( "Closed" ) );
                    }
                    else
                    {
                        if ( !keepStagingRepositoryOnFailure )
                        {
                            getLog().warn(
                                "Dropping failed staging repository with ID \"" + stagingRepository.getRepositoryId()
                                    + "\" (due to unsuccesful upload)." );
                            stagingService.dropStagingRepositories( getDefaultDescriptionForAction( "Dropped" )
                                + " (due to unsuccesful upload).", stagingRepository.getRepositoryId() );
                        }
                        else
                        {
                            getLog().warn(
                                "Not dropping failed staging repository with ID \""
                                    + stagingRepository.getRepositoryId() + "\" (due to unsuccesful upload)." );
                        }
                    }
                }
                else
                {
                    getLog().info(
                        " * Not closing staging repository with ID \"" + stagingRepository.getRepositoryId() + "\"." );
                }
                getLog().info( "Finished staging against Nexus " + ( successful ? "with success." : "with failure." ) );
            }
            catch ( UniformInterfaceException e )
            {
                getLog().error(
                    "Error while trying to close staging repository with ID \"" + stagingRepository.getRepositoryId()
                        + "\"." );
                throw new ArtifactDeploymentException(
                    "Error after upload while managing staging repository! Staging repository in question is "
                        + stagingRepository.getRepositoryId(), e );
            }
        }

        if ( successful )
        {
            final String stagingRepositoryUrl =
                concat( getNexusUrl(), "/content/repositories", stagingRepository.getRepositoryId() );

            final Properties stagingProperties = new Properties();
            // the staging repository ID where the staging went
            stagingProperties.put( STAGING_REPOSITORY_ID, stagingRepository.getRepositoryId() );
            // the staging repository's profile ID where the staging went
            stagingProperties.put( STAGING_REPOSITORY_PROFILE_ID, stagingRepository.getProfile().getId() );
            // the staging repository URL (if closed! see below)
            stagingProperties.put( STAGING_REPOSITORY_URL, stagingRepositoryUrl );
            // targeted repo mode or not (are we closing it or someone else? If false, the URL above might not yet
            // exists if not yet closed....
            stagingProperties.put( STAGING_REPOSITORY_MANAGED, String.valueOf( stagingRepository.isManaged() ) );

            final File stagingPropertiesFile =
                new File( getStagingDirectoryRoot(), stagingRepository.getProfile().getId()
                    + STAGING_REPOSITORY_PROPERTY_FILE_NAME_SUFFIX );
            FileOutputStream fout = null;
            try
            {
                fout = new FileOutputStream( stagingPropertiesFile );
                stagingProperties.store( fout, "Generated by " + getPluginGav() );
                fout.flush();
            }
            catch ( IOException e )
            {
                throw new ArtifactDeploymentException( "Error saving staging repository properties to file "
                    + stagingPropertiesFile, e );
            }
            finally
            {
                IOUtil.close( fout );
            }
        }
        else
        {
            getLog().error( "Remote staging was unsuccesful!" );
        }
    }

    // ==

    protected String concat( String... paths )
    {
        StringBuilder result = new StringBuilder();

        for ( String path : paths )
        {
            while ( path.endsWith( "/" ) )
            {
                path = path.substring( 0, path.length() - 1 );
            }
            if ( result.length() > 0 && !path.startsWith( "/" ) )
            {
                result.append( "/" );
            }
            result.append( path );
        }

        return result.toString();
    }

    protected ArtifactRepository getStagingRepositoryFor( final File stagingDirectory )
        throws MojoExecutionException
    {
        if ( stagingDirectory != null )
        {
            if ( stagingDirectory.exists() && ( !stagingDirectory.canWrite() || !stagingDirectory.isDirectory() ) )
            {
                // it exists but is not writable or is not a directory
                throw new MojoExecutionException(
                    "Staging failed: staging directory points to an existing file but is not a directory or is not writable!" );
            }
            else if ( !stagingDirectory.exists() )
            {
                // it does not exists, create it
                stagingDirectory.mkdirs();
            }

            try
            {
                final String id = "nexus";
                final String url = stagingDirectory.getCanonicalFile().toURI().toURL().toExternalForm();

                return repositoryFactory.createDeploymentArtifactRepository( id, url, defaultArtifactRepositoryLayout,
                    true );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException(
                    "Staging failed: staging directory path cannot be converted to canonical one!", e );
            }
        }
        else
        {
            throw new MojoExecutionException( "Staging failed: staging directory is null!" );
        }
    }
}