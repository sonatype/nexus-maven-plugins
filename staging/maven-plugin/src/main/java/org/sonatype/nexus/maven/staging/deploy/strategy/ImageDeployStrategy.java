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

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import com.sonatype.nexus.staging.client.Profile;

import org.sonatype.nexus.client.core.NexusStatus;
import org.sonatype.nexus.maven.staging.deploy.StagingRepository;
import org.sonatype.nexus.maven.staging.remote.RemoteNexus;
import org.sonatype.nexus.maven.staging.zapper.Zapper;
import org.sonatype.nexus.maven.staging.zapper.ZapperRequest;

import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

/**
 * Image deploy strategy, that stages the locally present directory structure to remote in "as is" form. It uses
 * staging features, but, uploads the "image", folder structure as-is.
 *
 * @author cstamas
 * @since 1.1
 */
@Component(role = DeployStrategy.class, hint = Strategies.IMAGE)
public class ImageDeployStrategy
    extends AbstractStagingDeployStrategy
{
  /**
   * Zapper component.
   */
  @Requirement
  private Zapper zapper;

  /**
   * This method is actually unused in this strategy, as the "image" to be deployed is prepared by something else.
   * For example, it might be prepared with maven-deploy-plugin using altDeploymentRepository switch pointing to local
   * file system.
   */
  @Override
  public void deployPerModule(final DeployPerModuleRequest request)
      throws ArtifactInstallationException, ArtifactDeploymentException, MojoExecutionException
  {
    // nothing
  }

  /**
   * Remote deploys the "image", using {@link #zapUp(Server, Proxy, File, String)}.
   */
  @Override
  public void finalizeDeploy(final FinalizeDeployRequest request)
      throws ArtifactDeploymentException, MojoExecutionException
  {
    getLogger().info("Staging remotely locally deployed repository...");
    final RemoteNexus remoteNexus = createRemoteNexus(request.getMavenSession(), request.getParameters());
    final NexusStatus nexusStatus = remoteNexus.getNexusStatus();
    getLogger().info(
        String.format(" * Connected to Nexus at %s, is version %s and edition \"%s\"",
            remoteNexus.getConnectionInfo().getBaseUrl(), nexusStatus.getVersion(), nexusStatus.getEditionLong()));

    final String profileId = request.getParameters().getStagingProfileId();
    final Profile stagingProfile = remoteNexus.getStagingWorkflowV2Service().selectProfile(profileId);
    final StagingRepository stagingRepository = beforeUpload(request.getParameters(), remoteNexus, stagingProfile);
    try {
      getLogger().info(" * Uploading locally staged artifacts to profile " + stagingProfile.name());
      zapUp(remoteNexus.getServer(), remoteNexus.getProxy(), request.getParameters().getStagingDirectoryRoot(),
          stagingRepository.getUrl());
      getLogger().info(" * Upload of locally staged artifacts finished.");
      afterUpload(request.getParameters(), remoteNexus, stagingRepository);
    }
    catch (Exception e) {
      afterUploadFailure(request.getParameters(), remoteNexus, Collections.singletonList(stagingRepository), e);
      getLogger().error("Remote staging finished with a failure.");
      throw new ArtifactDeploymentException("Remote staging failed: " + e.getMessage(), e);
    }
    getLogger().info("Remote staging finished with success.");
  }

  /**
   * Uploads the {@code sourceDirectory} to the {@code deployUrl} as a "whole". This means, that the "image"
   * (sourceDirectory) should be already prepared, as there will be no transformations applied to them, content and
   * filenames will be deploy as-is.
   */
  protected void zapUp(final Server server, final Proxy proxy, final File sourceDirectory, final String deployUrl)
      throws IOException
  {
    final ZapperRequest request = new ZapperRequest(sourceDirectory, deployUrl);
    if (server != null) {
      request.setRemoteUsername(server.getUsername());
      request.setRemotePassword(server.getPassword());
    }
    if (proxy != null) {
      request.setProxyProtocol(proxy.getProtocol());
      request.setProxyHost(proxy.getHost());
      request.setProxyPort(proxy.getPort());
      request.setProxyUsername(proxy.getUsername());
      request.setProxyPassword(proxy.getPassword());
    }
    zapper.deployDirectory(request);
  }

}
