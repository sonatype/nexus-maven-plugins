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
package org.sonatype.nexus.maven.staging.it.multiprofile;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.sonatype.nexus.bundle.launcher.NexusBundleConfiguration;
import org.sonatype.nexus.maven.staging.it.StagingMavenPluginITSupport;
import org.sonatype.nexus.testsuite.support.NexusStartAndStopStrategy;
import org.sonatype.sisu.filetasks.FileTaskBuilder;

import static org.sonatype.nexus.testsuite.support.NexusStartAndStopStrategy.Strategy.EACH_METHOD;
import static org.sonatype.sisu.filetasks.builder.FileRef.file;
import static org.sonatype.sisu.filetasks.builder.FileRef.path;

/**
 * Support class for multimodule/multiprofile ITs
 */
@NexusStartAndStopStrategy(EACH_METHOD)
public abstract class MultiprofileITSupport
    extends StagingMavenPluginITSupport
{
  @Inject
  private FileTaskBuilder fileTaskBuilder;

  public MultiprofileITSupport(final String nexusBundleCoordinates) {
    super(nexusBundleCoordinates);
  }

  @Override
  protected NexusBundleConfiguration configureNexus(final NexusBundleConfiguration configuration) {
    // TODO: (cstamas) I promised to Alin to change this "old way of doing things" to use of REST API that would
    // configure Nexus properly once the Security and Staging Management Nexus Client subsystems are done.
    return super.configureNexus(configuration).addOverlays(
        fileTaskBuilder.copy().directory(file(testData().resolveFile("preset-nexus"))).to().directory(
            path("sonatype-work/nexus/conf")));
  }

  @Override
  protected List<String> getMavenVersions() {
    return Arrays.asList(M30_VERSION, M31_VERSION, M32_VERSION);
  }
}
