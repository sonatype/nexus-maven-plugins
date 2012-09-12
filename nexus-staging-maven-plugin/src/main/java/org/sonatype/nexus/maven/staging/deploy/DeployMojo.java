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
import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;

/**
 * Alternative deploy plugin, that for all except last module actually "stages" to a location on local disk, and on last
 * module does the actual deploy.
 * 
 * @author cstamas
 * @since 1.0
 * @goal deploy
 * @phase deploy
 */
public class DeployMojo
    extends AbstractDeployMojo
{
    /**
     * @parameter default-value="${project.artifact}"
     * @required
     * @readonly
     */
    private Artifact artifact;

    /**
     * @parameter default-value="${project.packaging}"
     * @required
     * @readonly
     */
    private String packaging;

    /**
     * @parameter default-value="${project.file}"
     * @required
     * @readonly
     */
    private File pomFile;

    /**
     * @parameter default-value="${project.attachedArtifacts}
     * @required
     * @readonly
     */
    private List<Artifact> attachedArtifacts;

    /**
     * Parameter used to update the metadata to make the artifact as release.
     * 
     * @parameter expression="${updateReleaseInfo}" default-value="false"
     */
    protected boolean updateReleaseInfo;

    /**
     * Set this to {@code true} to bypass this mojo altogether (skip complete Mojo execution).
     * 
     * @parameter expression="${skipNexusStagingDeployMojo}"
     */
    private boolean skipNexusStagingDeployMojo = false;

    /**
     * Set this to {@code true} to bypass staging features, and use deferred depoy features only.
     * 
     * @parameter expression="${deferredDeployOnly}"
     */
    private boolean deferredDeployOnly = false;

    /**
     * Set this to {@code true} to bypass staging features completely, and make this plugin behave in exact way as
     * "maven-deploy-plugin" would, such as deploying at "deploy" phase in every module build to remote. In this case,
     * the repository deployment goes against is taken in from Dependency Management section, again, same as in case of
     * "maven-deploy-plugin".
     * 
     * @parameter expression="${skipLocalStaging}"
     */
    private boolean skipLocalStaging = false;

    /**
     * Set this to {@code true} to bypass remote staging (upload of locally staged artifacts) that would happen once
     * last project is being locally staged.
     * 
     * @parameter expression="${skipRemoteStaging}"
     */
    private boolean skipRemoteStaging = false;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( skipNexusStagingDeployMojo )
        {
            getLog().info( "Skipping Nexus Staging Deploy Mojo at user's demand." );
            return;
        }

        // StagingV2 cannot work offline, it needs REST calls to talk to Nexus even if not
        // deploying remotely (for example skipRemoteStaging equals true), but for stuff like profile selection,
        // matching, etc.
        failIfOffline();

        final WorkflowType workflowType;
        final String profileId;
        final File stagingDirectory;
        if ( skipLocalStaging )
        // totally skipped
        {
            workflowType = WorkflowType.DIRECT_DEPLOY;
            profileId = selectStagingProfile( workflowType );
            stagingDirectory = null;
            getLog().info( "Performing direct deploys (maven-deploy-plugin like)..." );
        }
        else if ( deferredDeployOnly || artifact.isSnapshot() )
        // locally staging but uploading to deployment repo (no profiles and V2 used at all)
        {
            workflowType = WorkflowType.DEFERRED_DEPLOY;
            profileId = selectStagingProfile( workflowType );
            stagingDirectory = getStagingDirectory( profileId );
            getLog().info(
                "Performing deferred deploys (local stagingDirectory=\"" + stagingDirectory.getAbsolutePath()
                    + "\")..." );
        }
        else
        // for releases, everything used: profile selection, full V2, etc
        {
            workflowType = WorkflowType.STAGING_DEPLOY;
            profileId = selectStagingProfile( workflowType );
            stagingDirectory = getStagingDirectory( profileId );
            getLog().info(
                "Performing staging (local stagingDirectory=\"" + stagingDirectory.getAbsolutePath() + "\")..." );
        }

        // DEPLOY

        // Deploy the POM
        boolean isPomArtifact = "pom".equals( packaging );
        if ( !isPomArtifact )
        {
            artifact.addMetadata( new ProjectArtifactMetadata( artifact, pomFile ) );
        }

        if ( updateReleaseInfo )
        {
            artifact.setRelease( true );
        }

        try
        {
            if ( isPomArtifact )
            {
                doDeploy( workflowType, pomFile, artifact, stagingDirectory );
            }
            else
            {
                final File file = artifact.getFile();

                if ( file != null && file.isFile() )
                {
                    doDeploy( workflowType, file, artifact, stagingDirectory );
                }
                else if ( !attachedArtifacts.isEmpty() )
                {
                    getLog().info( "No primary artifact to deploy, deploying attached artifacts instead." );

                    final Artifact pomArtifact =
                        getArtifactFactory().createProjectArtifact( artifact.getGroupId(), artifact.getArtifactId(),
                            artifact.getBaseVersion() );
                    pomArtifact.setFile( pomFile );
                    if ( updateReleaseInfo )
                    {
                        pomArtifact.setRelease( true );
                    }

                    doDeploy( workflowType, pomFile, pomArtifact, stagingDirectory );

                    // propagate the timestamped version to the main artifact for the attached artifacts to pick it up
                    artifact.setResolvedVersion( pomArtifact.getVersion() );
                }
                else
                {
                    throw new MojoExecutionException(
                        "The packaging for this project did not assign a file to the build artifact" );
                }
            }

            for ( Iterator<Artifact> i = attachedArtifacts.iterator(); i.hasNext(); )
            {
                Artifact attached = i.next();
                doDeploy( workflowType, attached.getFile(), attached, stagingDirectory );
            }
        }
        catch ( ArtifactInstallationException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
        catch ( ArtifactDeploymentException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

        // if local staging skipped, we even can't do remote staging at all (as nothing is staged locally)
        if ( !WorkflowType.DIRECT_DEPLOY.equals( workflowType ) && isThisLastProjectWithThisMojoInExecution() )
        {
            if ( !skipRemoteStaging )
            {
                try
                {
                    stageRemotely();
                }
                catch ( ArtifactDeploymentException e )
                {
                    throw new MojoExecutionException( e.getMessage(), e );
                }
            }
            else
            {
                getLog().info(
                    "Artifacts locally staged in directory " + stagingDirectory.getAbsolutePath()
                        + ", skipping remote staging at user's demand." );
            }
        }
    }

    /**
     * A simple indirection that call the proper underlying methods to either do direct deploy or local staging.
     * 
     * @param workflow type
     * @param source
     * @param artifact
     * @param stagingDirectory
     * @throws ArtifactDeploymentException
     * @throws MojoExecutionException
     */
    protected void doDeploy( final WorkflowType workflowType, final File source, final Artifact artifact,
                             final File stagingDirectory )
        throws ArtifactInstallationException, ArtifactDeploymentException, MojoExecutionException
    {
        if ( WorkflowType.DIRECT_DEPLOY.equals( workflowType ) )
        {
            deploy( source, artifact, getTargetRepository( workflowType, stagingDirectory ) );
        }
        else
        {
            install( source, artifact, getTargetRepository( workflowType, stagingDirectory ), stagingDirectory );
        }
    }
}