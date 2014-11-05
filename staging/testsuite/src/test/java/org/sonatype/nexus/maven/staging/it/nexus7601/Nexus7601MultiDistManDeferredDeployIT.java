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

package org.sonatype.nexus.maven.staging.it.nexus7601;

import java.io.File;
import java.io.IOException;

import org.sonatype.nexus.maven.staging.it.PreparedVerifier;

import org.apache.maven.it.VerificationException;

/**
 * IT for https://issues.sonatype.org/browse/NEXUS-7601
 * <p>
 * It verifies that in a multi module setup with multiple distributionManagement sections
 * the proper one is used for each module.
 */
public class Nexus7601MultiDistManDeferredDeployIT
    extends Nexus7601MultiDistManMetadataSupport
{

  public Nexus7601MultiDistManDeferredDeployIT(final String nexusBundleCoordinates) {
    super(nexusBundleCoordinates);
  }

  // ==

  @Override
  protected PreparedVerifier createMavenVerifier(final String mavenVersion, final File projectDirectory)
      throws VerificationException, IOException
  {
    return createMavenVerifier(getClass().getSimpleName(), mavenVersion,
        testData().resolveFile("preset-nexus-maven-settings.xml"), projectDirectory, "1.0-SNAPSHOT");
  }

  @Override
  protected String getTargetedRepositoryId() {
    return "snapshots";
  }
}
