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

package org.sonatype.nexus.maven.staging.deploy.strategy;

import org.sonatype.nexus.maven.staging.deploy.DeployableArtifact;

import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

/**
 * Direct deploy strategy, one that totally mimics the "vanilla" maven-deploy-plugin behaviour.
 *
 * @author cstamas
 * @since 1.1
 */
@Component(role = DeployStrategy.class, hint = Strategies.DIRECT)
public class DirectDeployStrategy
    extends AbstractDeployStrategy
{
  @Requirement
  private ArtifactDeployer artifactDeployer;

  /**
   * Remote deploy immediately all we have, at the end of the module build.
   */
  @Override
  public void deployPerModule(final DeployPerModuleRequest request)
      throws ArtifactInstallationException, ArtifactDeploymentException, MojoExecutionException
  {
    getLogger().info("Performing direct deploys (maven-deploy-plugin like)...");
    final ArtifactRepository deploymentRepository = getDeploymentRepository();
    final ArtifactRepository localRepository = getMavenSession().getLocalRepository();
    for (DeployableArtifact deployableArtifact : request.getDeployableArtifacts()) {
      artifactDeployer.deploy(deployableArtifact.getFile(), deployableArtifact.getArtifact(),
          deploymentRepository, localRepository);
    }
  }

  /**
   * Doing nothing in this method, as everything is already remotely deployed.
   */
  @Override
  public void finalizeDeploy(final FinalizeDeployRequest request)
      throws ArtifactDeploymentException, MojoExecutionException
  {
    // nothing, all is up already
  }
}
