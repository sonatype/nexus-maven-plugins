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

package org.sonatype.nexus.maven.staging.it.nxcm5297;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.sonatype.nexus.staging.client.StagingRepository;
import com.sonatype.nexus.staging.client.StagingRepository.State;

import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.SimpleRoundtripMatrixSupport;

import com.google.common.base.Throwables;
import org.apache.maven.it.VerificationException;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test for NXCM-5297 nexus-staging:promote should print the build promotion repository id that gets created.
 *
 * @author cstamas
 */
public abstract class Nxcm5297PrintPromotionGroupIdSupport
    extends SimpleRoundtripMatrixSupport
{
  public Nxcm5297PrintPromotionGroupIdSupport(final String nexusBundleCoordinates) {
    super(nexusBundleCoordinates);
  }

  // == the tests

  /**
   * Maven Plugin Project set up in m2-way with m2.
   */
  @Test
  public void roundtripWithM2ProjectUsingM2()
      throws VerificationException, IOException
  {
    roundtrip(createMavenVerifier(M22_VERSION, new File(getBasedir(),
        "target/test-classes/maven2-maven-plugin-project")));
  }

  /**
   * Maven Plugin Project set up in m2-way with m3.
   */
  @Test
  public void roundtripWithM2ProjectUsingM30()
      throws VerificationException, IOException
  {
    roundtrip(createMavenVerifier(M30_VERSION, new File(getBasedir(),
        "target/test-classes/maven2-maven-plugin-project")));
  }

  /**
   * Maven Plugin Project set up in m2-way with m3.
   */
  @Test
  public void roundtripWithM2ProjectUsingM31()
      throws VerificationException, IOException
  {
    roundtrip(createMavenVerifier(M31_VERSION, new File(getBasedir(),
        "target/test-classes/maven2-maven-plugin-project")));
  }

  /**
   * Maven Plugin Project set up in m2-way with m3.
   */
  @Test
  public void roundtripWithM2ProjectUsingM32()
      throws VerificationException, IOException
  {
    roundtrip(createMavenVerifier(M32_VERSION, new File(getBasedir(),
        "target/test-classes/maven2-maven-plugin-project")));
  }

  /**
   * Maven Plugin Project set up in m3-way using m3.
   */
  @Test
  public void roundtripWithM3ProjectUsingM30()
      throws VerificationException, IOException
  {
    roundtrip(createMavenVerifier(M30_VERSION, new File(getBasedir(),
        "target/test-classes/maven3-maven-plugin-project")));
  }

  /**
   * Maven Plugin Project set up in m3-way using m3.
   */
  @Test
  public void roundtripWithM3ProjectUsingM31()
      throws VerificationException, IOException
  {
    roundtrip(createMavenVerifier(M31_VERSION, new File(getBasedir(),
        "target/test-classes/maven3-maven-plugin-project")));
  }

  /**
   * Maven Plugin Project set up in m3-way using m3.
   */
  @Test
  public void roundtripWithM3ProjectUsingM32()
      throws VerificationException, IOException
  {
    roundtrip(createMavenVerifier(M32_VERSION, new File(getBasedir(),
        "target/test-classes/maven3-maven-plugin-project")));
  }

  // we always invoke the same, but results will be different: with deferred deploy
  // they will land into snapshots, with staging they will land in some
  // (closed) staging repo. That's why we have getTargetedRepositoryId() that will
  // tell us from where to fetch the G level MD

  @Override
  protected void invokeMaven(final PreparedVerifier verifier)
      throws VerificationException
  {
    // the workflow
    verifier.addCliOption("-DbuildPromotionProfileId=13051056fe421cf8"); // see preset config
    verifier.executeGoals(Arrays.asList("clean", "deploy", "nexus-staging:promote"));
    // should not fail
    verifier.verifyErrorFreeLog();
  }

  // == Scenario specific methods

  @Override
  protected abstract PreparedVerifier createMavenVerifier(final String mavenVersion, final File projectDirectory)
      throws VerificationException, IOException;

  // == Assertions

  @Override
  protected void preNexusAssertions(final PreparedVerifier verifier) {
    assertThatAllExistingStagingRepositoriesAreGrouped();
  }

  @Override
  protected void postNexusAssertions(final PreparedVerifier verifier) {
    try {
      // verify promotion actually did happen
      // we need to have ONE grouped state repo (as it seems client does not return promotion groups?)
      // As seen from "[StagingRepository [id=test1-1000, state=GROUPED]]"
      // FIXME: why does client omits groups?
      assertThatAllExistingStagingRepositoriesAreGrouped();
      // check the log, it should contain log line telling the group ID
      @SuppressWarnings("unchecked")
      List<String> lines = verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false);
      String promotionLogLine = null;
      for (String line : lines) {
        // if client would not get ID this message below would be different > FAIL
        if (line.contains("Promoted, created promotion group with ID ")) {
          promotionLogLine = line;
          break;
        }
      }
      assertThat("Promotion log line not found", promotionLogLine, notNullValue());
    }
    catch (VerificationException e) {
      Throwables.propagate(e);
    }
  }

  private void assertThatAllExistingStagingRepositoriesAreGrouped() {
    // as we have 3 @Tests above, and they iterate over same instance without cleaning up
    // we need to assert one thing, either there is 0 staging reposes, or all of them are in
    // State.GROUPED
    // FIXME: why does the client neglect promotion groups? This list contains
    // only reposes, not groups (as debugged)!
    final List<StagingRepository> allStagingRepositories = getAllStagingRepositories();
    if (allStagingRepositories != null && !allStagingRepositories.isEmpty()) {
      for (StagingRepository stagingRepository : allStagingRepositories) {
        assertThat("Staging repository not grouped: " + stagingRepository, stagingRepository.getState(),
            equalTo(State.GROUPED));
      }
    }
  }
}
