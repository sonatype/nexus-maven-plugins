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
package org.sonatype.nexus.maven.staging.it.nxcm5194;

import java.io.File;
import java.io.IOException;

import org.sonatype.nexus.maven.staging.it.PreparedVerifier;

import org.apache.maven.it.VerificationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

/**
 * IT for https://issues.sonatype.org/browse/NXCM-5194
 * <p>
 * It builds Releases of Maven Plugin, hence uses Staging (and releases it) to deploy them into "releases" repo.
 *
 * @author cstamas
 */
public class Nxcm5194GLevelRMDStagingIT
    extends Nxcm5194GLevelRepositoryMetadataSupport
{

  public Nxcm5194GLevelRMDStagingIT(final String nexusBundleCoordinates) {
    super(nexusBundleCoordinates);
  }

  // ==

  @Override
  protected PreparedVerifier createMavenVerifier(final String mavenVersion, final File projectDirectory)
      throws VerificationException, IOException
  {
    return createMavenVerifier(getClass().getSimpleName(), mavenVersion,
        testData().resolveFile("preset-nexus-maven-settings.xml"), projectDirectory, "1.0");
  }

  @Override
  protected String getTargetedRepositoryId() {
    // this is the "target" group, so we will fetch the staged MD over it to not have to guess the repo
    return "public";
  }

  // == Assertions (we do use staging, so we need a bit "extra" work here)

  @Override
  protected void preNexusAssertions(final PreparedVerifier verifier) {
    // on 1st deploy it will be empty, but subsequent tests (2nd, 3rd) not
    // so we just leave pre-assertion empty
  }

  @Override
  protected void postNexusAssertions(final PreparedVerifier verifier) {
    assertThat(getAllStagingRepositories().toString(), getAllStagingRepositories(), not(empty()));
    checkGLevelMD(verifier);
  }
}
