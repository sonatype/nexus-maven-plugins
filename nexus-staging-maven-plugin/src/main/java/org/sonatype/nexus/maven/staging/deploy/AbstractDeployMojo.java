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
package org.sonatype.nexus.maven.staging.deploy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.maven.mojo.logback.LogbackUtils;
import org.sonatype.nexus.client.core.NexusErrorMessageException;
import org.sonatype.nexus.client.core.NexusStatus;
import org.sonatype.nexus.maven.staging.AbstractStagingMojo;
import org.sonatype.nexus.maven.staging.ErrorDumper;

import ch.qos.logback.classic.Level;

import com.sonatype.nexus.staging.client.Profile;
import com.sonatype.nexus.staging.client.ProfileMatchingParameters;
import com.sonatype.nexus.staging.client.StagingRuleFailures;
import com.sonatype.nexus.staging.client.StagingRuleFailuresException;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

//import com.sun.jersey.api.client.UniformInterfaceException;

/**
 * Abstract class for deploy related mojos.
 * 
 * @author cstamas
 * @since 1.0
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
    private ArtifactInstaller installer;

    /**
     * @component
     */
    private ArtifactDeployer deployer;

    /**
     * @component
     */
    private ArtifactRepositoryFactory artifactRepositoryFactory;

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
     * Default layout.
     * 
     * @component
     */
    private ArtifactRepositoryLayout artifactRepositoryLayout;

    /**
     * Map that contains the layouts.
     * 
     * @component role="org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout" hint="default"
     */
    private ArtifactRepositoryLayout defaultArtifactRepositoryLayout;

    /**
     * Artifact handlers.
     * 
     * @component role="org.apache.maven.artifact.handler.ArtifactHandler"
     */
    private Map<String, ArtifactHandler> artifactHandlers;

    /**
     * Zapper component
     * 
     * @component role="org.sonatype.nexus.maven.staging.deploy.Zapper"
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

    public String getStagingProfileId()
    {
        return stagingProfileId;
    }

    public void setStagingProfileId( String stagingProfileId )
    {
        this.stagingProfileId = stagingProfileId;
    }

    public String getStagingRepositoryId()
    {
        return stagingRepositoryId;
    }

    public void setStagingRepositoryId( String stagingRepositoryId )
    {
        this.stagingRepositoryId = stagingRepositoryId;
    }

    protected ArtifactRepository getLocalRepository()
    {
        return localRepository;
    }

    protected ArtifactFactory getArtifactFactory()
    {
        return artifactFactory;
    }

    /**
     * Performs an "install" (not to be confused with "install into local repository!) into the staging repository. It
     * will retain snapshot versions, and no metadata is created at all. In short: performs a simple file copy.
     * 
     * @param source
     * @param artifact
     * @param stagingRepository
     * @param stagingDirectory
     * @throws ArtifactDeploymentException
     * @throws MojoExecutionException
     */
    protected void install( final File source, final Artifact artifact, final ArtifactRepository stagingRepository,
                            final File stagingDirectory )
        throws ArtifactInstallationException, MojoExecutionException
    {
        final String path = stagingRepository.pathOf( artifact );
        try
        {
            installer.install( source, artifact, stagingRepository );

            // append the index file
            final FileOutputStream fos = new FileOutputStream( new File( stagingDirectory, ".index" ), true );
            final OutputStreamWriter osw = new OutputStreamWriter( fos, "ISO-8859-1" );
            final PrintWriter pw = new PrintWriter( osw );
            String pomFileName = null;
            for ( ArtifactMetadata artifactMetadata : artifact.getMetadataList() )
            {
                if ( artifactMetadata instanceof ProjectArtifactMetadata )
                {
                    pomFileName = ( (ProjectArtifactMetadata) artifactMetadata ).getLocalFilename( stagingRepository );
                }
            }
            pw.println( String.format( "%s=%s:%s:%s:%s:%s:%s", path, artifact.getGroupId(), artifact.getArtifactId(),
                artifact.getVersion(),
                StringUtils.isBlank( artifact.getClassifier() ) ? "n/a" : artifact.getClassifier(), artifact.getType(),
                StringUtils.isBlank( pomFileName ) ? "n/a" : pomFileName ) );
            pw.flush();
            pw.close();
        }
        catch ( IOException e )
        {
            throw new ArtifactInstallationException( "Cannot locally stage and maintain the index file!", e );
        }
    }

    /**
     * Invoked Maven's ArtifactDeployer with provided parameters.
     * 
     * @param source
     * @param artifact
     * @param localRepository
     * @param remoteRepository
     * @throws ArtifactDeploymentException
     * @throws MojoExecutionException
     */
    protected void deploy( final File source, final Artifact artifact, final ArtifactRepository remoteRepository )
        throws ArtifactDeploymentException, MojoExecutionException
    {
        deployer.deploy( source, artifact, remoteRepository, getLocalRepository() );
    }

    /**
     * Stages remotely the locally staged directory(ies).
     * 
     * @throws ArtifactDeploymentException if an error occurred uploading the artifacts
     * @throws MojoExecutionException if some staging related problem, like wrong profile ID, etc. happened
     */
    protected void stageRemotely()
        throws ArtifactDeploymentException, MojoExecutionException
    {
        getLog().info( "Staging remotely..." );
        final File stageRoot = getStagingDirectoryRoot();
        final File[] localStageRepositories = stageRoot.listFiles();
        if ( localStageRepositories == null )
        {
            getLog().info( "We have nothing locally staged, bailing out." );
            return;
        }
        final List<StagingRepository> zappedStagingRepositories = new ArrayList<StagingRepository>();
        for ( File profileDirectory : localStageRepositories )
        {
            if ( !profileDirectory.isDirectory() )
            {
                continue;
            }

            final String profileId = profileDirectory.getName();
            if ( DEFERRED_UPLOAD.equals( profileId ) )
            {
                // we do direct upload
                getLog().info( "Bulk deploying locally gathered snapshots directory: " );
                try
                {
                    // prepare the local staging directory
                    // we have normal deploy
                    getLog().info(
                        " * Bulk deploying locally gathered snapshot artifacts to URL "
                            + getDeploymentRepository().getUrl() );
                    deployUp( getStagingDirectory( profileId ), getDeploymentRepository() );
                    getLog().info( " * Bulk deploy of locally gathered snapshot artifacts finished." );
                }
                catch ( IOException e )
                {
                    getLog().error( "Upload of locally staged directory finished with a failure." );
                    throw new ArtifactDeploymentException( "Remote staging failed: " + e.getMessage(), e );
                }
            }
            else
            {
                // we do staging
                getLog().info( "Remote staging locally staged directory: " + profileId );

                if ( getNexusClient() == null )
                {
                    createTransport( getNexusUrl() );
                    createNexusClient( getServer(), getProxy() );
                }

                getLog().info( " * Connecting to Nexus on URL " + getNexusUrl() );
                final NexusStatus nexusStatus = getNexusClient().getNexusStatus();
                getLog().info(
                    String.format( " * Remote Nexus reported itself as version %s and edition \"%s\"",
                        nexusStatus.getVersion(), nexusStatus.getEditionLong() ) );

                final Profile stagingProfile = selectStagingProfileById( profileId );
                final StagingRepository stagingRepository = beforeUpload( stagingProfile );
                zappedStagingRepositories.add( stagingRepository );
                try
                {
                    final String deployUrl = calculateUploadUrl( stagingRepository );
                    getLog().info( " * Uploading locally staged artifacts to profile " + stagingProfile.getName() );
                    deployUp( getStagingDirectory( profileId ),
                        getNexusStagingRepositoryFor( stagingRepository, deployUrl ) );
                    getLog().info( " * Upload of locally staged artifacts finished." );
                    afterUpload( stagingRepository, skipStagingRepositoryClose );
                }
                catch ( StagingRuleFailuresException e )
                {
                    afterUploadFailure( zappedStagingRepositories, e );
                    getLog().error( "Remote staging finished with a failure." );
                    throw new ArtifactDeploymentException( "Remote staging failed: " + e.getMessage(), e );
                }
                catch ( InvalidRepositoryException e )
                {
                    afterUploadFailure( zappedStagingRepositories, e );
                    getLog().error( "Remote staging finished with a failure." );
                    throw new ArtifactDeploymentException( "Remote staging failed: " + e.getMessage(), e );
                }
                catch ( IOException e )
                {
                    afterUploadFailure( zappedStagingRepositories, e );
                    getLog().error( "Remote staging finished with a failure." );
                    throw new ArtifactDeploymentException( "Remote staging failed: " + e.getMessage(), e );
                }
            }
        }
        getLog().info( "Remote staging finished with success." );
    }

    /**
     * Stages remotely the locally staged repository.
     * 
     * @throws ArtifactDeploymentException if an error occurred uploading the artifacts
     * @throws MojoExecutionException if some staging related problem, like wrong profile ID, etc. happened
     */
    protected void stageRepositoryRemotely( final File repositoryDirectory )
        throws ArtifactDeploymentException, MojoExecutionException
    {
        getLog().info( "Staging remotely locally staged repository..." );

        createTransport( getNexusUrl() );
        createNexusClient( getServer(), getProxy() );

        getLog().info( " * Connecting to Nexus on URL " + getNexusUrl() );
        final NexusStatus nexusStatus = getNexusClient().getNexusStatus();
        getLog().info(
            String.format( " * Remote Nexus reported itself as version %s and edition \"%s\"",
                nexusStatus.getVersion(), nexusStatus.getEditionLong() ) );

        final String profileId = getStagingProfileId();
        final Profile stagingProfile = selectStagingProfileById( profileId );
        final StagingRepository stagingRepository = beforeUpload( stagingProfile );
        try
        {
            final String deployUrl = calculateUploadUrl( stagingRepository );
            getLog().info( " * Uploading locally staged artifacts to profile " + stagingProfile.getName() );
            zapUp( repositoryDirectory, deployUrl );
            getLog().info( " * Upload of locally staged artifacts finished." );
            afterUpload( stagingRepository, skipStagingRepositoryClose );
        }
        catch ( StagingRuleFailuresException e )
        {
            afterUploadFailure( Collections.singletonList( stagingRepository ), e );
            getLog().error( "Remote staging finished with a failure." );
            throw new ArtifactDeploymentException( "Remote staging failed: " + e.getMessage(), e );
        }
        catch ( IOException e )
        {
            afterUploadFailure( Collections.singletonList( stagingRepository ), e );
            getLog().error( "Remote staging finished with a failure." );
            throw new ArtifactDeploymentException( "Remote staging failed: " + e.getMessage(), e );
        }
        getLog().info( "Remote staging finished with success." );
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
    }

    // G:A:V:C:P:PomFileName
    private final Pattern indexProps = Pattern.compile( "(.*):(.*):(.*):(.*):(.*):(.*)" );

    /**
     * Performs a bulk upload using {@link ArtifactDeployer}.
     * 
     * @param sourceDirectory
     * @param remoteRepository
     * @throws ArtifactDeploymentException
     * @throws IOException
     */
    protected void deployUp( final File sourceDirectory, final ArtifactRepository remoteRepository )
        throws ArtifactDeploymentException, IOException
    {
        // I'd need Aether RepoSystem and create one huge DeployRequest will _all_ artifacts (would be FAST as it would
        // go parallel), but we need to work in Maven2 too, so old compat and slow method remains: deploy one by one...
        final FileInputStream fis = new FileInputStream( new File( sourceDirectory, ".index" ) );
        final Properties index = new Properties();
        try
        {
            index.load( fis );
        }
        finally
        {
            IOUtil.close( fis );
        }

        for ( String includedFilePath : index.stringPropertyNames() )
        {
            final File includedFile = new File( sourceDirectory, includedFilePath );
            final String includedFileProps = index.getProperty( includedFilePath );
            final Matcher matcher = indexProps.matcher( includedFileProps );
            if ( !matcher.matches() )
            {
                throw new ArtifactDeploymentException( "Internal error! Line \"" + includedFileProps
                    + "\" does not match pattern \"" + indexProps.toString() + "\"?" );
            }

            final String groupId = matcher.group( 1 );
            final String artifactId = matcher.group( 2 );
            final String version = matcher.group( 3 );
            final String classifier = "n/a".equals( matcher.group( 4 ) ) ? null : matcher.group( 4 );
            final String packaging = matcher.group( 5 );
            final String pomFileName = "n/a".equals( matcher.group( 6 ) ) ? null : matcher.group( 6 );

            ArtifactHandler artifactHandler = artifactHandlers.get( "default" );
            if ( artifactHandlers.containsKey( packaging ) )
            {
                artifactHandler = artifactHandlers.get( packaging );
            }
            final DefaultArtifact artifact =
                new DefaultArtifact( groupId, artifactId, VersionRange.createFromVersion( version ), null, packaging,
                    classifier, artifactHandler );
            if ( pomFileName != null )
            {
                final File pomFile = new File( includedFile.getParentFile(), pomFileName );
                final ProjectArtifactMetadata pom = new ProjectArtifactMetadata( artifact, pomFile );
                artifact.addMetadata( pom );
            }
            deployer.deploy( includedFile, artifact, remoteRepository, getLocalRepository() );
        }
    }

    // ==

    protected static final String DIRECT_UPLOAD = "direct";

    protected static final String DEFERRED_UPLOAD = "deferred";

    /**
     * Selects a staging profile based on informations given (configured) to Mojo.
     * 
     * @param the workflow type we do
     * @return the profileID selected or a special {@link AbstractStagingMojo#DIRECT_UPLOAD} constant.
     * @throws MojoExecutionException
     */
    protected String selectStagingProfile( final WorkflowType type )
        throws MojoExecutionException
    {
        if ( WorkflowType.DIRECT_DEPLOY.equals( type ) )
        {
            createTransport( getDeploymentRepository().getUrl() );
            return DIRECT_UPLOAD;
        }
        else if ( WorkflowType.DEFERRED_DEPLOY.equals( type ) )
        {
            createTransport( getDeploymentRepository().getUrl() );
            return DEFERRED_UPLOAD;
        }
        else if ( WorkflowType.STAGING_DEPLOY.equals( type ) && getNexusUrl() != null )
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
            catch ( NexusErrorMessageException e )
            {
                ErrorDumper.dumpErrors( getLog(), e );
                // fail the build
                throw new MojoExecutionException( "Could not perform action: Nexus ErrorResponse received!", e );
            }
        }
        else
        {
            throw new MojoExecutionException(
                String.format( "No deploy URL set, nor Nexus BaseURL given or wrong mode (type=%s, nexusUrl=%s)!",
                    type, getNexusUrl() ) );
        }
    }

    /**
     * Returns the Profile instance selected by ID on remote Nexus.
     * 
     * @param stagingProfileId
     * @return the {@link Profile} for given {@code stagingProfileId}.
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
        catch ( NexusErrorMessageException e )
        {
            ErrorDumper.dumpErrors( getLog(), e );
            // fail the build
            throw new MojoExecutionException( "Could not perform action: Nexus ErrorResponse received!", e );
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
     * @throws MojoExecutionException
     * @throws StagingRuleFailuresException
     */
    protected void afterUpload( final StagingRepository stagingRepository, final boolean skipClose )
        throws MojoExecutionException, StagingRuleFailuresException
    {
        // if upload successful. write out the properties file
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
            throw new MojoExecutionException( "Error saving staging repository properties to file "
                + stagingPropertiesFile, e );
        }
        finally
        {
            IOUtil.close( fout );
        }

        // if repository is managed, then manage it
        if ( stagingRepository.isManaged() )
        {
            final StagingWorkflowV2Service stagingService = getStagingWorkflowService();
            try
            {
                if ( !skipClose )
                {
                    try
                    {
                        getLog().info(
                            " * Closing staging repository with ID \"" + stagingRepository.getRepositoryId() + "\"." );
                        stagingService.finishStaging( stagingRepository.getProfile(),
                            stagingRepository.getRepositoryId(), getDescriptionWithDefaultsForAction( "Closed" ) );
                    }
                    catch ( StagingRuleFailuresException e )
                    {
                        getLog().error(
                            "Rule failure while trying to close staging repository with ID \""
                                + stagingRepository.getRepositoryId() + "\"." );
                        // report staging repository failures
                        ErrorDumper.dumpErrors( getLog(), e );
                        // rethrow
                        throw e;
                    }
                }
                else
                {
                    getLog().info(
                        " * Not closing staging repository with ID \"" + stagingRepository.getRepositoryId() + "\"." );
                }
            }
            catch ( NexusErrorMessageException e )
            {
                getLog().error(
                    "Error while trying to close staging repository with ID \"" + stagingRepository.getRepositoryId()
                        + "\"." );
                ErrorDumper.dumpErrors( getLog(), e );
                // fail the build
                throw new MojoExecutionException( "Could not perform action against repository \""
                    + stagingRepository.getRepositoryId() + "\": Nexus ErrorResponse received!", e );
            }
        }
    }

    /**
     * Performs various cleanup after staging repository failure.
     * 
     * @param stagingRepositories
     * @param problem
     * @throws MojoExecutionException
     */
    protected void afterUploadFailure( final List<StagingRepository> stagingRepositories, final Throwable problem )
        throws MojoExecutionException
    {
        final String msg;
        final boolean keep;

        // rule failed, undo all what we did on server side and client side: drop all the products of this reactor
        if ( problem instanceof StagingRuleFailuresException )
        {
            final StagingRuleFailuresException srfe = (StagingRuleFailuresException) problem;
            final List<String> failedRepositories = new ArrayList<String>();
            for ( StagingRuleFailures failures : srfe.getFailures() )
            {
                failedRepositories.add( failures.getRepositoryName() + "(id=" + failures.getRepositoryId() + ")" );
            }
            msg = "Rule failure during close of staging repositories: " + failedRepositories;
            keep = isKeepStagingRepositoryOnCloseRuleFailure();
        }
        else if ( problem instanceof IOException )
        {
            msg = "IO failure during deploy";
            keep = keepStagingRepositoryOnFailure;
        }
        else if ( problem instanceof InvalidRepositoryException )
        {
            msg = "Internal error: " + problem.getMessage();
            keep = keepStagingRepositoryOnFailure;
        }
        else
        {
            return;
        }

        getLog().error( "Cleaning up local stage directory after a " + msg );
        // delete properties (as they are getting created when remotely staged)
        final File stageRoot = getStagingDirectoryRoot();
        final File[] localStageRepositories = stageRoot.listFiles();
        if ( localStageRepositories != null )
        {
            for ( File file : localStageRepositories )
            {
                if ( file.isFile() && file.getName().endsWith( STAGING_REPOSITORY_PROPERTY_FILE_NAME_SUFFIX ) )
                {
                    getLog().error( " * Deleting context " + file.getName() );
                    file.delete();
                }
            }
        }

        getLog().error( "Cleaning up remote stage repositories after a " + msg );
        // drop all created staging repositories
        final StagingWorkflowV2Service stagingService = getStagingWorkflowService();
        for ( StagingRepository stagingRepository : stagingRepositories )
        {
            if ( stagingRepository.isManaged() )
            {
                if ( !keep )
                {
                    getLog().error(
                        " * Dropping failed staging repository with ID \"" + stagingRepository.getRepositoryId()
                            + "\" (" + msg + ")." );
                    stagingService.dropStagingRepositories( getDefaultDescriptionForAction( "Dropped" ) + " (" + msg
                        + ").", stagingRepository.getRepositoryId() );
                }
                else
                {
                    getLog().error(
                        " * Not dropping failed staging repository with ID \"" + stagingRepository.getRepositoryId()
                            + "\" (" + msg + ")." );
                }
            }
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

    protected ArtifactRepository getTargetRepository( final WorkflowType workflowType, final File stagingDirectory )
        throws MojoExecutionException
    {
        if ( WorkflowType.DIRECT_DEPLOY.equals( workflowType ) )
        {
            return getDeploymentRepository();
        }
        else
        {
            return getStagingRepositoryFor( stagingDirectory );
        }
    }

    private ArtifactRepository getStagingRepositoryFor( final File stagingDirectory )
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

    private ArtifactRepository getNexusStagingRepositoryFor( final StagingRepository stagingRepository, final String url )
        throws InvalidRepositoryException
    {
        // final Repository nexusStagingRepository = new Repository();
        // nexusStagingRepository.setId( stagingRepository.getRepositoryId() );
        // nexusStagingRepository.setName( stagingRepository.getRepositoryId() );
        // // nexusStagingRepository.setLayout( "default" );
        // nexusStagingRepository.setUrl( url );
        // final ArtifactRepository result = repositorySystem.buildArtifactRepository( nexusStagingRepository );
        // repositorySystem.injectProxy( getMavenSession().getRepositorySession(), Arrays.asList( result ) );
        // repositorySystem.injectAuthentication( getMavenSession().getRepositorySession(), Arrays.asList( result ) );

        final ArtifactRepository result =
            artifactRepositoryFactory.createArtifactRepository( getServerId(), url, artifactRepositoryLayout, null,
                null );
        return result;
    }

    // ==
    // Code copy pasted and slightly modified (removal of altDeploymentRepository) from
    // http://svn.apache.org/viewvc/maven/plugins/tags/maven-deploy-plugin-2.7 @ 1160178

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    private ArtifactRepository getDeploymentRepository()
        throws MojoExecutionException
    {
        final ArtifactRepository repo = project.getDistributionManagementArtifactRepository();
        if ( repo == null )
        {
            String msg =
                "Deployment failed: repository element was not specified in the POM inside"
                    + " distributionManagement element or in -DaltDeploymentRepository=id::layout::url parameter";

            throw new MojoExecutionException( msg );
        }
        return repo;
    }
}