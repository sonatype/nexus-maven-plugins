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
package org.sonatype.nexus.maven.staging.it.nexus6757;

import java.util.Arrays;
import java.util.List;

import com.sonatype.nexus.staging.client.StagingRepository;
import com.sonatype.nexus.staging.client.StagingRepository.State;

import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.SimpleRoundtripMatrixBaseTests;
import org.sonatype.nexus.mindexer.client.SearchResponse;

import org.apache.maven.it.VerificationException;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * IT that implements the workflow Staging V2 testing guide's "Two Shot" scenario followed by the "release" Post
 * Staging Steps section. It also "verifies" that a "matrix" of projects (set up in m2 or m3 way) and maven runtimes
 * (m2 and m3) all work as expected.
 *
 * @see <a href="https://github.com/sonatype/nexus-maven-plugins/blob/master/staging/maven-plugin/WORKFLOWS.md#two-shots">Two Shots Workflow</a>
 */
public class TwoPassV2RoundtripIT
    extends SimpleRoundtripMatrixBaseTests
{

  public TwoPassV2RoundtripIT(final String nexusBundleCoordinates) {
    super(nexusBundleCoordinates);
  }

  /**
   * Nothing to validate before hand.
   */
  @Override
  protected void preNexusAssertions(final PreparedVerifier verifier) {
  }

  /**
   * Validates nexus side of affairs post maven invocations.
   */
  @Override
  protected void postNexusAssertions(final PreparedVerifier verifier) {
    //
    // FIXME: Maybe add the drop after release to super test methods, and then leave no repositories as success
    //

    final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
    assertThat("Should have 1 'released' staging repositories",
        stagingRepositories, hasSize(1));

    StagingRepository repository = stagingRepositories.get(0);
    assertThat(repository.getState(), is(State.RELEASED));

    // drop the repository so the next test has empty repositories
    getStagingWorkflowV2Service().dropStagingRepositories("cleanup", repository.getId());

    // stuff we staged are released and found by indexer
    final SearchResponse searchResponse =
        searchWithRetriesForGAV(verifier.getProjectGroupId(), verifier.getProjectArtifactId(),
            verifier.getProjectVersion(), null, null, "releases");
    if (searchResponse.getHits().isEmpty()) {
      fail(String.format(
          "Nexus should have staged artifact in releases repository with GAV=%s:%s:%s but those are not found on index!",
          verifier.getProjectGroupId(), verifier.getProjectArtifactId(), verifier.getProjectVersion()));
    }
  }

  protected void verifyDescription() {
    final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
    assertThat("Should have 1 'released' staging repositories",
        stagingRepositories, hasSize(1));

    StagingRepository repository = stagingRepositories.get(0);
    assertThat(repository.getState(), is(State.CLOSED));

    // see corresponding POM for mapping:
    // maven2-project and maven3-project
    assertThat(repository.getDescription(), equalTo("finish"));
  }

  /**
   * Simulates separate invocation of commands. Deploy then release.
   */
  @Override
  protected void invokeMaven(final PreparedVerifier verifier)
      throws VerificationException
  {
    // v2 workflow: 1st "shot"
    verifier.addCliOption("-DskipRemoteStaging=true");
    verifier.executeGoals(Arrays.asList("clean", "deploy"));
    // should not fail
    verifier.verifyErrorFreeLog();
    // v2 workflow: 2nd "shot"
    // TODO: Not using this below to kill skipRemoteStaging, as that would bork Verifier
    // So, we are adding an "override" and last will prevail
    // verifier.getCliOptions().clear();
    verifier.addCliOption("-DskipRemoteStaging=false");
    verifier.executeGoals(Arrays.asList("nexus-staging:deploy-staged"));
    // should not fail
    verifier.verifyErrorFreeLog();
    // verify the description
    verifyDescription();
    // v2 release
    verifier.executeGoals(Arrays.asList("nexus-staging:release"));
    // should not fail
    verifier.verifyErrorFreeLog();
  }
}
