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

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import com.sonatype.nexus.staging.client.StagingWorkflowV2Service;
import com.sonatype.nexus.staging.client.StagingWorkflowV3Service;
import com.sonatype.nexus.staging.client.rest.JerseyStagingWorkflowV2SubsystemFactory;
import com.sonatype.nexus.staging.client.rest.JerseyStagingWorkflowV3SubsystemFactory;

import org.sonatype.maven.mojo.settings.MavenSettings;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.core.NexusStatus;
import org.sonatype.nexus.client.rest.BaseUrl;
import org.sonatype.nexus.client.rest.ConnectionInfo;
import org.sonatype.nexus.client.rest.ConnectionInfo.ValidationLevel;
import org.sonatype.nexus.client.rest.Protocol;
import org.sonatype.nexus.client.rest.ProxyInfo;
import org.sonatype.nexus.client.rest.UsernamePasswordAuthenticationInfo;
import org.sonatype.nexus.client.rest.jersey.JerseyNexusClientFactory;
import org.sonatype.nexus.maven.staging.ProgressMonitorImpl;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import com.sun.jersey.api.client.UniformInterfaceException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Remoting, the Nexus client. In the moment of creation of this object, Client will already attempt to reach
 * remote Nexus.
 */
public class RemoteNexus
{
  private static final Logger log = LoggerFactory.getLogger(RemoteNexus.class);

  private final Server server;

  private final Proxy proxy;

  private final NexusClient nexusClient;

  private final StagingWorkflowV2Service stagingWorkflowService;

  public RemoteNexus(final MavenSession mavenSession,
                     final SecDispatcher secDispatcher,
                     final boolean debug,
                     final Parameters parameters)
  {
    checkNotNull(mavenSession);
    checkNotNull(secDispatcher);
    checkNotNull(parameters);
    parameters.validateRemoting();

    // init
    final String nexusUrl = parameters.getNexusUrl();
    try {
      final Server server =
          MavenSettings.selectServer(mavenSession.getSettings(), parameters.getServerId());
      if (server != null) {
        this.server = MavenSettings.decrypt(secDispatcher, server);
      }
      else {
        throw new IllegalArgumentException("Server credentials with ID \"" + parameters.getServerId()
            + "\" not found!");
      }

      // NEXUS-6538: Behave like Wagons: select strictly based on Nexus URL protocol
      final Proxy proxy = MavenSettings.selectProxy(mavenSession.getSettings(), nexusUrl, true);
      if (proxy != null) {
        this.proxy = MavenSettings.decrypt(secDispatcher, proxy);
      }
      else {
        this.proxy = null;
      }
    }
    catch (SecDispatcherException e) {
      throw new IllegalArgumentException("Cannot decipher credentials to be used with Nexus!", e);
    }
    catch (MalformedURLException e) {
      throw new IllegalArgumentException("Malformed Nexus base URL [" + nexusUrl + "]", e);
    }

    // create client and needed subsystem
    this.nexusClient = createNexusClient(parameters);
    this.stagingWorkflowService = createStagingWorkflowV2Service(parameters, nexusClient, debug);
  }

  /**
   * Returns the Server of Maven environment to source authc information from.
   */
  public Server getServer() {
    return server;
  }

  /**
   * Returns the Proxy of Naven environment to use with remote accessing Nexus.
   */
  public Proxy getProxy() {
    return proxy;
  }

  /**
   * Returns connection informations used by Client to connect to remote Nexus.
   */
  public ConnectionInfo getConnectionInfo() {
    return getNexusClient().getConnectionInfo();
  }

  /**
   * Returns the Nexus client ready to be used.
   */
  public NexusClient getNexusClient() {
    return nexusClient;
  }

  /**
   * Returns the status of remote nexus in a moment connection was established. The instance is a cached instance, is
   * not refetched for every call.
   */
  public NexusStatus getNexusStatus() {
    return getNexusClient().getNexusStatus();
  }

  /**
   * Returns the staging workflow service.
   */
  public StagingWorkflowV2Service getStagingWorkflowV2Service() {
    return stagingWorkflowService;
  }

  // ==

  protected NexusClient createNexusClient(final Parameters parameters) {
    final String nexusUrl = parameters.getNexusUrl();
    try {
      final BaseUrl baseUrl = BaseUrl.baseUrlFrom(nexusUrl);
      final UsernamePasswordAuthenticationInfo authenticationInfo;
      final Map<Protocol, ProxyInfo> proxyInfos = new HashMap<Protocol, ProxyInfo>(1);

      if (server != null && server.getUsername() != null) {
        log.info(" + Using server credentials \"{}\" from Maven settings.", server.getId());
        authenticationInfo =
            new UsernamePasswordAuthenticationInfo(server.getUsername(), server.getPassword());
      }
      else {
        authenticationInfo = null;
      }

      if (proxy != null) {
        final UsernamePasswordAuthenticationInfo proxyAuthentication;
        if (proxy.getUsername() != null) {
          proxyAuthentication =
              new UsernamePasswordAuthenticationInfo(proxy.getUsername(), proxy.getPassword());
        }
        else {
          proxyAuthentication = null;
        }
        log.info(" + Using \"{}\" {} Proxy from Maven settings", proxy.getId(), proxy.getProtocol().toUpperCase());
        final ProxyInfo zProxy =
            new ProxyInfo(baseUrl.getProtocol(), proxy.getHost(),
                proxy.getPort(), proxyAuthentication);
        proxyInfos.put(zProxy.getProxyProtocol(), zProxy);
      }

      final ValidationLevel sslCertificateValidationLevel = parameters
          .isSslInsecure() ? ValidationLevel.LAX : ValidationLevel.STRICT;
      final ValidationLevel sslCertificateHostnameValidationLevel = parameters
          .isSslAllowAll() ? ValidationLevel.NONE : ValidationLevel.LAX;
      final ConnectionInfo connectionInfo = new ConnectionInfo(baseUrl, authenticationInfo, proxyInfos,
          sslCertificateValidationLevel, sslCertificateHostnameValidationLevel);
      final NexusClient nexusClient = new JerseyNexusClientFactory(
          // support v2 and v3
          new JerseyStagingWorkflowV2SubsystemFactory(),
          new JerseyStagingWorkflowV3SubsystemFactory()
      ).createFor(connectionInfo);
      final NexusStatus nexusStatus = nexusClient.getNexusStatus();
      log.info(
          String.format(" * Connected to Nexus at %s, is version %s and edition \"%s\"",
              connectionInfo.getBaseUrl(), nexusStatus.getVersion(), nexusStatus.getEditionLong()));
      return nexusClient;
    }
    catch (MalformedURLException e) {
      throw new IllegalArgumentException("Malformed Nexus base URL [" + nexusUrl + "]", e);
    }
    catch (UniformInterfaceException e) {
      throw new IllegalArgumentException("Nexus base URL [" + nexusUrl + "] does not point to a valid Nexus location: "
          + e.getMessage(), e);
    }
    catch (Exception e) {
      throw new IllegalArgumentException("Nexus connection problem to URL [" + nexusUrl + " ]: " + e.getMessage(), e);
    }
  }

  protected StagingWorkflowV2Service createStagingWorkflowV2Service(final Parameters parameters,
                                                                    final NexusClient nexusClient, final boolean debug)
  {
    StagingWorkflowV2Service workflowService = null;
    // First try v3
    try {
      StagingWorkflowV3Service service = nexusClient.getSubsystem(StagingWorkflowV3Service.class);
      log.debug("Using staging v3 service");

      service.setProgressMonitor(new ProgressMonitorImpl(debug));
      service.setProgressTimeoutMinutes(parameters.getStagingProgressTimeoutMinutes());
      service.setProgressPauseDurationSeconds(parameters.getStagingProgressPauseDurationSeconds());

      workflowService = service;
    }
    catch (Exception e) {
      log.debug("Unable to resolve staging v3 service; falling back to v2", e);
    }

    if (workflowService == null) {
      // fallback to v2 if v3 not available
      try {
        workflowService = nexusClient.getSubsystem(StagingWorkflowV2Service.class);
        log.debug("Using staging v2 service");
      }
      catch (IllegalArgumentException e) {
        throw new IllegalArgumentException( // same ex, but "translated" to be more meaningful
            String.format("Nexus instance at base URL %s does not support staging v2; reported status: %s, reason: %s",
                nexusClient.getConnectionInfo().getBaseUrl(),
                nexusClient.getNexusStatus(),
                e.getMessage()),
            e);
      }
    }

    return workflowService;
  }
}
