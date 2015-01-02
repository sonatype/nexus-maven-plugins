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
package org.sonatype.nexus.maven.staging.zapper;

import java.io.File;

/**
 * A "zap" request.
 *
 * @author cstamas
 * @since 1.0
 */
public class ZapperRequest
{
  private final File stageRepository;

  private final String remoteUrl;

  private String remoteUsername;

  private String remotePassword;

  private String proxyProtocol;

  private String proxyHost;

  private int proxyPort;

  private String proxyUsername;

  private String proxyPassword;

  public ZapperRequest(File stageRepository, String remoteUrl) {
    this.stageRepository = stageRepository;
    this.remoteUrl = remoteUrl.endsWith("/") ? remoteUrl : remoteUrl + "/";
  }

  public String getRemoteUsername() {
    return remoteUsername;
  }

  public void setRemoteUsername(String remoteUsername) {
    this.remoteUsername = remoteUsername;
  }

  public String getRemotePassword() {
    return remotePassword;
  }

  public void setRemotePassword(String remotePassword) {
    this.remotePassword = remotePassword;
  }

  public String getProxyProtocol() {
    return proxyProtocol;
  }

  public void setProxyProtocol(String proxyProtocol) {
    this.proxyProtocol = proxyProtocol;
  }

  public String getProxyHost() {
    return proxyHost;
  }

  public void setProxyHost(String proxyHost) {
    this.proxyHost = proxyHost;
  }

  public int getProxyPort() {
    return proxyPort;
  }

  public void setProxyPort(int proxyPort) {
    this.proxyPort = proxyPort;
  }

  public String getProxyUsername() {
    return proxyUsername;
  }

  public void setProxyUsername(String proxyUsername) {
    this.proxyUsername = proxyUsername;
  }

  public String getProxyPassword() {
    return proxyPassword;
  }

  public void setProxyPassword(String proxyPassword) {
    this.proxyPassword = proxyPassword;
  }

  public File getStageRepository() {
    return stageRepository;
  }

  public String getRemoteUrl() {
    return remoteUrl;
  }
}
