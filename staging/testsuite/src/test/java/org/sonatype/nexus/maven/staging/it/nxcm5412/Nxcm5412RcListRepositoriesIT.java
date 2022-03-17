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
package org.sonatype.nexus.maven.staging.it.nxcm5412;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.SimpleRoundtripMatrixSupport;

import org.apache.maven.it.VerificationException;
import org.junit.Test;
import org.sonatype.sisu.goodies.testsupport.hamcrest.FileMatchers;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * IT for NXCM-5412 Add an rc-list goal to the nexus-staging-maven-plugin
 * <p>
 * Testing the rc-list goal.
 *
 * @author cstamas
 * @see <a href="https://issues.sonatype.org/browse/NXCM-5412">NXCM-5412</a>
 */
public class Nxcm5412RcListRepositoriesIT
    extends SimpleRoundtripMatrixSupport
{

  public Nxcm5412RcListRepositoriesIT(final String nexusBundleCoordinates) {
    super(nexusBundleCoordinates);
  }

  /**
   * Maven Plugin Project set up in m2-way with m2.
   */
  @Test
  public void roundtripWithM2() throws VerificationException, IOException {
    // HACK: The project passed in here is irrelevant, as goal we test does not require a project
    // but createMavenVerifier method must have a baseDir input!
    roundtrip(
        createMavenVerifier(M22_VERSION, new File(getBasedir(), "target/test-classes/maven2-maven-plugin-project")));
  }

  /**
   * Maven Plugin Project set up in m3-way using m3.
   */
  @Test
  public void roundtripWithM30() throws VerificationException, IOException {
    // HACK: The project passed in here is irrelevant, as goal we test does not require a project
    // but createMavenVerifier method must have a baseDir input!
    roundtrip(
        createMavenVerifier(M30_VERSION, new File(getBasedir(), "target/test-classes/maven3-maven-plugin-project")));
  }

  /**
   * Maven Plugin Project set up in m3-way using m3.
   */
  @Test
  public void roundtripWithM31() throws VerificationException, IOException {
    // HACK: The project passed in here is irrelevant, as goal we test does not require a project
    // but createMavenVerifier method must have a baseDir input!
    roundtrip(
        createMavenVerifier(M31_VERSION, new File(getBasedir(), "target/test-classes/maven3-maven-plugin-project")));
  }

  /**
   * Maven Plugin Project set up in m3-way using m3.
   */
  @Test
  public void roundtripWithM32() throws VerificationException, IOException {
    // HACK: The project passed in here is irrelevant, as goal we test does not require a project
    // but createMavenVerifier method must have a baseDir input!
    roundtrip(
        createMavenVerifier(M32_VERSION, new File(getBasedir(), "target/test-classes/maven3-maven-plugin-project")));
  }

  @Override
  protected void preNexusAssertions(PreparedVerifier verifier) {
    // nop
  }

  @Override
  protected void postNexusAssertions(PreparedVerifier verifier) {
    // see invokeMaven method, we passed in fix description, this class name
    assertThat(new File(verifier.getBasedir(), verifier.getLogFileName()),
        FileMatchers.contains("test1-1000"));
  }

  @Override
  protected void invokeMaven(PreparedVerifier verifier) throws VerificationException {
    // stage the project
    verifier.addCliOption("-Ddescription=" + Nxcm5412RcListRepositoriesIT.class.getName());
    verifier.executeGoals(Arrays.asList("clean", "deploy"));
    verifier.verifyErrorFreeLog();

    // test rc-list goal
    verifier.addCliOption("-DserverId=local-nexus");
    verifier.addCliOption("-DnexusUrl=" + nexus().getUrl());
    verifier.executeGoals(Arrays.asList("nexus-staging:rc-list"));
    verifier.verifyErrorFreeLog();
  }
}
