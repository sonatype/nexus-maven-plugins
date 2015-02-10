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

package org.sonatype.nexus.maven.staging.it.nexus8065;

import com.sonatype.nexus.staging.client.StagingRepository;
import junit.framework.Assert;
import org.apache.maven.it.VerificationException;
import org.junit.Test;
import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.multiprofile.MultiprofileITSupport;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * IT that Verifies deploying to multi nexus instances, where a subsequent staged repository close operation fails. In these cases -
 * set of staging repositories coming from ONE REACTOR - if build fails due to rule failure, all created staging
 * repositories should be dropped by same guidelines we drop the one repository that failed:
 * "user will start another build, and new ones will be created", but the ones not dropped will remain dangling.
 * Failure is achieved by activating a profile, that makes 2nd module produce an invalid build (lacking javadoc).
 *
 * Copied from @{link MultiprofileFailureV2RoundtripIT}. Only change the test maven project
 */
public class MultiNexusFailureV2RoundtripIT
    extends MultiprofileITSupport
{
  public MultiNexusFailureV2RoundtripIT(final String nexusBundleCoordinates) {
    super(nexusBundleCoordinates);
  }

  /**
   * Using "deploy".
   */
  public void roundtripWithM3MultinexusProjectUsingM3Deploy(final String mavenVersion)
      throws VerificationException, IOException
  {
    final PreparedVerifier verifier =
        createMavenVerifier(getClass().getSimpleName() + "_roundtripWithM3MultinexusProjectUsingM3Deploy",
            mavenVersion, testData().resolveFile("preset-nexus-maven-settings.xml"), new File(getBasedir(),
                "target/test-classes/maven3-multinexus-project"));

    try {
      // skip javadoc, we want failing build but in m2
      verifier.addCliOption("-Pskip-javadoc");
      // v2 workflow
      verifier.executeGoals(Arrays.asList("clean", "deploy"));
      // should fail
      verifier.verifyErrorFreeLog();
      // foolproof the failure
      Assert.fail("We should not get here, close at the end of deploy should fail!");
    }
    catch (VerificationException e) {
      // good
    }

    // perform some checks
    {
      final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
      if (!stagingRepositories.isEmpty()) {
        Assert.fail("Nexus should have 0 staging repositories, but it has: " + stagingRepositories);
      }
    }
  }

  /**
   * Using "deploy".
   */
  @Test
  public void roundtripWithM3MultinexusProjectUsingM30Deploy()
      throws VerificationException, IOException
  {
    roundtripWithM3MultinexusProjectUsingM3Deploy(M30_VERSION);
  }

  /**
   * Using "deploy".
   */
  @Test
  public void roundtripWithM3MultinexusProjectUsingM31Deploy()
      throws VerificationException, IOException
  {
    roundtripWithM3MultinexusProjectUsingM3Deploy(M31_VERSION);
  }

  /**
   * Using "deploy".
   */
  @Test
  public void roundtripWithM3MultinexusProjectUsingM32Deploy()
      throws VerificationException, IOException
  {
    roundtripWithM3MultinexusProjectUsingM3Deploy(M32_VERSION);
  }

  /**
   * Using "close" build action.
   */
  public void roundtripWithM3MultinexusProjectUsingM3BuildActionClose(final String mavenVersion)
      throws VerificationException, IOException
  {
    final PreparedVerifier verifier =
        createMavenVerifier(getClass().getSimpleName()
                + "_roundtripWithM3MultinexusProjectUsingM3BuildActionClose", mavenVersion,
            testData().resolveFile("preset-nexus-maven-settings.xml"), new File(getBasedir(),
                "target/test-classes/maven3-multinexus-project"));

    try {
      // skip javadoc, we want failing build but in m2
      verifier.addCliOption("-Pskip-javadoc");
      // we want to test the "close" build action here
      verifier.addCliOption("-DskipStagingRepositoryClose=true");
      // v2 workflow
      verifier.executeGoals(Arrays.asList("clean", "deploy"));
      // should not fail
      verifier.verifyErrorFreeLog();
      // build action: close, will fail
      verifier.executeGoals(Arrays.asList("nexus-staging:close"));
      // should fail
      verifier.verifyErrorFreeLog();
      // foolproof the failure
      Assert.fail("We should not get here, close at the end of deploy should fail!");
    }
    catch (VerificationException e) {
      // good
    }

    // perform some checks
    {
      final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
      if (!stagingRepositories.isEmpty()) {
        Assert.fail("Nexus should have 0 staging repositories, but it has: " + stagingRepositories);
      }
    }
  }

  /**
   * Using "close" build action.
   */
  @Test
  public void roundtripWithM3MultinexusProjectUsingM30BuildActionClose()
      throws VerificationException, IOException
  {
    roundtripWithM3MultinexusProjectUsingM3BuildActionClose(M30_VERSION);
  }

  /**
   * Using "close" build action.
   */
  @Test
  public void roundtripWithM3MultinexusProjectUsingM31BuildActionClose()
      throws VerificationException, IOException
  {
    roundtripWithM3MultinexusProjectUsingM3BuildActionClose(M31_VERSION);
  }

  /**
   * Using "close" build action.
   */
  @Test
  public void roundtripWithM3MultinexusProjectUsingM32BuildActionClose()
      throws VerificationException, IOException
  {
    roundtripWithM3MultinexusProjectUsingM3BuildActionClose(M32_VERSION);
  }
}
