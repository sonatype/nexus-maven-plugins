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

import javax.annotation.Nullable;

import com.google.common.base.Strings;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Execution parameters, for connecting to remote Nexus.
 */
public class Parameters
{
  private final String nexusUrl;

  private final String serverId;

  // "optional" parts

  private String stagingProfileId;

  private String description;

  // connection tuning, with sensible defaults

  private int stagingProgressTimeoutMinutes = 5;

  private int stagingProgressPauseDurationSeconds = 3;

  private boolean sslInsecure = false;

  private boolean sslAllowAll = false;

  // behaviour flags

  private boolean keepStagingRepositoryOnRuleFailure = true;

  private boolean keepStagingRepositoryOnBuildFailure = false;

  private boolean autoReleaseAfterClose = false;

  private boolean autoDropAfterRelease = true;

  public Parameters(final String nexusUrl, final String serverId) {
    checkArgument(!Strings.isNullOrEmpty(nexusUrl), "Mandatory plugin parameter 'nexusUrl' is missing");
    checkArgument(nexusUrl.toLowerCase(Locale.ENGLISH).startsWith("https") ||
            nexusUrl.toLowerCase(Locale.ENGLISH).startsWith("http"),
        "Mandatory plugin parameter 'nexusUrl' must start with http or https");
    checkArgument(!nexusUrl.contains("/service/local/") && !nexusUrl.contains("/content/repositories/"),
        "Mandatory plugin parameter 'nexusUrl' should be your Nexus base URL only - for example" +
            " http://localhost:8081/nexus"
    );
    checkArgument(!Strings.isNullOrEmpty(serverId), "Mandatory plugin parameter 'serverId' is missing");

    this.nexusUrl = nexusUrl;
    this.serverId = serverId;
  }

  public String getNexusUrl() {
    return nexusUrl;
  }

  public String getServerId() {
    return serverId;
  }

  @Nullable
  public String getStagingProfileId() {
    return stagingProfileId;
  }

  public void setStagingProfileId(final @Nullable String stagingProfileId) {
    this.stagingProfileId = stagingProfileId;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  public void setDescription(final @Nullable String description) {
    this.description = description;
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

  public boolean isKeepStagingRepositoryOnRuleFailure() {
    return keepStagingRepositoryOnRuleFailure;
  }

  public void setKeepStagingRepositoryOnRuleFailure(final boolean keepStagingRepositoryOnRuleFailure) {
    this.keepStagingRepositoryOnRuleFailure = keepStagingRepositoryOnRuleFailure;
  }

  public boolean isKeepStagingRepositoryOnBuildFailure() {
    return keepStagingRepositoryOnBuildFailure;
  }

  public void setKeepStagingRepositoryOnBuildFailure(final boolean keepStagingRepositoryOnBuildFailure) {
    this.keepStagingRepositoryOnBuildFailure = keepStagingRepositoryOnBuildFailure;
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
