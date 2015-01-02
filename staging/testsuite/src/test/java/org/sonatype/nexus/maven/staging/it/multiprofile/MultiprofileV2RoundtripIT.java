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
package org.sonatype.nexus.maven.staging.it.multiprofile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.sonatype.nexus.staging.client.StagingRepository;
import com.sonatype.nexus.staging.client.StagingRepository.State;

import org.sonatype.nexus.maven.staging.it.PreparedVerifier;

import junit.framework.Assert;
import org.apache.maven.it.VerificationException;
import org.junit.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * IT that "implements" the Staging V2 testing guide's "multi profile" scenario followed by the "release" Post Staging
 * Steps section.
 *
 * @author cstamas
 * @see https://docs.sonatype.com/display/Nexus/Staging+V2+Testing
 */
public class MultiprofileV2RoundtripIT
    extends MultiprofileITSupport
{
  public MultiprofileV2RoundtripIT(final String nexusBundleCoordinates) {
    super(nexusBundleCoordinates);
  }

  /**
   * Using "deploy".
   */
  public void roundtripWithM3MultiprofileProjectUsingM3Deploy(final String mavenVersion)
      throws VerificationException, IOException
  {
    final PreparedVerifier verifier =
        createMavenVerifier(getClass().getSimpleName() + "_roundtripWithM3MultiprofileProjectUsingM3Deploy",
            mavenVersion, testData().resolveFile("preset-nexus-maven-settings.xml"), new File(getBasedir(),
                "target/test-classes/maven3-multiprofile-project"));

    // v2 workflow
    verifier.executeGoals(Arrays.asList("clean", "deploy"));
    // should not fail
    verifier.verifyErrorFreeLog();

    // perform some checks
    {
      final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
      if (stagingRepositories.isEmpty()) {
        Assert.fail("Nexus should have 2 staging repositories, but it has none!");
      }
      Assert.assertEquals("Nexus should have 2 staging repository, the ones of the current build", 2,
          stagingRepositories.size());
      Assert.assertEquals("Staging repository should be closed!", StagingRepository.State.CLOSED,
          stagingRepositories.get(0).getState());
      Assert.assertEquals("Staging repository should be closed!", StagingRepository.State.CLOSED,
          stagingRepositories.get(1).getState());
    }

    // v2 release
    verifier.executeGoals(Arrays.asList("nexus-staging:release"));
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

      // stuff we staged are released and found by indexer
      // TODO: this "assertion" is disabled for now as it shows as highly unreliable
      // final SearchResponse searchResponse =
      // searchThreeTimesForGAV( verifier.getProjectGroupId(), "m1", verifier.getProjectVersion(), "sources",
      // "jar", "releases" );
      // if ( searchResponse.getHits().isEmpty() )
      // {
      // Assert.fail( String.format(
      // "Nexus should have staged artifact in releases repository with GAV=%s:%s:%s but those are not found on index!",
      // verifier.getProjectGroupId(), verifier.getProjectArtifactId(), verifier.getProjectVersion() ) );
      // }
      // Assert.assertEquals( "We deployed 1 module of this GAV but none or more was found!", 1,
      // searchResponse.getHits().size() );
    }
  }

  /**
   * Using "deploy".
   */
  @Test
  public void roundtripWithM3MultiprofileProjectUsingM30Deploy()
      throws VerificationException, IOException
  {
    roundtripWithM3MultiprofileProjectUsingM3Deploy(M30_VERSION);
  }

  /**
   * Using "deploy".
   */
  @Test
  public void roundtripWithM3MultiprofileProjectUsingM31Deploy()
      throws VerificationException, IOException
  {
    roundtripWithM3MultiprofileProjectUsingM3Deploy(M31_VERSION);
  }

  /**
   * Using "deploy".
   */
  @Test
  public void roundtripWithM3MultiprofileProjectUsingM32Deploy()
      throws VerificationException, IOException
  {
    roundtripWithM3MultiprofileProjectUsingM3Deploy(M32_VERSION);
  }

  /**
   * Using "close" build action.
   */
  public void roundtripWithM3MultiprofileProjectUsingM3BuildActionClose(final String mavenVersion)
      throws VerificationException, IOException
  {
    final PreparedVerifier verifier =
        createMavenVerifier(getClass().getSimpleName()
                + "_roundtripWithM3MultiprofileProjectUsingM3BuildActionClose", mavenVersion,
            testData().resolveFile("preset-nexus-maven-settings.xml"), new File(getBasedir(),
                "target/test-classes/maven3-multiprofile-project"));

    // we want to test the "close" build action here
    verifier.addCliOption("-DskipStagingRepositoryClose=true");
    // v2 workflow
    verifier.executeGoals(Arrays.asList("clean", "deploy"));
    // should not fail
    verifier.verifyErrorFreeLog();
    // build action: close, should not fail
    verifier.executeGoals(Arrays.asList("nexus-staging:close"));
    // should not fail
    verifier.verifyErrorFreeLog();

    // perform some checks
    {
      final List<StagingRepository> stagingRepositories = getAllStagingRepositories();
      if (stagingRepositories.isEmpty()) {
        Assert.fail("Nexus should have 2 staging repositories, but it has none!");
      }
      Assert.assertEquals("Nexus should have 2 staging repository, the ones of the current build", 2,
          stagingRepositories.size());
      Assert.assertEquals("Staging repository should be closed!", StagingRepository.State.CLOSED,
          stagingRepositories.get(0).getState());
      Assert.assertEquals("Staging repository should be closed!", StagingRepository.State.CLOSED,
          stagingRepositories.get(1).getState());
    }

    // v2 release
    verifier.executeGoals(Arrays.asList("nexus-staging:release"));
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

      // stuff we staged are released and found by indexer
      // TODO: this "assertion" is disabled for now as it shows as highly unreliable
      // final SearchResponse searchResponse =
      // searchThreeTimesForGAV( verifier.getProjectGroupId(), "m1", verifier.getProjectVersion(), "sources",
      // "jar", "releases" );
      // if ( searchResponse.getHits().isEmpty() )
      // {
      // Assert.fail( String.format(
      // "Nexus should have staged artifact in releases repository with GAV=%s:%s:%s but those are not found on index!",
      // verifier.getProjectGroupId(), verifier.getProjectArtifactId(), verifier.getProjectVersion() ) );
      // }
      // Assert.assertEquals( "We deployed 1 module of this GAV but none or more was found!", 1,
      // searchResponse.getHits().size() );
    }
  }

  /**
   * Using "close" build action.
   */
  @Test
  public void roundtripWithM3MultiprofileProjectUsingM30BuildActionClose()
      throws VerificationException, IOException
  {
    roundtripWithM3MultiprofileProjectUsingM3BuildActionClose(M30_VERSION);
  }

  /**
   * Using "close" build action.
   */
  @Test
  public void roundtripWithM3MultiprofileProjectUsingM31BuildActionClose()
      throws VerificationException, IOException
  {
    roundtripWithM3MultiprofileProjectUsingM3BuildActionClose(M31_VERSION);
  }

  /**
   * Using "close" build action.
   */
  @Test
  public void roundtripWithM3MultiprofileProjectUsingM32BuildActionClose()
      throws VerificationException, IOException
  {
    roundtripWithM3MultiprofileProjectUsingM3BuildActionClose(M32_VERSION);
  }
}
