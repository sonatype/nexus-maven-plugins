/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2013 Sonatype, Inc.
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.sonatype.nexus.maven.staging.deploy.strategy.DeployPerModuleRequest;
import org.sonatype.nexus.maven.staging.deploy.strategy.DeployStrategy;
import org.sonatype.nexus.maven.staging.deploy.strategy.FinalizeDeployRequest;
import org.sonatype.nexus.maven.staging.deploy.strategy.Parameters;
import org.sonatype.nexus.maven.staging.deploy.strategy.ParametersImpl;
import org.sonatype.nexus.maven.staging.deploy.strategy.StagingParameters;
import org.sonatype.nexus.maven.staging.deploy.strategy.StagingParametersImpl;
import org.sonatype.nexus.maven.staging.deploy.strategy.Strategies;

/**
 * Alternative deploy mojo, that will select proper {@link DeployStrategy} to perform deploys. Hence, this mojo might
 * function in same was as maven-deploy-plugin's deploy mojo, but also might do deferred deploy or staging.
 *
 * @author cstamas
 * @since 1.0
 */
@Mojo( name = "deploy", defaultPhase = LifecyclePhase.DEPLOY, requiresOnline = true )
public class DeployMojo
    extends AbstractDeployMojo
{

    /**
     * Component used to create an artifact.
     */
    @Component
    private ArtifactFactory artifactFactory;

    /**
     * Project Artifact.
     */
    @Parameter( defaultValue = "${project.artifact}", readonly = true, required = true )
    private Artifact artifact;

    /**
     * Project packaging.
     */
    @Parameter( defaultValue = "${project.packaging}", readonly = true, required = true )
    private String packaging;

    /**
     * Project POM file.
     */
    @Parameter( defaultValue = "${project.file}", readonly = true, required = true )
    private File pomFile;

    /**
     * Project's attached artifacts.
     */
    @Parameter( defaultValue = "${project.attachedArtifacts}", readonly = true, required = true )
    private List<Artifact> attachedArtifacts;

    /**
     * Parameter used to update the metadata to make the artifact as release.
     */
    @Parameter( property = "updateReleaseInfo" )
    private boolean updateReleaseInfo;

    /**
     * Set this to {@code true} to bypass this mojo altogether (skip complete Mojo execution).
     */
    @Parameter( property = "skipNexusStagingDeployMojo" )
    private boolean skipNexusStagingDeployMojo;

    /**
     * Set this to {@code true} to bypass staging features completely, and make this plugin behave in exact way as
     * "maven-deploy-plugin" would, such as deploying at "deploy" phase in every module build to remote. In this case,
     * the repository deployment goes against is taken in from Dependency Management section, again, same as in case of
     * "maven-deploy-plugin".
     */
    @Parameter( property = "skipLocalStaging" )
    private boolean skipLocalStaging;

    /**
     * Set this to {@code true} to bypass remote staging (upload of locally staged artifacts) that would happen once
     * last project is being locally staged.
     */
    @Parameter( property = "skipRemoteStaging" )
    private boolean skipRemoteStaging;

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

        final Parameters parameters;
        final DeployStrategy deployStrategy;
        if ( skipLocalStaging )
        // totally skipped
        {
            deployStrategy = getDeployStrategy( Strategies.DIRECT );
        }
        else if ( isSkipStaging() || artifact.isSnapshot() )
        // locally staging but uploading to deployment repo (no profiles and V2 used at all)
        {
            deployStrategy = getDeployStrategy( Strategies.DEFERRED );
        }
        else
        // for releases, everything used: profile selection, full V2, etc
        {
            deployStrategy = getDeployStrategy( Strategies.STAGING );
        }

        // DEPLOY
        final ArrayList<DeployableArtifact> deployables = new ArrayList<DeployableArtifact>( 2 );

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
                deployables.add( new DeployableArtifact( pomFile, artifact ) );
            }
            else
            {
                final File file = artifact.getFile();

                if ( file != null && file.isFile() )
                {
                    deployables.add( new DeployableArtifact( file, artifact ) );
                }
                else if ( !attachedArtifacts.isEmpty() )
                {
                    getLog().info( "No primary artifact to deploy, deploying attached artifacts instead." );

                    final Artifact pomArtifact =
                        artifactFactory.createProjectArtifact( artifact.getGroupId(), artifact.getArtifactId(),
                                                               artifact.getBaseVersion() );
                    pomArtifact.setFile( pomFile );
                    if ( updateReleaseInfo )
                    {
                        pomArtifact.setRelease( true );
                    }

                    deployables.add( new DeployableArtifact( pomFile, pomArtifact ) );

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
                deployables.add( new DeployableArtifact( attached.getFile(), attached ) );
            }

            parameters = buildParameters( deployStrategy );
            final DeployPerModuleRequest request =
                new DeployPerModuleRequest( getMavenSession(), parameters, deployables );
            deployStrategy.deployPerModule( request );
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
        if ( isThisLastProjectWithThisMojoInExecution() )
        {
            if ( skipRemoteStaging )
            {
                getLog().info(
                    "Artifacts locally staged in directory " + parameters.getStagingDirectoryRoot().getAbsolutePath()
                        + ", skipping remote staging at user's demand." );
                return;
            }

            try
            {
                final FinalizeDeployRequest request = new FinalizeDeployRequest( getMavenSession(), parameters );
                deployStrategy.finalizeDeploy( request );
            }
            catch ( ArtifactDeploymentException e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }
        }
    }
}