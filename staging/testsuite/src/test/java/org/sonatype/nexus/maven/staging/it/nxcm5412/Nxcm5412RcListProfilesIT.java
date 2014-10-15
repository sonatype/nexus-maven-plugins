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

package org.sonatype.nexus.maven.staging.it.nxcm5412;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.SimpleRoundtripMatrixSupport;
import org.sonatype.sisu.litmus.testsupport.hamcrest.FileMatchers;

import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.Test;
import org.apache.maven.it.VerificationException;

/**
 * IT for NXCM-5412 Add an rc-list goal to the nexus-staging-maven-plugin
 * <p>
 * Testing the rc-list-profiles goal.
 * 
 * @author cstamas
 * @see <a href="https://issues.sonatype.org/browse/NXCM-5412">NXCM-5412</a>
 */
public class Nxcm5412RcListProfilesIT
    extends SimpleRoundtripMatrixSupport
{

  public Nxcm5412RcListProfilesIT(final String nexusBundleCoordinates) {
    super(nexusBundleCoordinates);
  }

  /**
   * Maven Plugin Project set up in m2-way with m2.
   */
  @Test
  public void roundtripWithM2() throws VerificationException, IOException {
    // HACK: The project passed in here is irrelevant, as goal we test does not require a project
    // but createMavenVerifier method must have a baseDir input!
    roundtrip(createMavenVerifier(M22_VERSION, new File(getBasedir(), "target/test-classes/maven2-maven-plugin-project")));
  }

  /**
   * Maven Plugin Project set up in m3-way using m3.
   */
  @Test
  public void roundtripWithM30() throws VerificationException, IOException {
    // HACK: The project passed in here is irrelevant, as goal we test does not require a project
    // but createMavenVerifier method must have a baseDir input!
    roundtrip(createMavenVerifier(M30_VERSION, new File(getBasedir(), "target/test-classes/maven3-maven-plugin-project")));
  }

  /**
   * Maven Plugin Project set up in m3-way using m3.
   */
  @Test
  public void roundtripWithM31() throws VerificationException, IOException {
    // HACK: The project passed in here is irrelevant, as goal we test does not require a project
    // but createMavenVerifier method must have a baseDir input!
    roundtrip(createMavenVerifier(M31_VERSION, new File(getBasedir(), "target/test-classes/maven3-maven-plugin-project")));
  }

  /**
   * Maven Plugin Project set up in m3-way using m3.
   */
  @Test
  public void roundtripWithM32() throws VerificationException, IOException {
    // HACK: The project passed in here is irrelevant, as goal we test does not require a project
    // but createMavenVerifier method must have a baseDir input!
    roundtrip(createMavenVerifier(M32_VERSION, new File(getBasedir(), "target/test-classes/maven3-maven-plugin-project")));
  }

  @Override
  protected void preNexusAssertions(PreparedVerifier verifier) {
    // nop
  }

  @Override
  protected void postNexusAssertions(PreparedVerifier verifier) {
    // see src/test/it-resources/preset-nexus/staging.xml for predefined profiles
    assertThat(new File(verifier.getBasedir(), verifier.getLogFileName()),
        FileMatchers.contains("12a2439c79f79c6f", "23b3440d80080d70"));
  }

  @Override
  protected void invokeMaven(PreparedVerifier verifier) throws VerificationException {
    verifier.addCliOption("-DserverId=local-nexus");
    verifier.addCliOption("-DnexusUrl=" + nexus().getUrl());

    verifier.executeGoals(Arrays.asList("nexus-staging:rc-list-profiles"));
    // should not fail
    verifier.verifyErrorFreeLog();
  }
}
