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

import java.util.Locale;

import com.google.common.base.Strings;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Execution parameters, for connecting to remote Nexus.
 */
public class Parameters
{
  private final String nexusUrl;

  private final String serverId;

  private final String stagingProfileId;

  // tuning, with sensible defaults

  private int stagingProgressTimeoutMinutes = 5;

  private int stagingProgressPauseDurationSeconds = 3;

  private boolean sslInsecure = false;

  private boolean sslAllowAll = false;

  // flags

  private boolean keepStagingRepositoryOnCloseRuleFailure = true;

  private boolean keepStagingRepositoryOnFailure = false;

  private boolean skipStagingRepositoryClose = false;

  private boolean autoReleaseAfterClose = false;

  private boolean autoDropAfterRelease = true;

  public Parameters(final String nexusUrl, final String serverId, final String stagingProfileId) {
    checkArgument(!Strings.isNullOrEmpty(nexusUrl), "Mandatory plugin parameter 'nexusUrl' is missing");
    checkArgument(nexusUrl.toLowerCase(Locale.ENGLISH).startsWith("https") ||
            nexusUrl.toLowerCase(Locale.ENGLISH).startsWith("http"),
        "Mandatory plugin parameter 'nexusUrl' must start with http or https");
    checkArgument(!nexusUrl.contains("/service/local/") && !nexusUrl.contains("/content/repositories/"),
        "Mandatory plugin parameter 'nexusUrl' should be your Nexus base URL only - for example" +
            " http://localhost:8081/nexus"
    );
    checkArgument(!Strings.isNullOrEmpty(serverId), "Mandatory plugin parameter 'serverId' is missing");
    checkArgument(!Strings.isNullOrEmpty(stagingProfileId), "Mandatory plugin parameter 'stagingProfileId' is missing");

    this.nexusUrl = nexusUrl;
    this.serverId = serverId;
    this.stagingProfileId = stagingProfileId;
  }

  public String getNexusUrl() {
    return nexusUrl;
  }

  public String getServerId() {
    return serverId;
  }

  public String getStagingProfileId() {
    return stagingProfileId;
  }

  public int getStagingProgressTimeoutMinutes() {
    return stagingProgressTimeoutMinutes;
  }

  public void setStagingProgressTimeoutMinutes(final int stagingProgressTimeoutMinutes) {
    this.stagingProgressTimeoutMinutes = stagingProgressTimeoutMinutes;
  }

  public int getStagingProgressPauseDurationSeconds() {
    return stagingProgressPauseDurationSeconds;
  }

  public void setStagingProgressPauseDurationSeconds(final int stagingProgressPauseDurationSeconds) {
    this.stagingProgressPauseDurationSeconds = stagingProgressPauseDurationSeconds;
  }

  public boolean isSslInsecure() {
    return sslInsecure;
  }

  public void setSslInsecure(final boolean sslInsecure) {
    this.sslInsecure = sslInsecure;
  }

  public boolean isSslAllowAll() {
    return sslAllowAll;
  }

  public void setSslAllowAll(final boolean sslAllowAll) {
    this.sslAllowAll = sslAllowAll;
  }

  public boolean isKeepStagingRepositoryOnCloseRuleFailure() {
    return keepStagingRepositoryOnCloseRuleFailure;
  }

  public void setKeepStagingRepositoryOnCloseRuleFailure(final boolean keepStagingRepositoryOnCloseRuleFailure) {
    this.keepStagingRepositoryOnCloseRuleFailure = keepStagingRepositoryOnCloseRuleFailure;
  }

  public boolean isKeepStagingRepositoryOnFailure() {
    return keepStagingRepositoryOnFailure;
  }

  public void setKeepStagingRepositoryOnFailure(final boolean keepStagingRepositoryOnFailure) {
    this.keepStagingRepositoryOnFailure = keepStagingRepositoryOnFailure;
  }

  public boolean isSkipStagingRepositoryClose() {
    return skipStagingRepositoryClose;
  }

  public void setSkipStagingRepositoryClose(final boolean skipStagingRepositoryClose) {
    this.skipStagingRepositoryClose = skipStagingRepositoryClose;
  }

  public boolean isAutoReleaseAfterClose() {
    return autoReleaseAfterClose;
  }

  public void setAutoReleaseAfterClose(final boolean autoReleaseAfterClose) {
    this.autoReleaseAfterClose = autoReleaseAfterClose;
  }

  public boolean isAutoDropAfterRelease() {
    return autoDropAfterRelease;
  }

  public void setAutoDropAfterRelease(final boolean autoDropAfterRelease) {
    this.autoDropAfterRelease = autoDropAfterRelease;
  }
}
