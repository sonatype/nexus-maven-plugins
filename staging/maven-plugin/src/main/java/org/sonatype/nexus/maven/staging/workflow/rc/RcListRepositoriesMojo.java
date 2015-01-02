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
package org.sonatype.nexus.maven.staging.workflow.rc;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.sonatype.nexus.staging.client.StagingRepository;
import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;

import org.sonatype.nexus.maven.staging.workflow.AbstractStagingActionMojo;

import org.apache.maven.plugins.annotations.Parameter;

import com.google.common.base.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Lists staging repositories accessible by current user available on Nexus.
 * 
 * @author cstamas
 * @since 2.7.0
 */
@Mojo(name = "rc-list", requiresProject = false, requiresDirectInvocation = true, requiresOnline = true)
public class RcListRepositoriesMojo
    extends AbstractStagingActionMojo
{
  private static final String FORMAT_MASK = "%-20s %-8s %-30s";

  /**
   * Parameter that drives logic to filter out RELEASED repositories from console output, as those repositories might
   * dropped only, and is usually done by server side cleanup task.
   */
  @Parameter(property = "showReleased", defaultValue = "false")
  private boolean showReleased;

  @Override
  public void doExecute(final StagingWorkflowV2Service stagingWorkflow) throws MojoExecutionException,
      MojoFailureException
  {
    getLog().info("Getting list of available staging repositories...");
    final List<StagingRepository> stagingRepositories = stagingWorkflow.listStagingRepositories();
    Collections.sort(stagingRepositories, new RepositoryComparator());
    getLog().info("");
    getLog().info(String.format(FORMAT_MASK, "ID", "State", "Description"));
    for (StagingRepository stagingRepository : stagingRepositories) {
      if ((StagingRepository.State.RELEASED == stagingRepository.getState()) && !showReleased) {
        continue;
      }
      final String line = String.format(FORMAT_MASK, stagingRepository.getId(), stagingRepository.getState(),
          clean(stagingRepository.getDescription()));
      getLog().info(line);
    }
  }

  // ==

  public static class RepositoryComparator
      implements Comparator<StagingRepository>
  {
    @Override
    public int compare(final StagingRepository o1, final StagingRepository o2) {
      // group by state
      if (o1.getState() != o2.getState()) {
        return o1.getState().compareTo(o2.getState());
      }
      // within state, sort by id
      return o1.getId().compareTo(o2.getId());
    }
  }

  private String clean(final String str) {
    return Strings.nullToEmpty(str).replaceAll("\\n", " ");
  }
}
