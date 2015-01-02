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
package org.sonatype.nexus.maven.staging.deploy.strategy;

import java.io.File;
import java.io.IOException;

import org.sonatype.nexus.maven.staging.deploy.DeployableArtifact;

import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.component.annotations.Component;

/**
 * Deferred deploy strategy, that locally installs the stuff to be deployed (together with maintaining an "index" of
 * deployable artifacts). At the reactor build end, remote deploys happens driven by "index".
 *
 * @author cstamas
 * @since 1.1
 */
@Component(role = DeployStrategy.class, hint = Strategies.DEFERRED)
public class DeferredDeployStrategy
    extends AbstractDeployStrategy
{
  /**
   * Performs local install plus maintains the index file, that contains needed informations needed to perform remote
   * deploys.
   */
  @Override
  public void deployPerModule(final DeployPerModuleRequest request)
      throws ArtifactInstallationException, ArtifactDeploymentException, MojoExecutionException
  {
    log.info(
        "Performing deferred deploys (gathering into \"{}\")...",
        request.getParameters().getDeferredDirectoryRoot().getAbsolutePath());
    if (!request.getDeployableArtifacts().isEmpty()) {
      // deploys always to same stagingDirectory
      final File stagingDirectory = request.getParameters().getDeferredDirectoryRoot();
      final ArtifactRepository stagingRepository = getArtifactRepositoryForDirectory(stagingDirectory);
      final ArtifactRepository deploymentRepository = getDeploymentRepository(request.getMavenSession());
      
      for (DeployableArtifact deployableArtifact : request.getDeployableArtifacts()) {
        install(deployableArtifact.getFile(), deployableArtifact.getArtifact(), stagingRepository,
            stagingDirectory, deploymentRepository);
      }
    }
    else {
      log.info("Nothing to locally stage?");
    }
  }

  /**
   * Performs "bulk" remote deploy of locally installed artifacts, and is driven by "index" file.
   */
  @Override
  public void finalizeDeploy(final FinalizeDeployRequest request)
      throws ArtifactDeploymentException, MojoExecutionException
  {
    log.info("Deploying remotely...");
    final File stagingDirectory = request.getParameters().getDeferredDirectoryRoot();
    if (!stagingDirectory.isDirectory()) {
      log.warn(
          "Nothing to deploy, directory \"{}\" does not exists!", stagingDirectory.getAbsolutePath());
      return;
    }

    // we do direct upload
    log.info("Bulk deploying locally gathered artifacts from directory: ");
    try {
      // prepare the local staging directory
      // we have normal deploy
      log.info(
          " * Bulk deploying locally gathered snapshot artifacts");
      deployUp(request.getMavenSession(), stagingDirectory, null);
      log.info(" * Bulk deploy of locally gathered snapshot artifacts finished.");
    }
    catch (IOException e) {
      log.error("Upload of locally deferred directory finished with a failure.");
      throw new ArtifactDeploymentException("Remote deploy failed: " + e.getMessage(), e);
    }

    log.info("Remote deploy finished with success.");
  }
}
