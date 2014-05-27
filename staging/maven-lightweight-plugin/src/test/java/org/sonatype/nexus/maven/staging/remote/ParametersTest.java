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

package org.sonatype.nexus.maven.staging.remote;

import org.sonatype.sisu.litmus.testsupport.TestSupport;

import org.junit.Test;

public class ParametersTest
    extends TestSupport
{
  Parameters params;

  @Test(expected = IllegalArgumentException.class)
  public void validateRemotingMissingNexusUrl() {
    params = new Parameters("", "nexus", "profile");
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateRemotingNexusUrlNoService() {
    params = new Parameters("http://localhost:8081/nexus", "", "profile");
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateRemotingNexusUrlNoContent() {
    params = new Parameters("http://localhost:8081/nexus/content/repositories/snapshots", "", "profile");
  }

  @Test
  public void validateRemotingIsValid() {
    params = new Parameters("http://localhost:8081/nexus", "nexus", "profile");
  }

  @Test
  public void validateRemotingNexusUrlSlash() {
    params = new Parameters("http://localhost:8081/nexus/", "nexus", "profile");
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateRemotingNexusUrlHttpOnly() {
    params = new Parameters("scp://localhost:8081/nexus/", "nexus", "profile");
  }

  @Test
  public void validateRemotingNexusUrlHttpsAllowed() {
    params = new Parameters("https://localhost:8081/nexus/", "nexus", "profile");
  }

  @Test
  public void validateRemotingNexusUrlIgnoreCase() {
    params = new Parameters("HTTPS://localhost:8081/nexus/", "nexus", "profile");
  }
}