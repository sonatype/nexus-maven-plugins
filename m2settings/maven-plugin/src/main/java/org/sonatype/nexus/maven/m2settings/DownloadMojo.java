/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.sonatype.nexus.rest.templates.settings.api.dto.M2SettingsTemplateListResponseDto;
import com.sonatype.nexus.templates.client.M2SettingsTemplates;
import com.sonatype.nexus.templates.client.rest.JerseyTemplatesSubsystemFactory;
import com.sonatype.nexus.usertoken.client.rest.JerseyUserTokenSubsystemFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.interpolation.Interpolator;
import org.codehaus.plexus.interpolation.InterpolatorFilterReader;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.codehaus.plexus.util.IOUtil;
import org.sonatype.aether.util.version.GenericVersionScheme;
import org.sonatype.aether.version.Version;
import org.sonatype.aether.version.VersionConstraint;
import org.sonatype.aether.version.VersionScheme;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.core.NexusStatus;
import org.sonatype.nexus.client.rest.BaseUrl;
import org.sonatype.nexus.client.rest.ConnectionInfo;
import org.sonatype.nexus.client.rest.Protocol;
import org.sonatype.nexus.client.rest.ProxyInfo;
import org.sonatype.nexus.client.rest.UsernamePasswordAuthenticationInfo;
import org.sonatype.nexus.client.rest.jersey.JerseyNexusClientFactory;

import javax.annotation.Nullable;
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
    public static final String START_EXPR = "$[";

    public static final String END_EXPR = "]";

    /**
     * Allows Nexus 2.3+
     */
    private static final String VERSION_CONSTRAINT = "[2.3,)";

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
     * Disable fetching of content over insecure HTTP (ie. HTTPS URL required).
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
     * True to backup and existing file before overwriting.
     */
    @Parameter(property = "backup", defaultValue = "true")
    private boolean backup;

    /**
     * The format of the backup file timestamp.
     */
    @Parameter(property = "backup.timestampFormat", defaultValue = "-yyyyMMddHHmmss")
    private String backupTimestampFormat;

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
                // ignore
            }
        }
    }

    /**
     * Connect to Nexus.
     */
    private void connect() throws Exception {
        // Request details from user interactively for anything missing
        if (StringUtils.isBlank(nexusUrl)) {
            nexusUrl = prompter.prompt("Nexus URL").trim();
        }
        if (StringUtils.isBlank(username)) {
            username = prompter.promptWithDefaultValue("Username", System.getProperty("user.name")).trim();
        }
        if (StringUtils.isBlank(password)) {
            password = prompter.prompt("Password", '*'); // trim?
        }

        // Setup the connection
        try {
            log.info("Connecting to: {} (as {})", nexusUrl, username);
            nexusClient = createClient(nexusUrl, username, password);
        }
        catch (Exception e) {
            throw fail("Connection failed", e);
        }

        // Validate the connection
        NexusStatus status = nexusClient.getStatus();
        ensureCompatibleNexus(status);
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

        // configure client w/m2settings and usertoken support
        JerseyNexusClientFactory factory = new JerseyNexusClientFactory(
            new JerseyTemplatesSubsystemFactory(),
            new JerseyUserTokenSubsystemFactory()
        );

        // for now we assume we always have username/password
        UsernamePasswordAuthenticationInfo auth = new UsernamePasswordAuthenticationInfo(username, password);

        Map<Protocol, ProxyInfo> proxies = Maps.newHashMapWithExpectedSize(1);
        // TODO: Configure proxy

        return factory.createFor(new ConnectionInfo(baseUrl, auth, proxies));
    }

    /**
     * Require Nexus PRO version 2.3+.
     */
    private void ensureCompatibleNexus(final NexusStatus status) throws Exception {
        log.debug("Ensuring compatibility: {}", status);

        String edition = status.getEditionShort();
        if (!"PRO".equals(edition)) {
            throw fail("Unsupported Nexus edition: " + edition);
        }

        VersionScheme scheme = new GenericVersionScheme();
        VersionConstraint constraint = scheme.parseVersionConstraint(VERSION_CONSTRAINT);
        Version version = scheme.parseVersion(status.getVersion());
        log.debug("Version: {}", version);

        if (!constraint.containsVersion(version)) {
            log.error("Incompatible Nexus version detected");
            log.error("Raw version: {}", status.getVersion());
            log.error("Detected version: {}", version);
            log.error("Compatible version constraint: {}", constraint);
            throw fail("Unsupported Nexus version: " + status.getEditionShort());
        }
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

            List<String> ids = Lists.newArrayListWithExpectedSize(availableTemplates.size());
            for (M2SettingsTemplateListResponseDto template : availableTemplates) {
                ids.add(template.getId());
            }
            templateId = prompter.promptChoice("Available Templates", "Select Template", ids);
        }

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
                InterpolatorFilterReader reader = new InterpolatorFilterReader(new StringReader(content), interpolator, START_EXPR, END_EXPR);
                IOUtil.copy(reader, writer);
                writer.flush();
            }
            finally {
                IOUtil.close(writer);
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
            boolean debug = log.isDebugEnabled();

            for (TemplateInterpolatorCustomizer customizer : customizers) {
                try {
                    if (debug) {
                        log.debug("Applying customizer: {}", customizer);
                    }
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
