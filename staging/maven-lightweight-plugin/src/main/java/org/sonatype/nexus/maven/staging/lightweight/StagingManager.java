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

package org.sonatype.nexus.maven.staging.lightweight;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;

/**
 * Staging manager that manages staging and mutates the projects too.
 */
public interface StagingManager
{
  /**
   * Method performing initialization.
   */
  void initialize(final MavenSession mavenSession);

  /**
   * Method invoked after all the projects have be read up by Maven. Here, NexusClient is being built, and
   * staging profile is determined, and staging is started for given profile. The repository URLs are then
   * "redirected" to deploy into newly created staging repository. This method intentionally resembles
   * an {@link AbstractMavenLifecycleParticipant#afterProjectsRead(MavenSession)} method name, as ideally,
   * that's how it should be invoked.
   */
  void afterProjectsRead()
      throws MavenExecutionException;

  /**
   * Method invoked at the build end. On successful build outcome, it will close the staging repository, and if needed,
   * release it too. In case of build failure, if required, repository will be dropped. This method intentionally
   * resembles {@link AbstractMavenLifecycleParticipant#afterSessionEnd(MavenSession)} method name, as ideally,
   * that's how it should be invoked.
   */
  public void afterSessionEnd()
      throws MavenExecutionException;
}
