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
package org.sonatype.nexus.maven.staging.it.nxcm4527;

import java.util.Arrays;

import org.sonatype.nexus.maven.staging.it.PreparedVerifier;

import junit.framework.Assert;
import org.apache.maven.it.VerificationException;

/**
 * See NXCM-4527, this IT implements it's verification part for Nexus Staging Maven Plugin side. Here, we build and
 * deploy/stage a "malformed" project (will lack the javadoc JAR, achieved by passing in "-Dmaven.javadoc.skip=true"
 * during
 * deploy). The project uses default settings for nexus-staging-maven-plugin, so such malformed staged project should
 * have the staging repository dropped upon rule failure. Hence, {@link #postNexusAssertions(PreparedVerifier)}
 * contains
 * checks that there are no staging repositories but also that the artifact built is not released either.
 *
 * @author cstamas
 */
public class NXCM4527DropOnCloseRuleFailureIT
    extends NXCM4527Support
{

  public NXCM4527DropOnCloseRuleFailureIT(final String nexusBundleCoordinates) {
    super(nexusBundleCoordinates);
  }

  /**
   * Validates nexus side of affairs post maven invocations.
   */
  @Override
  protected void postNexusAssertions(final PreparedVerifier verifier) {
    assertDefaults(verifier);
  }

  @Override
  protected void invokeMaven(final PreparedVerifier verifier)
      throws VerificationException
  {
    try {
      // skip javadoc, we want failing build
      verifier.addCliOption("-Dmaven.javadoc.skip=true");
      // v2 workflow
      verifier.executeGoals(Arrays.asList("clean", "deploy"));
      // should fail
      verifier.verifyErrorFreeLog();
      // if no exception, fail the test
      Assert.fail("We should end up with failed remote staging!");
    }
    catch (VerificationException e) {
      // good
    }
  }
}