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

import java.io.File;

import org.sonatype.sisu.litmus.testsupport.TestSupport;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class ParametersTest
    extends TestSupport
{
  Parameters params;

  @Mock
  File file;

  @Before
  public void setUp() {
    params = new Parameters("", file, file);
    // valid values by default
    params.setServerId("nexus");
    params.setNexusUrl("http://localhost:8081/nexus");
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateRemotingMissingNexusUrl() {
    params.setNexusUrl("");
    params.validateRemoting();
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateRemotingNexusUrlNoService() {
    params.setNexusUrl("http://localhost:8081/nexus/service/local/staging");
    params.validateRemoting();
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateRemotingNexusUrlNoContent() {
    params.setNexusUrl("http://localhost:8081/nexus/content/repositories/snapshots");
    params.validateRemoting();
  }

  @Test
  public void validateRemotingIsValid() {
    params.validateRemoting();
  }

  @Test
  public void validateRemotingNexusUrlSlash() {
    params.setNexusUrl("http://localhost:8081/nexus/");
    params.validateRemoting();
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateRemotingNexusUrlHttpOnly() {
    params.setNexusUrl("scp://localhost:8081/nexus");
    params.validateRemoting();
  }

  @Test
  public void validateRemotingNexusUrlHttpsAllowed() {
    params.setNexusUrl("https://localhost:8081/nexus");
    params.validateRemoting();
  }

  @Test
  public void validateRemotingNexusUrlIgnoreCase() {
    params.setNexusUrl("HTTP://localhost:8081/nexus");
    params.validateRemoting();
  }

}