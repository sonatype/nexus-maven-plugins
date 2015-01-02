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
package org.sonatype.nexus.maven.staging;

import java.util.Map;

import com.google.common.base.Strings;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Simple message source for all staging steps.
 *
 * @author cstamas
 * @since 1.4
 */
public class StagingActionMessages
{
  private final String stagingDescription;

  private final Map<StagingAction, String> stagingDescriptions;

  private final String defaultDescription;

  /**
   * Constructor.
   */
  public StagingActionMessages(final String stagingDescription,
                               final Map<StagingAction, String> stagingDescriptions, final String defaultDescription)
  {
    this.stagingDescription = stagingDescription;
    this.stagingDescriptions = checkNotNull(stagingDescriptions);
    this.defaultDescription = checkNotNull(defaultDescription);
  }

  /**
   * Returns the message corresponding to given action.
   *
   * @return message, never {@code null}.
   */
  public String getMessageForAction(final StagingAction action) {
    checkNotNull(action);
    if (!Strings.isNullOrEmpty(stagingDescription)) {
      return stagingDescription;
    }
    if (stagingDescriptions.containsKey(action)) {
      return stagingDescriptions.get(action);
    }
    return defaultDescription;
  }
}
