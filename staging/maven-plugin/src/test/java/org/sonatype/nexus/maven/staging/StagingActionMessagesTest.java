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

package org.sonatype.nexus.maven.staging;

import java.util.Collections;
import java.util.HashMap;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class StagingActionMessagesTest
{
  @Test(expected = NullPointerException.class)
  public void constructorNpe() {
    final StagingActionMessages subject =
        new StagingActionMessages(null, Collections.<StagingAction, String>emptyMap(), null);
  }

  @Test(expected = NullPointerException.class)
  public void getMessageForActionNpe() {
    final StagingActionMessages subject =
        new StagingActionMessages(null, Collections.<StagingAction, String>emptyMap(), "default");
    subject.getMessageForAction(null);
  }

  @Test
  public void takesAll() {
    final StagingActionMessages subject =
        new StagingActionMessages("takes all", Collections.<StagingAction, String>emptyMap(), "default");

    assertThat(subject.getMessageForAction(StagingAction.DROP), equalTo("takes all"));
  }

  @Test
  public void usualUsecase() {
    final HashMap<StagingAction, String> messages = new HashMap<StagingAction, String>();
    messages.put(StagingAction.START, StagingAction.START.name());
    messages.put(StagingAction.FINISH, StagingAction.FINISH.name());

    final StagingActionMessages subject = new StagingActionMessages(null, messages, "default");

    assertThat(subject.getMessageForAction(StagingAction.START), equalTo(StagingAction.START.name()));
    assertThat(subject.getMessageForAction(StagingAction.FINISH), equalTo(StagingAction.FINISH.name()));
    assertThat(subject.getMessageForAction(StagingAction.DROP), equalTo("default"));
    assertThat(subject.getMessageForAction(StagingAction.PROMOTE), equalTo("default"));
    assertThat(subject.getMessageForAction(StagingAction.RELEASE), equalTo("default"));
  }
}
