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
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

/**
 * Lifecycle participant that is meant to perform "lightweight" (simpler) staging workflow that the
 * nexus-staging-plugin does, using {@link StagingManager}.
 */
@Component(role = AbstractMavenLifecycleParticipant.class,
    hint = "org.sonatype.nexus.maven.staging.lightweight.LightweightStagingLifecycleParticipant")
public class LightweightStagingLifecycleParticipant
    extends AbstractMavenLifecycleParticipant
{
  @Requirement
  private StagingManager stagingManager;

  @Override
  public void afterProjectsRead(MavenSession session)
      throws MavenExecutionException
  {
    stagingManager.initialize(session);
    stagingManager.afterProjectsRead();
  }

  @Override
  public void afterSessionEnd(MavenSession session)
      throws MavenExecutionException
  {
    stagingManager.afterSessionEnd();
  }
}
