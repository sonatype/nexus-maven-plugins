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

package org.sonatype.nexus.maven.m2settings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.sonatype.nexus.rest.templates.settings.api.dto.M2SettingsTemplateListResponseDto;
import com.sonatype.nexus.templates.client.M2SettingsTemplates;
import com.sonatype.nexus.templates.client.rest.JerseyTemplatesSubsystemFactory;
import com.sonatype.nexus.usertoken.client.rest.JerseyUserTokenSubsystemFactory;

import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.core.NexusStatus;
import org.sonatype.nexus.client.core.condition.EditionConditions;
import org.sonatype.nexus.client.core.condition.LogicalConditions;
import org.sonatype.nexus.client.core.condition.VersionConditions;
import org.sonatype.nexus.client.rest.AuthenticationInfo;
import org.sonatype.nexus.client.rest.BaseUrl;
import org.sonatype.nexus.client.rest.ConnectionInfo;
import org.sonatype.nexus.client.rest.NexusClientFactory;
import org.sonatype.nexus.client.rest.Protocol;
import org.sonatype.nexus.client.rest.ProxyInfo;
import org.sonatype.nexus.client.rest.UsernamePasswordAuthenticationInfo;
import org.sonatype.nexus.client.rest.jersey.JerseyNexusClientFactory;
import org.sonatype.nexus.maven.m2settings.template.TemplateInterpolatorCustomizer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.interpolation.Interpolator;
import org.codehaus.plexus.interpolation.InterpolatorFilterReader;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;

import static org.sonatype.nexus.client.rest.BaseUrl.baseUrlFrom;

/**
 * Download Nexus m2settings template content and save to local settings file.
 *
 * @since 1.4
 */
@Mojo(name = "download", requiresOnline = true, requiresProject = false, aggregator = true)
public class DownloadMojo
    extends MojoSupport
{
  /**
   * Start of expression for client-side template interpolation.
   */
  public static final String START_EXPR = "$[";

  /**
   * End of expression for client-side template interpolation.
   */
  public static final String END_EXPR = "]";

  @Component
  private Settings settings;

  @Component
  private Prompter prompter;

  @Component
  private List<TemplateInterpolatorCustomizer> customizers;

  /**
   * The base URL of the Nexus server to connect to.
   * If not configured the value will be prompted from user.
   */
  @Parameter(property = "nexusUrl")
  private String nexusUrl;

  /**
   * The name of the user to connect to Nexus as.
   * If not configured the value will be prompted from user.
   */
  @Parameter(property = "username")
  private String username;

  /**
   * The password of the user connecting as.
   * If not configured the value will be prompted from user.
   */
  @Parameter(property = "password")
  private String password;

  /**
   * The id of the m2settings template to download.
   * If not configured the value will be prompted from user.
   */
  @Parameter(property = "templateId")
  private String templateId;

  /**
   * Disable fetching of content over insecure HTTP (ie. when 'true' HTTPS URL is required).
   */
  @Parameter(property = "secure", defaultValue = "true", required = true)
  private boolean secure;

  /**
   * The location of the file to save content to.
   */
  @Parameter(property = "outputFile", defaultValue = "${user.home}/.m2/settings.xml", required = true)
  private File outputFile;

  /**
   * Optional file content encoding.
   */
  @Parameter(property = "encoding")
  private String encoding;

  /**
   * Backup any existing file before overwriting.
   */
  @Parameter(property = "backup", defaultValue = "true")
  private boolean backup;

  /**
   * The format of the backup file timestamp.
   */
  @Parameter(property = "backup.timestampFormat", defaultValue = "-yyyyMMddHHmmss")
  private String backupTimestampFormat;

  /**
   * Enable proxy support.
   */
  @Parameter(property = "proxy", defaultValue = "false")
  private boolean proxyEnabled;

  /**
   * Proxy protocol, either http or https.
   */
  @Parameter(property = "proxy.protocol")
  private String proxyProtocol;

  /**
   * Proxy host or ip address.
   */
  @Parameter(property = "proxy.host")
  private String proxyHost;

  /**
   * Proxy port.
   */
  @Parameter(property = "proxy.port")
  private Integer proxyPort;

  /**
   * Proxy username when authentication is required.
   */
  @Parameter(property = "proxy.username")
  private String proxyUsername;

  /**
   * Proxy password when authentication is required.
   */
  @Parameter(property = "proxy.password")
  private String proxyPassword;

  private NexusClient nexusClient;

  @Override
  protected void doExecute() throws Exception {
    connect();

    String content = fetch();

    save(content);
  }

  @Override
  protected void doCleanup() {
    if (nexusClient != null) {
      try {
        nexusClient.close();
      }
      catch (Exception e) {
        log.debug("Failed to close NexusClient; ignoring", e);
      }
    }
  }

  /**
   * Connect to Nexus.
   */
  private void connect() throws Exception {
    // Request details from user interactively for anything missing
    if (StringUtils.isBlank(nexusUrl)) {
      nexusUrl = prompter.prompt("Nexus URL");
    }
    nexusUrl = nexusUrl.trim();

    if (StringUtils.isBlank(username)) {
      username = prompter.promptWithDefaultValue("Username", System.getProperty("user.name"));
    }
    username = username.trim();

    if (StringUtils.isBlank(password)) {
      password = prompter.prompt("Password", '*');
    }
    // do not trim password, needs to be given asis

    // Setup the connection
    try {
      log.info("Connecting to: {} (as {})", nexusUrl, username);
      nexusClient = createClient(nexusUrl, username, password);
    }
    catch (Exception e) {
      throw fail("Connection failed", e);
    }

    // Validate the connection
    final NexusStatus status = nexusClient.getStatus();
    log.info("Connected: {} {}", status.getAppName(), status.getVersion());
  }

  private NexusClient createClient(final String url, final String username, final String password) throws Exception {
    BaseUrl baseUrl = baseUrlFrom(url);
    if (baseUrl.getProtocol() == Protocol.HTTP) {
      String message = "Insecure protocol: " + baseUrl;
      if (secure) {
        throw fail(message);
      }
      else {
        log.warn(message);
      }
    }

    // configure client w/m2settings and usertoken support, but allow to connect to Nexus Pro 2.3+ only
    NexusClientFactory factory = new JerseyNexusClientFactory(
        LogicalConditions.and(VersionConditions.any27AndLaterVersion(), EditionConditions.anyProEdition()),
        new JerseyTemplatesSubsystemFactory(),
        new JerseyUserTokenSubsystemFactory()
    );

    // for now we assume we always have username/password
    UsernamePasswordAuthenticationInfo auth = new UsernamePasswordAuthenticationInfo(username, password);

    // Configure proxy
    Map<Protocol, ProxyInfo> proxies = Maps.newHashMapWithExpectedSize(1);
    if (proxyEnabled) {
      if (StringUtils.isBlank(proxyProtocol)) {
        proxyProtocol = prompter.promptChoice("Proxy Protocol", "Choose", Lists.newArrayList("http", "https"));
      }
      proxyProtocol = proxyProtocol.trim();

      if (StringUtils.isBlank(proxyHost)) {
        proxyHost = prompter.prompt("Proxy Host");
      }
      proxyHost = proxyHost.trim();

      if (proxyPort == null) {
        proxyPort = prompter.promptInteger("Proxy Port", 1, 65536);
      }

      AuthenticationInfo proxyAuth = null;
      if (!StringUtils.isBlank(proxyUsername) && !StringUtils.isBlank(proxyUsername)) {
        proxyAuth = new UsernamePasswordAuthenticationInfo(proxyUsername.trim(), proxyPassword);
      }
      else {
        // FIXME: asis, when not using proxy auth, this will always get prompted
        String answer = prompter.promptChoice("Proxy Authentication", "Choose", Lists.newArrayList("yes", "no"));
        if ("yes".equals(answer)) {
          if (StringUtils.isBlank(proxyUsername)) {
            proxyUsername = prompter.promptWithDefaultValue("Proxy Username", System.getProperty("user.name"));
          }

          if (StringUtils.isBlank(proxyPassword)) {
            proxyPassword = prompter.prompt("Proxy Password", '*');
          }

          proxyAuth = new UsernamePasswordAuthenticationInfo(proxyUsername.trim(), proxyPassword);
        }
      }

      if (proxyAuth != null) {
        log.info("Proxy enabled: {}@{}:{}:{}", proxyUsername, proxyProtocol, proxyHost, proxyPort);
      }
      else {
        log.info("Proxy enabled: {}:{}:{}", proxyProtocol, proxyHost, proxyPort);
      }

      Protocol protocol = Protocol.valueOf(proxyProtocol);
      ProxyInfo proxy = new ProxyInfo(protocol, proxyHost, proxyPort, proxyAuth);
      proxies.put(protocol, proxy);
    }

    return factory.createFor(new ConnectionInfo(baseUrl, auth, proxies));
  }

  /**
   * Fetch template content.
   */
  private String fetch() throws Exception {
    M2SettingsTemplates templates = nexusClient.getSubsystem(M2SettingsTemplates.class);

    // Ask the user for the templateId if not configured
    if (StringUtils.isBlank(templateId)) {
      List<M2SettingsTemplateListResponseDto> availableTemplates = templates.get();

      // This might never happen, but just in-case the server's impl changes
      if (availableTemplates.isEmpty()) {
        throw fail("There are no accessible m2settings available");
      }

      // If only 1 template, select it
      if (availableTemplates.size() == 1) {
        templateId = availableTemplates.get(0).getId();
      }
      else {
        // Else ask the user which template they want
        List<String> ids = Lists.newArrayListWithExpectedSize(availableTemplates.size());
        for (M2SettingsTemplateListResponseDto template : availableTemplates) {
          ids.add(template.getId());
        }

        // FIXME: would like to sort here to help keep the list consistent, but would also like "default" to always be first
        // FIXME: if we can assume that "default" is always first, then we can strip it off, sort, then prepend it to get what we want
        // FIXME: have to see what the impl/backing of server-side looks like before I'd feel comfortable with this assumption
        //Collections.sort(ids);

        templateId = prompter.promptChoice("Available Templates", "Select Template", ids); // already trimmed
      }
    }
    templateId = templateId.trim();

    // Fetch the template content
    String content;
    try {
      log.info("Fetching content for templateId: {}", templateId);
      content = templates.getContent(templateId);
      log.debug("Content: {}", content);
    }
    catch (Exception e) {
      throw fail("Unable to fetch content for templateId: " + templateId, e);
    }
    return content;
  }

  /**
   * Save content to file.
   */
  private void save(final String content) throws Exception {
    // Backup if requested
    try {
      maybeBackup();
    }
    catch (Exception e) {
      throw fail("Failed to backup file: " + outputFile.getAbsolutePath(), e);
    }

    // Save the content
    log.info("Saving content to: {}", outputFile.getAbsolutePath());

    try {
      Interpolator interpolator = createInterpolator();
      Writer writer = createWriter(outputFile, encoding);
      try {
        InterpolatorFilterReader reader = new InterpolatorFilterReader(new StringReader(content), interpolator,
            START_EXPR, END_EXPR);
        CharStreams.copy(reader, writer);
        writer.flush();
      }
      finally {
        Closeables.closeQuietly(writer);
      }
    }
    catch (Exception e) {
      throw fail("Failed to save content to: " + outputFile.getAbsolutePath(), e);
    }
  }

  /**
   * Backup target file if requested by user if needed.
   */
  private void maybeBackup() throws IOException {
    if (!backup) {
      return;
    }
    if (!outputFile.exists()) {
      log.debug("Output file does not exist; skipping backup");
      return;
    }

    String timestamp = new SimpleDateFormat(backupTimestampFormat).format(new Date());
    File file = new File(outputFile.getParentFile(), outputFile.getName() + timestamp);

    log.info("Backing up: {} to: {}", outputFile.getAbsolutePath(), file.getAbsolutePath());
    Files.move(outputFile, file);
  }

  /**
   * Create the interpolator to filter content.
   */
  private Interpolator createInterpolator() {
    Interpolator interpolator = new StringSearchInterpolator(START_EXPR, END_EXPR);
    if (customizers != null) {
      for (TemplateInterpolatorCustomizer customizer : customizers) {
        try {
          log.debug("Applying customizer: {}", customizer);
          customizer.customize(nexusClient, interpolator);
        }
        catch (Exception e) {
          log.warn("Template customization failed; ignoring", e);
        }
      }
    }
    return interpolator;
  }

  /**
   * Create a writer for the given file and optional encoding.
   */
  private BufferedWriter createWriter(final File file, final @Nullable String encoding) throws IOException {
    if (encoding != null) {
      return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
    }
    else {
      return new BufferedWriter(new FileWriter(file));
    }
  }
}
