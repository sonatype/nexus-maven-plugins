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

import org.sonatype.nexus.maven.staging.deploy.strategy.DeployStrategy;
import org.sonatype.nexus.maven.staging.deploy.strategy.FinalizeDeployRequest;
import org.sonatype.nexus.maven.staging.deploy.strategy.Strategies;

import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Deploys the (previously) locally staged artifacts from nexus-staging repository, that were staged using
 * {@link DeployMojo} and having the {@link DeployMojo#skipRemoteStaging} flag set to {@code true}.
 *
 * @author cstamas
 * @since 1.0
 */
@Mojo(name = "deploy-staged", requiresOnline = true, threadSafe = true)
public class DeployStagedMojo
    extends AbstractDeployMojo
{
  @Override
  public void execute()
      throws MojoExecutionException, MojoFailureException
  {
    failIfOffline();

    if (isThisLastProjectWithThisMojoInExecution()) {
      try {
        final DeployStrategy deployStrategy;
        if (getMavenSession().getCurrentProject().getArtifact().isSnapshot()) {
          deployStrategy = getDeployStrategy(Strategies.DEFERRED);
        }
        else {
          if (isSkipStaging()){
        	deployStrategy = getDeployStrategy(Strategies.DEFERRED);
          } else {
        	deployStrategy = getDeployStrategy(Strategies.STAGING);
          }
        }

        final FinalizeDeployRequest request = new FinalizeDeployRequest(getMavenSession(), buildParameters());
        deployStrategy.finalizeDeploy(request);
      }
      catch (ArtifactDeploymentException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }
  }
}
