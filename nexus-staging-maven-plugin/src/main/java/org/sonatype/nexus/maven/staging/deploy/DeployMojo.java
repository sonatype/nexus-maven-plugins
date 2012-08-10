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
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;

/**
 * Alternative deploy plugin, that for all except last module actually "stages" to a location on local disk, and on last
 * module does the actual deploy.
 * 
 * @author cstamas
 * @since 2.1
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
     * Set this to {@code true} to bypass staging altogether (skip complete Mojo execution).
     * 
     * @parameter expression="${skipStaging}"
     */
    private boolean skipStaging = false;

    /**
     * Set this to {@code true} to bypass artifact deploy that would happen once last project is being staged.
     * 
     * @parameter expression="${skipLocalStaging}"
     */
    private boolean skipLocalStaging = false;

    /**
     * Set this to {@code true} to bypass artifact deploy that would happen once last project is being staged.
     * 
     * @parameter expression="${skipRemoteStaging}"
     */
    private boolean skipRemoteStaging = false;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( skipStaging )
        {
            getLog().info( "Skipping Nexus Staging." );
            return;
        }

        // StagingV2 cannot work offline, it needs REST calls to talk to Nexus even if not
        // deploying remotely, but for stuff like profile selection, matching, etc.
        failIfOffline();

        final String profileId = selectStagingProfile();
        final File stagingDirectory = getStagingDirectory( profileId );

        getLog().info( "Staging locally (stagingDirectory=\"" + stagingDirectory.getAbsolutePath() + "\")..." );

        // Deploy the POM
        boolean isPomArtifact = "pom".equals( packaging );
        if ( !isPomArtifact )
        {
            ArtifactMetadata metadata = new ProjectArtifactMetadata( artifact, pomFile );
            artifact.addMetadata( metadata );
        }

        if ( updateReleaseInfo )
        {
            artifact.setRelease( true );
        }

        try
        {
            if ( isPomArtifact )
            {
                stageLocally( pomFile, artifact, getLocalRepository(), stagingDirectory );
            }
            else
            {
                File file = artifact.getFile();

                if ( file != null && file.isFile() )
                {
                    stageLocally( file, artifact, getLocalRepository(), stagingDirectory );
                }
                else if ( !attachedArtifacts.isEmpty() )
                {
                    getLog().info( "No primary artifact to deploy, deploying attached artifacts instead." );

                    Artifact pomArtifact =
                        getArtifactFactory().createProjectArtifact( artifact.getGroupId(), artifact.getArtifactId(),
                            artifact.getBaseVersion() );
                    pomArtifact.setFile( pomFile );
                    if ( updateReleaseInfo )
                    {
                        pomArtifact.setRelease( true );
                    }

                    stageLocally( pomFile, pomArtifact, getLocalRepository(), stagingDirectory );

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
                stageLocally( attached.getFile(), attached, getLocalRepository(), stagingDirectory );
            }
        }
        catch ( ArtifactDeploymentException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

        if ( isThisLastProjectWithThisMojoInExecution() )
        {
            if ( !skipRemoteStaging )
            {
                failIfOffline();

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
                    "Artifacts staged in directory " + stagingDirectory.getAbsolutePath() + ", skipping deploy." );
            }
        }
    }
}