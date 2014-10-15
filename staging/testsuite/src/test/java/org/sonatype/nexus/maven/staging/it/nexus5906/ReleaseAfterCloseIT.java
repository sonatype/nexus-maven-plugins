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

package org.sonatype.nexus.maven.staging.it.nexus5906;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import com.sonatype.nexus.staging.client.StagingRepository;
import com.sonatype.nexus.staging.client.StagingRepository.State;

import org.sonatype.nexus.bundle.launcher.NexusBundleConfiguration;
import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.StagingMavenPluginITSupport;
import org.sonatype.nexus.testsuite.support.NexusStartAndStopStrategy;
import org.sonatype.nexus.testsuite.support.NexusStartAndStopStrategy.Strategy;
import org.sonatype.sisu.filetasks.FileTaskBuilder;

import org.apache.maven.it.VerificationException;
import org.junit.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.sonatype.sisu.filetasks.builder.FileRef.file;
import static org.sonatype.sisu.filetasks.builder.FileRef.path;

/**
 * NEXUS-5906: Release after close IT.
 *
 * @author cstamas
 * @since 1.5
 */
@NexusStartAndStopStrategy(Strategy.EACH_TEST)
public class ReleaseAfterCloseIT
    extends StagingMavenPluginITSupport
{
  @Inject
  private FileTaskBuilder fileTaskBuilder;

  public ReleaseAfterCloseIT(final String nexusBundleCoordinates) {
    super(nexusBundleCoordinates);
  }

  @Override
  protected NexusBundleConfiguration configureNexus(final NexusBundleConfiguration configuration) {
    // TODO: (cstamas) I promised to Alin to change this "old way of doing things" to use of REST API that would
    // configure Nexus properly once the Security and Staging Management Nexus Client subsystems are done.
    return super.configureNexus(configuration).addOverlays(
        fileTaskBuilder.copy().directory(file(testData().resolveFile("preset-nexus"))).to().directory(
            path("sonatype-work/nexus/conf")));
  }

  @Override
  protected List<String> getMavenVersions() {
    return Arrays.asList(M30_VERSION, M31_VERSION, M32_VERSION);
  }

  /**
   * Using "deploy" with -DreleaseAfterClose.
   */
  protected void releaseAfterClose(final String mavenVersion)
      throws VerificationException, IOException
  {
    final PreparedVerifier verifier =
        createMavenVerifier(getClass().getSimpleName() + "_releaseAfterClose",
            mavenVersion, testData().resolveFile("preset-nexus-maven-settings.xml"), new File(getBasedir(),
                "target/test-classes/maven3-multiprofile-project"));

    // plain v2 work-flow
    verifier.addCliOption("-DautoReleaseAfterClose=true");
    verifier.addCliOption("-DautoDropAfterRelease=false"); // we do not test auto drop, but is easier assertions
    verifier.executeGoals(Arrays.asList("clean", "deploy"));
    // should not fail
    verifier.verifyErrorFreeLog();

    // post execution Nexus side asserts
    {
      final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
      assertThat("Should have 2 'released' staging repositories",
          stagingRepositories, hasSize(2));

      // ensure both are released
      assertThat(stagingRepositories.get(0).getState(), is(State.RELEASED));
      assertThat(stagingRepositories.get(1).getState(), is(State.RELEASED));
    }
  }

  /**
   * Using "deploy" with -DreleaseAfterClose.
   */
  @Test
  public void releaseAfterCloseM30()
      throws VerificationException, IOException
  {
    releaseAfterClose(M30_VERSION);
  }

  /**
   * Using "deploy" with -DreleaseAfterClose.
   */
  @Test
  public void releaseAfterCloseM31()
      throws VerificationException, IOException
  {
    releaseAfterClose(M31_VERSION);
  }

  /**
   * Using "deploy" with -DreleaseAfterClose.
   */
  @Test
  public void releaseAfterCloseM32()
      throws VerificationException, IOException
  {
    releaseAfterClose(M32_VERSION);
  }
}
