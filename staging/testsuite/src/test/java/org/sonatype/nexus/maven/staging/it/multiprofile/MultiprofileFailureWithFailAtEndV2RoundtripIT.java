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

package org.sonatype.nexus.maven.staging.it.multiprofile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import com.sonatype.nexus.staging.client.StagingRepository;

import org.sonatype.nexus.bundle.launcher.NexusBundleConfiguration;
import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.StagingMavenPluginITSupport;
import org.sonatype.nexus.testsuite.support.NexusStartAndStopStrategy;
import org.sonatype.sisu.filetasks.FileTaskBuilder;

import junit.framework.Assert;
import org.apache.maven.it.VerificationException;
import org.junit.Test;

import static org.sonatype.nexus.testsuite.support.NexusStartAndStopStrategy.Strategy.EACH_METHOD;
import static org.sonatype.sisu.filetasks.builder.FileRef.file;
import static org.sonatype.sisu.filetasks.builder.FileRef.path;

/**
 * IT that Verifies multi module build along with CLI "-fae" switch, and asserts that staging plugin
 * does sense and detects previously happened build errors (as due to "fae" it will be invoked even
 * after a failure). Failure is achieved by activating a profile, that makes 2nd module produce an invalid build
 * (lacking javadoc).
 */
public class MultiprofileFailureWithFailAtEndV2RoundtripIT
    extends MultiprofileITSupport
{
  public MultiprofileFailureWithFailAtEndV2RoundtripIT(final String nexusBundleCoordinates) {
    super(nexusBundleCoordinates);
  }

  /**
   * Using "deploy".
   */
  @Test
  public void roundtripWithM3AndFae()
      throws VerificationException, IOException
  {
    final PreparedVerifier verifier =
        createMavenVerifier(getClass().getSimpleName() + "_roundtripWithM3MultiprofileProjectUsingM3Deploy",
            M3_VERSION, testData().resolveFile("preset-nexus-maven-settings.xml"), new File(getBasedir(),
            "target/test-classes/maven3-multiprofile-project"));

    try {
      // add FAE
      verifier.addCliOption("-fae");
      // skip javadoc, we want failing build but in m2
      verifier.addCliOption("-Pmake-build-break");
      // v2 workflow
      verifier.executeGoals(Arrays.asList("clean", "deploy"));
      // should fail
      verifier.verifyErrorFreeLog();
      // foolproof the failure
      Assert.fail("We should not get here, close at the end of deploy should fail!");
    }
    catch (VerificationException e) {
      // good, now verify did we detect the breakage at all?
      verifier.verifyTextInLog("Earlier build failures detected. Staging will not continue.");
    }

    // perform some checks, no remote staging should happen
    {
      final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
      if (!stagingRepositories.isEmpty()) {
        Assert.fail("Nexus should have 0 staging repositories, but it has: " + stagingRepositories);
      }
    }
  }
}
