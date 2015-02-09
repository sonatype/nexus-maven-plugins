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

package org.sonatype.nexus.maven.staging.workflow;

import com.google.common.base.Preconditions;
import com.sonatype.nexus.staging.client.StagingRuleFailuresException;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.core.exception.NexusClientErrorResponseException;
import org.sonatype.nexus.maven.staging.AbstractStagingMojo;
import org.sonatype.nexus.maven.staging.ErrorDumper;
import org.sonatype.nexus.maven.staging.StagingAction;
import org.sonatype.nexus.maven.staging.remote.Parameters;
import org.sonatype.nexus.maven.staging.remote.RemoteNexus;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Super class of non-RC Actions. These mojos are "plain" non-aggregator ones, and will use the property file from
 * staged repository to get the repository ID they need. This way, you can integrate these mojos in your build directly
 * (ie. to release or promote even).
 *
 * @author cstamas
 */
public abstract class AbstractStagingActionMojo
    extends AbstractStagingMojo
{
  /**
   * The RemoteNexus instance.
   */
  private NexusClient nexusClient;

  @Override
  public final void execute()
      throws MojoExecutionException, MojoFailureException
  {
    // all these RC or build actions cannot work in offline mode, as we perform remote REST calls
    failIfOffline();

    if (shouldExecute()) {
      final StagingWorkflowV2Service stagingWorkflow = createStagingWorkflowService();

      try {
        doExecute(stagingWorkflow);
      }
      catch (NexusClientErrorResponseException e) {
        ErrorDumper.dumpErrors(getLog(), e);
        // fail the build
        throw new MojoExecutionException("Could not perform action: Nexus ErrorResponse received!", e);
      }
      catch (StagingRuleFailuresException e) {
        ErrorDumper.dumpErrors(getLog(), e);
        // fail the build
        throw new MojoExecutionException("Could not perform action: there are failing staging rules!", e);
      }
    }
  }

  protected String getDescriptionWithDefaultsForAction(final StagingAction action)
      throws MojoExecutionException
  {
    return getStagingActionMessages().getMessageForAction(action);
  }

  protected boolean shouldExecute() {
    return true;
  }

  protected abstract void doExecute(final StagingWorkflowV2Service stagingWorkflow)
      throws MojoExecutionException, MojoFailureException;

  // == TRANSPORT

  /**
   * Initialized stuff needed for transport, stuff like: Server, Proxy and RemoteNexus.
   */
  protected StagingWorkflowV2Service createStagingWorkflowService()
      throws MojoExecutionException
  {
    try {
      final Parameters parameters = buildParameters();
      final RemoteNexus remoteNexus = new RemoteNexus(getMavenSession(), getSecDispatcher(), parameters);
      return remoteNexus.getStagingWorkflowV2Service();
    }
    catch (Exception e) {
      throw new MojoExecutionException("Nexus connection problem: " + e.getMessage(), e);
    }
  }

  /**
   * Initialized stuff needed for transport, stuff like: Server, Proxy and RemoteNexus.
   */
  protected StagingWorkflowV2Service createStagingWorkflowService(String nexusUrl)
          throws MojoExecutionException
  {
    Preconditions.checkNotNull(nexusUrl);
    try {
      final Parameters parameters = buildParameters();
      parameters.setNexusUrl(nexusUrl);
      final RemoteNexus remoteNexus = new RemoteNexus(getMavenSession(), getSecDispatcher(), parameters);
      return remoteNexus.getStagingWorkflowV2Service();
    }
    catch (Exception e) {
      throw new MojoExecutionException("Nexus connection problem: " + e.getMessage(), e);
    }
  }
}
