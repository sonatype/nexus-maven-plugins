/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2015 Sonatype, Inc.
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

import org.sonatype.nexus.maven.staging.deploy.strategy.DeployStrategy;
import org.sonatype.nexus.maven.staging.deploy.strategy.FinalizeDeployRequest;
import org.sonatype.nexus.maven.staging.deploy.strategy.Strategies;

import com.google.common.base.Strings;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Deploys the (previously) staged artifacts from some local repository, that were staged using
 * {@code maven-deploy-plugin} together with switch {@code altDeploymentRepository} for cases when POM modifications
 * are
 * not possible for some reason. In contrast to {@link DeployMojo} and {@link DeployStagedMojo} mojos, this mojo takes
 * an "image" of the previously deployed (to local FS) in some previous build, and uploads the whole directory (with
 * all
 * files and subdirectories) "as-is" to a staging repository on Nexus. Naturally, this will would work only for a build
 * of a released project (like a tag checkout). Since POM is not editable -- otherwise you would not be using this but
 * the {@link DeployMojo} --, the intent with this Mojo is to be called directly, fully parameterized. At least, you
 * have to set {@code nexusUrl}, {@code serverId}, {@code stagingProfileId} (also {@code stagingRepositoryId} if
 * needed)
 * and {@code repositoryDirectory} parameters on CLI.
 *
 * @author cstamas
 * @since 1.1
 */
@Mojo(name = "deploy-staged-repository", requiresProject = false, requiresDirectInvocation = true,
    requiresOnline = true, threadSafe = true)
public class DeployRepositoryMojo
    extends AbstractDeployMojo
{

  /**
   * Specifies an the location of staging directory to which the project artifacts was staged using
   * {@code maven-deploy-plugin} together with switch {@code altDeploymentRepository}. Note: this parameters if of
   * type {@link java.io.File}, it has to point to an existend FS directory!
   * <p/>
   * TODO: should this parameter be marked as readOnly=true? As it is mandatory, and must be supplied on CLI as
   * -DreposioryDirectory=...
   */
  @Parameter(property = "repositoryDirectory", required = true)
  private File repositoryDirectory;

  @Override
  public void execute()
      throws MojoExecutionException, MojoFailureException
  {
    failIfOffline();

    if (repositoryDirectory == null) {
      throw new MojoFailureException(
          "Staged repository path is not set, use \"-DrepositoryDirectory=/some/path\" on CLI to set it.");
    }
    if (!repositoryDirectory.isDirectory()) {
      throw new MojoFailureException(
          "Staged repository path is not pointing to an existing (or readable?) directory! Path set is "
              + repositoryDirectory.getAbsolutePath());
    }
    if (Strings.isNullOrEmpty(getStagingProfileId())) {
      throw new MojoFailureException(
          "Stage profile ID is not set, use \"-DstagingProfileId=XXXX\" on CLI to set it.");
    }

    if (isThisLastProjectWithThisMojoInExecution()) {
      try {
        final DeployStrategy deployStrategy = getDeployStrategy(Strategies.IMAGE);
        final FinalizeDeployRequest request = new FinalizeDeployRequest(getMavenSession(), buildParameters());

        deployStrategy.finalizeDeploy(request);
      }
      catch (ArtifactDeploymentException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }
  }

  /**
   * This goal override the parent's AbstractStagingMojo#getStagingDirectoryRoot as this Mojo explicitly receives
   * (and validates) it in the #execute method.
   */
  @Override
  protected File getStagingDirectoryRoot() {
    return repositoryDirectory;
  }
}
