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
package org.sonatype.nexus.maven.staging.remote;

import java.io.File;
import java.util.Locale;
import java.util.Map;

import org.sonatype.nexus.maven.staging.StagingAction;
import org.sonatype.nexus.maven.staging.StagingActionMessages;

import com.google.common.base.Strings;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Execution parameters, mostly coming from Mojo parameters.
 *
 * @author cstamas
 */
public class Parameters
{
  // == minimal requirement, always present

  private final String pluginGav;

  private final File deferredDirectoryRoot;

  private final File stagingDirectoryRoot;

  // == present only if staging involved

  private String nexusUrl;

  private String serverId;

  private boolean keepStagingRepositoryOnCloseRuleFailure;

  private boolean keepStagingRepositoryOnFailure;

  private boolean skipStagingRepositoryClose;

  private boolean autoReleaseAfterClose;

  private boolean autoDropAfterRelease;

  private String stagingProfileId;

  private String stagingRepositoryId;

  private StagingActionMessages stagingActionMessages;

  private Map<String, String> tags;

  private int stagingProgressTimeoutMinutes;

  private int stagingProgressPauseDurationSeconds;

  private boolean sslInsecure;

  private boolean sslAllowAll;

  /**
   * Ctor, validates the minimal set of required parameters.
   */
  public Parameters(final String pluginGav, final File deferredDirectoryRoot, final File stagingDirectoryRoot) {
    this.pluginGav = checkNotNull(pluginGav, "Plugin GAV is null");
    this.deferredDirectoryRoot = checkNotNull(deferredDirectoryRoot, "Deferred directory root is null");
    this.stagingDirectoryRoot = checkNotNull(stagingDirectoryRoot, "Staging directory root is null");
  }

  /**
   * Ivokes validation of basic/minimal parameters.
   */
  public void validateBasic() {
    // nothing, ctor is okay for now
  }

  /**
   * Invokes validation of transport parameters.
   */
  public void validateRemoting() {
    validateBasic();
    checkArgument(!Strings.isNullOrEmpty(nexusUrl), "Mandatory plugin parameter 'nexusUrl' is missing");
    checkArgument(nexusUrl.toLowerCase(Locale.ENGLISH).startsWith("https") ||
            nexusUrl.toLowerCase(Locale.ENGLISH).startsWith("http"),
        "Mandatory plugin parameter 'nexusUrl' must start with http or https");
    checkArgument(!nexusUrl.contains("/service/local/") && !nexusUrl.contains("/content/repositories/"),
        "Mandatory plugin parameter 'nexusUrl' should be your Nexus base URL only - for example" +
            " http://localhost:8081/nexus"
    );
    checkArgument(!Strings.isNullOrEmpty(serverId), "Mandatory plugin parameter 'serverId' is missing");
  }

  /**
   * Invokes validation of staging parameters, when staging is involved.
   */
  public void validateStaging() {
    validateRemoting();
    checkNotNull(stagingActionMessages, "Staging action messages is null");
  }

  public String getPluginGav() {
    return pluginGav;
  }

  public File getDeferredDirectoryRoot() {
    return deferredDirectoryRoot;
  }

  public File getStagingDirectoryRoot() {
    return stagingDirectoryRoot;
  }

  // ==

  public String getActionDescription(final StagingAction action) {
    return stagingActionMessages.getMessageForAction(action);
  }

  // ==

  public String getNexusUrl() {
    return nexusUrl;
  }

  public void setNexusUrl(final String nexusUrl) {
    this.nexusUrl = nexusUrl;
  }

  public String getServerId() {
    return serverId;
  }

  public void setServerId(final String serverId) {
    this.serverId = serverId;
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

  public String getStagingProfileId() {
    return stagingProfileId;
  }

  public void setStagingProfileId(final String stagingProfileId) {
    this.stagingProfileId = stagingProfileId;
  }

  public String getStagingRepositoryId() {
    return stagingRepositoryId;
  }

  public void setStagingRepositoryId(final String stagingRepositoryId) {
    this.stagingRepositoryId = stagingRepositoryId;
  }

  public StagingActionMessages getStagingActionMessages() {
    return stagingActionMessages;
  }

  public void setStagingActionMessages(final StagingActionMessages stagingActionMessages) {
    this.stagingActionMessages = stagingActionMessages;
  }

  public Map<String, String> getTags() {
    return tags;
  }

  public void setTags(final Map<String, String> tags) {
    this.tags = tags;
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

  // ==

  @Override
  public String toString() {
    return "Parameters{" +
        "pluginGav='" + pluginGav + '\'' +
        ", deferredDirectoryRoot=" + deferredDirectoryRoot +
        ", stagingDirectoryRoot=" + stagingDirectoryRoot +
        ", nexusUrl='" + nexusUrl + '\'' +
        ", serverId='" + serverId + '\'' +
        ", keepStagingRepositoryOnCloseRuleFailure=" + keepStagingRepositoryOnCloseRuleFailure +
        ", keepStagingRepositoryOnFailure=" + keepStagingRepositoryOnFailure +
        ", skipStagingRepositoryClose=" + skipStagingRepositoryClose +
        ", autoReleaseAfterClose=" + autoReleaseAfterClose +
        ", autoDropAfterRelease=" + autoDropAfterRelease +
        ", stagingProfileId='" + stagingProfileId + '\'' +
        ", stagingRepositoryId='" + stagingRepositoryId + '\'' +
        ", stagingActionMessages=" + stagingActionMessages +
        ", tags=" + tags +
        ", stagingProgressTimeoutMinutes=" + stagingProgressTimeoutMinutes +
        ", stagingProgressPauseDurationSeconds=" + stagingProgressPauseDurationSeconds +
        ", sslInsecure=" + sslInsecure +
        ", sslAllowAll=" + sslAllowAll +
        '}';
  }
}
