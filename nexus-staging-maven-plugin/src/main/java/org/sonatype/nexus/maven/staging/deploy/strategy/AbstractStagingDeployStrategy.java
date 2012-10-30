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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.maven.mojo.logback.LogbackUtils;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.core.NexusErrorMessageException;
import org.sonatype.nexus.client.core.NexusStatus;
import org.sonatype.nexus.maven.staging.AbstractStagingMojo;
import org.sonatype.nexus.maven.staging.ErrorDumper;
import org.sonatype.nexus.maven.staging.deploy.StagingRepository;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import ch.qos.logback.classic.Level;

import com.google.common.base.Preconditions;
import com.sonatype.nexus.staging.client.Profile;
import com.sonatype.nexus.staging.client.ProfileMatchingParameters;
import com.sonatype.nexus.staging.client.StagingRuleFailures;
import com.sonatype.nexus.staging.client.StagingRuleFailuresException;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

public abstract class AbstractStagingDeployStrategy
    extends AbstractDeployStrategy
{
    @Requirement
    private ArtifactInstaller artifactInstaller;

    @Requirement
    private ArtifactDeployer artifactDeployer;

    @Requirement
    private ArtifactRepositoryFactory artifactRepositoryFactory;

    @Requirement
    private ArtifactRepositoryLayout artifactRepositoryLayout;

    @Requirement
    private SecDispatcher secDispatcher;

    private Remoting remoting;

    protected void initRemoting( final MavenSession mavenSession, final Parameters parameters )
        throws MojoExecutionException
    {
        if ( getLogger().isDebugEnabled() )
        {
            LogbackUtils.syncLogLevelWithLevel( Level.DEBUG );
        }
        else
        {
            LogbackUtils.syncLogLevelWithLevel( Level.WARN );
        }
        remoting = new RemotingImpl( mavenSession, parameters, secDispatcher );
        if ( remoting.getServer() != null )
        {
            getLogger().info(
                "Using server credentials with ID=\"" + remoting.getServer().getId() + "\" from Maven settings." );
        }
        if ( remoting.getProxy() != null )
        {
            getLogger().info(
                "Using " + remoting.getProxy().getProtocol().toUpperCase() + " Proxy with ID=\""
                    + remoting.getProxy().getId() + "\" from Maven settings." );
        }
    }

    protected synchronized Remoting getRemoting()
    {
        return remoting;
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
            artifactInstaller.install( source, artifact, stagingRepository );

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
            pw.println( String.format( "%s=%s:%s:%s:%s:%s:%s:%s", path, artifact.getGroupId(),
                artifact.getArtifactId(), artifact.getVersion(),
                StringUtils.isBlank( artifact.getClassifier() ) ? "n/a" : artifact.getClassifier(), artifact.getType(),
                artifact.getArtifactHandler().getExtension(), StringUtils.isBlank( pomFileName ) ? "n/a" : pomFileName ) );
            pw.flush();
            pw.close();
        }
        catch ( IOException e )
        {
            throw new ArtifactInstallationException( "Cannot locally stage and maintain the index file!", e );
        }
    }

    // G:A:V:C:P:Ext:PomFileName
    private final Pattern indexProps = Pattern.compile( "(.*):(.*):(.*):(.*):(.*):(.*):(.*)" );

    protected void deployUp( final MavenSession mavenSession, final File sourceDirectory,
                             final ArtifactRepository remoteRepository )
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
            final String extension = matcher.group( 6 );
            final String pomFileName = "n/a".equals( matcher.group( 7 ) ) ? null : matcher.group( 7 );

            // just a synthetic one, to properly set extension
            final FakeArtifactHandler artifactHandler = new FakeArtifactHandler( packaging, extension );

            final DefaultArtifact artifact =
                new DefaultArtifact( groupId, artifactId, VersionRange.createFromVersion( version ), null, packaging,
                    classifier, artifactHandler );
            if ( pomFileName != null )
            {
                final File pomFile = new File( includedFile.getParentFile(), pomFileName );
                final ProjectArtifactMetadata pom = new ProjectArtifactMetadata( artifact, pomFile );
                artifact.addMetadata( pom );
            }
            artifactDeployer.deploy( includedFile, artifact, remoteRepository, mavenSession.getLocalRepository() );
        }
    }

    // ==

    /**
     * Just a "fake" synthetic handler, to overcome Maven2/3 differences (no extension setter in M2 but there is in M3
     * on {@link DefaultArtifactHandler}.
     */
    public static class FakeArtifactHandler
        extends DefaultArtifactHandler
    {
        private final String extension;

        /**
         * Constructor.
         * 
         * @param type
         * @param extension
         */
        public FakeArtifactHandler( final String type, final String extension )
        {
            super( Preconditions.checkNotNull( type ) );
            this.extension = Preconditions.checkNotNull( extension );
        }

        @Override
        public String getExtension()
        {
            return extension;
        }
    }

    // ==

    /**
     * Selects a staging profile based on informations given (configured) to Mojo.
     * 
     * @param the workflow type we do
     * @return the profileID selected or a special {@link AbstractStagingMojo#DIRECT_UPLOAD} constant.
     * @throws MojoExecutionException
     */
    protected String selectStagingProfile( final Parameters parameters, final Artifact artifact )
        throws MojoExecutionException
    {
        try
        {
            final NexusClient nexusClient = getRemoting().getNexusClient();
            getLogger().info( "Preparing staging against Nexus on URL " + nexusClient.getConnectionInfo().getBaseUrl() );
            final NexusStatus nexusStatus = nexusClient.getNexusStatus();
            getLogger().info(
                String.format( " * Remote Nexus reported itself as version %s and edition \"%s\"",
                    nexusStatus.getVersion(), nexusStatus.getEditionLong() ) );
            final StagingWorkflowV2Service stagingService = remoting.getStagingWorkflowV2Service();

            Profile stagingProfile;
            // if profile is not "targeted", perform a match and save the result
            if ( StringUtils.isBlank( parameters.getStagingProfileId() ) )
            {
                final ProfileMatchingParameters params =
                    new ProfileMatchingParameters( artifact.getGroupId(), artifact.getArtifactId(),
                        artifact.getVersion() );
                stagingProfile = stagingService.matchProfile( params );
                getLogger().info( " * Using staging profile ID \"" + stagingProfile.getId() + "\" (matched by Nexus)." );
            }
            else
            {
                stagingProfile = stagingService.selectProfile( parameters.getStagingProfileId() );
                getLogger().info(
                    " * Using staging profile ID \"" + stagingProfile.getId() + "\" (configured by user)." );
            }
            return stagingProfile.getId();
        }
        catch ( NexusErrorMessageException e )
        {
            ErrorDumper.dumpErrors( getLogger(), e );
            // fail the build
            throw new MojoExecutionException( "Could not perform action: Nexus ErrorResponse received!", e );
        }
    }

    protected StagingRepository beforeUpload( final Parameters parameters, final Profile stagingProfile )
        throws MojoExecutionException
    {
        try
        {
            final StagingWorkflowV2Service stagingService = getRemoting().getStagingWorkflowV2Service();
            if ( StringUtils.isBlank( parameters.getStagingRepositoryId() ) )
            {
                String createdStagingRepositoryId =
                    stagingService.startStaging( stagingProfile,
                        parameters.getDefaultedUserDescriptionOfAction( "Started" ), parameters.getTags() );
                // store the one just created for us, as it means we need to "babysit" it (close or drop, depending
                // on outcome)
                if ( parameters.getTags() != null && !parameters.getTags().isEmpty() )
                {
                    getLogger().info(
                        " * Created staging repository with ID \"" + createdStagingRepositoryId + "\", applied tags: "
                            + parameters.getTags() );
                }
                else
                {
                    getLogger().info( " * Created staging repository with ID \"" + createdStagingRepositoryId + "\"." );
                }
                final String url = stagingService.startedRepositoryBaseUrl( stagingProfile, createdStagingRepositoryId );
                return new StagingRepository( stagingProfile, createdStagingRepositoryId, url, true );
            }
            else
            {
                getLogger().info(
                    " * Using non-managed staging repository with ID \"" + parameters.getStagingRepositoryId()
                        + "\" (we are NOT managing it)." ); // we will not close it! This might be created by some
                                                            // other automated component
                final String url =
                    stagingService.startedRepositoryBaseUrl( stagingProfile, parameters.getStagingRepositoryId() );
                return new StagingRepository( stagingProfile, parameters.getStagingRepositoryId(), url, false );
            }
        }
        catch ( NexusErrorMessageException e )
        {
            ErrorDumper.dumpErrors( getLogger(), e );
            // fail the build
            throw new MojoExecutionException( "Could not perform action: Nexus ErrorResponse received!", e );
        }
    }

    public static final String STAGING_REPOSITORY_PROPERTY_FILE_NAME_SUFFIX = ".properties";

    public static final String STAGING_REPOSITORY_ID = "stagingRepository.id";

    public static final String STAGING_REPOSITORY_PROFILE_ID = "stagingRepository.profileId";

    public static final String STAGING_REPOSITORY_URL = "stagingRepository.url";

    public static final String STAGING_REPOSITORY_MANAGED = "stagingRepository.managed";

    protected void afterUpload( final Parameters parameters, final StagingRepository stagingRepository )
        throws MojoExecutionException, StagingRuleFailuresException
    {
        // if upload successful. write out the properties file
        final String stagingRepositoryUrl =
            concat( getRemoting().getNexusClient().getConnectionInfo().getBaseUrl().toString(),
                "/content/repositories", stagingRepository.getRepositoryId() );

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
            new File( parameters.getStagingDirectoryRoot(), stagingRepository.getProfile().getId()
                + STAGING_REPOSITORY_PROPERTY_FILE_NAME_SUFFIX );

        // this below is the case with DeployRepositoryMojo, where we have no folder created
        // as we remotely staged something completely different
        if ( !stagingPropertiesFile.getParentFile().isDirectory() )
        {
            stagingPropertiesFile.getParentFile().mkdirs();
        }

        FileOutputStream fout = null;
        try
        {
            fout = new FileOutputStream( stagingPropertiesFile );
            stagingProperties.store( fout, "Generated by " + parameters.getPluginGav() );
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
            final StagingWorkflowV2Service stagingService = getRemoting().getStagingWorkflowV2Service();
            try
            {
                if ( !parameters.isSkipStagingRepositoryClose() )
                {
                    try
                    {
                        getLogger().info(
                            " * Closing staging repository with ID \"" + stagingRepository.getRepositoryId() + "\"." );
                        stagingService.finishStaging( stagingRepository.getProfile(),
                            stagingRepository.getRepositoryId(),
                            parameters.getDefaultedUserDescriptionOfAction( "Closed" ) );
                    }
                    catch ( StagingRuleFailuresException e )
                    {
                        getLogger().error(
                            "Rule failure while trying to close staging repository with ID \""
                                + stagingRepository.getRepositoryId() + "\"." );
                        // report staging repository failures
                        ErrorDumper.dumpErrors( getLogger(), e );
                        // rethrow
                        throw e;
                    }
                }
                else
                {
                    getLogger().info(
                        " * Not closing staging repository with ID \"" + stagingRepository.getRepositoryId() + "\"." );
                }
            }
            catch ( NexusErrorMessageException e )
            {
                getLogger().error(
                    "Error while trying to close staging repository with ID \"" + stagingRepository.getRepositoryId()
                        + "\"." );
                ErrorDumper.dumpErrors( getLogger(), e );
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
    protected void afterUploadFailure( final Parameters parameters, final List<StagingRepository> stagingRepositories,
                                       final Throwable problem )
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
            keep = parameters.isKeepStagingRepositoryOnCloseRuleFailure();
        }
        else if ( problem instanceof IOException )
        {
            msg = "IO failure during deploy";
            keep = parameters.isKeepStagingRepositoryOnFailure();
        }
        else if ( problem instanceof InvalidRepositoryException )
        {
            msg = "Internal error: " + problem.getMessage();
            keep = parameters.isKeepStagingRepositoryOnFailure();
        }
        else
        {
            return;
        }

        getLogger().error( "Cleaning up local stage directory after a " + msg );
        // delete properties (as they are getting created when remotely staged)
        final File stageRoot = parameters.getStagingDirectoryRoot();
        final File[] localStageRepositories = stageRoot.listFiles();
        if ( localStageRepositories != null )
        {
            for ( File file : localStageRepositories )
            {
                if ( file.isFile() && file.getName().endsWith( STAGING_REPOSITORY_PROPERTY_FILE_NAME_SUFFIX ) )
                {
                    getLogger().error( " * Deleting context " + file.getName() );
                    file.delete();
                }
            }
        }

        getLogger().error( "Cleaning up remote stage repositories after a " + msg );
        // drop all created staging repositories
        final StagingWorkflowV2Service stagingService = getRemoting().getStagingWorkflowV2Service();
        for ( StagingRepository stagingRepository : stagingRepositories )
        {
            if ( stagingRepository.isManaged() )
            {
                if ( !keep )
                {
                    getLogger().error(
                        " * Dropping failed staging repository with ID \"" + stagingRepository.getRepositoryId()
                            + "\" (" + msg + ")." );
                    stagingService.dropStagingRepositories( parameters.getDefaultedUserDescriptionOfAction( "Dropped" )
                        + " (" + msg + ").", stagingRepository.getRepositoryId() );
                }
                else
                {
                    getLogger().error(
                        " * Not dropping failed staging repository with ID \"" + stagingRepository.getRepositoryId()
                            + "\" (" + msg + ")." );
                }
            }
        }
    }

    protected String concat( String... paths )
    {
        final StringBuilder result = new StringBuilder();
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

    protected File getStagingDirectory( final File stagingDirectoryRoot, final String profileId )
        throws MojoExecutionException
    {
        final File root = stagingDirectoryRoot;
        if ( StringUtils.isBlank( profileId ) )
        {
            throw new MojoExecutionException(
                "Internal bug: passed in profileId must be non-null and non-empty string!" );
        }
        return new File( root, profileId );
    }

    protected ArtifactRepository getArtifactRepositoryForDirectory( final File stagingDirectory )
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

                return artifactRepositoryFactory.createDeploymentArtifactRepository( id, url, artifactRepositoryLayout,
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

    protected ArtifactRepository getArtifactRepositoryForNexus( final StagingRepository stagingRepository )
        throws InvalidRepositoryException
    {
        final ArtifactRepository result =
            artifactRepositoryFactory.createArtifactRepository( getRemoting().getServer().getId(),
                stagingRepository.getUrl(), artifactRepositoryLayout, null, null );
        return result;
    }
}
