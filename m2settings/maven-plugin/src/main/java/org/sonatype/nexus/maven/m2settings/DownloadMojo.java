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

import com.google.common.base.Throwables;
import com.sonatype.nexus.rest.templates.settings.api.dto.M2SettingsTemplateListResponseDto;
import com.sonatype.nexus.templates.client.M2SettingsTemplates;
import com.sonatype.nexus.templates.client.rest.JerseyTemplatesSubsystemFactory;
import com.sonatype.nexus.usertoken.client.rest.JerseyUserTokenSubsystemFactory;
import jline.console.ConsoleReader;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.interpolation.Interpolator;
import org.codehaus.plexus.interpolation.InterpolatorFilterReader;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.codehaus.plexus.util.IOUtil;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.core.NexusStatus;
import org.sonatype.nexus.client.rest.BaseUrl;
import org.sonatype.nexus.client.rest.ConnectionInfo;
import org.sonatype.nexus.client.rest.Protocol;
import org.sonatype.nexus.client.rest.UsernamePasswordAuthenticationInfo;
import org.sonatype.nexus.client.rest.jersey.JerseyNexusClientFactory;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.List;

import static org.sonatype.nexus.client.rest.BaseUrl.baseUrlFrom;

/**
 * ???
 *
 * @since 1.4
 */
@Mojo(name = "download")
public class DownloadMojo
    extends AbstractMojo
{
    public static final String START_EXPR = "$[";

    public static final String END_EXPR = "]";

    @Component
    private Settings settings;

    @Component
    private List<TemplateInterpolatorCustomizer> customizers;

    @Parameter(property = "nexusUrl")
    private String nexusUrl;

    @Parameter(property = "username")
    private String username;

    @Parameter(property = "password")
    private String password;

    @Parameter(property = "templateId")
    private String templateId;

    @Parameter(property = "secure", defaultValue = "true")
    private boolean secure = true;

    @Parameter(property = "encoding")
    private String encoding;

    // TODO: target location, backup, overwrite, etc.

    private Log log;

    private ConsoleReader console;

    private NexusClient nexusClient;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        log = getLog();

        try {
            doExecute();
        }
        catch (Exception e) {
            Throwables.propagateIfPossible(e, MojoExecutionException.class, MojoFailureException.class);
            throw Throwables.propagate(e);
        }
    }

    private Exception fail(final String message) throws Exception {
        throw new MojoExecutionException(message);
    }

    private Exception fail(final String message, final Throwable cause) throws Exception {
        throw new MojoExecutionException(message, cause);
    }

    private void ensureOnline() throws Exception {
        if (settings.isOffline()) {
            throw fail("Maven is in 'offline' mode; unable to download m2settings");
        }
    }

    // FIXME: Refactor into either components, or into specific methods

    private void doExecute() throws Exception {
        ensureOnline();

        console = new ConsoleReader();

        // TODO: Add an optional mode which will force interactive prompting for confirm/validation ?

        if (StringUtils.isBlank(nexusUrl)) {
            nexusUrl = readString("Nexus URL");
        }

        if (StringUtils.isBlank(username)) {
            username = readStringWithDefaultValue("Username", System.getProperty("user.name"));
        }

        if (StringUtils.isBlank(password)) {
            password = readString("Password", '*');
        }

        // TODO: Better UX here when any of these parameters result in failure to create client

        nexusClient = createClient(nexusUrl, username, password);

        ConnectionInfo info = nexusClient.getConnectionInfo();
        log.debug("Connection: " + info);

        NexusStatus status = nexusClient.getStatus();
        log.debug("Status: " + status);

        // TODO: Verify we are talking to correct version of NX 2.3+

        M2SettingsTemplates templates = nexusClient.getSubsystem(M2SettingsTemplates.class);

        if (StringUtils.isBlank(templateId)) {
            List<M2SettingsTemplateListResponseDto> availableTemplates = templates.get();
            if (availableTemplates.isEmpty()) {
                throw fail("Nexus server has m2settings templates defined");
            }

            // TODO: Provide better UI here to select, would like to preload console history too
            log.info("Available templates:");

            for (M2SettingsTemplateListResponseDto template : availableTemplates) {
                log.info(template.getId());
            }

            templateId = readString("Select Template");
        }

        // TODO: Better UX here when templateId is not valid
        String content = templates.getContent(templateId);
        log.debug("Content: " + content);

        Interpolator interpolator = new StringSearchInterpolator(START_EXPR, END_EXPR);
        if (customizers != null) {
            boolean debug = log.isDebugEnabled();

            for (TemplateInterpolatorCustomizer customizer : customizers) {
                try {
                    if (debug) {
                        log.debug("Applying customizer: " + customizer);
                    }
                    customizer.customize(nexusClient, interpolator);
                }
                catch (Exception e) {
                    log.warn("Template customization failed; ignoring", e);
                }
            }
        }

        InterpolatorFilterReader reader = new InterpolatorFilterReader(
            new StringReader(content), interpolator, START_EXPR, END_EXPR);

        // HACK: Spit it out
        log.info("Content:");
        IOUtil.copy(reader, System.out);
        IOUtil.close(reader);

        // HACK: Turn this off for now while testing
        if (false) {
            // TODO: Select the target file to write to
            File file = null;

            // TODO: Optionally backup, etc.

            Writer writer = null;
            try {
                if (encoding != null) {
                    writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
                }
                else {
                    writer = new BufferedWriter(new FileWriter(file));
                }

                IOUtil.copy(reader, writer);
            }
            catch (IOException e) {
                throw fail("Failed to save settings. Reason: " + e.getMessage(), e);
            }
            finally {
                IOUtil.close(writer);
            }
        }

        nexusClient.close();
    }

    public NexusClient createClient(final String url, final String username, final String password) throws Exception {
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

        return factory.createFor(baseUrl, new UsernamePasswordAuthenticationInfo(username, password));
    }

    private String readString(final String message, final Character mask) throws IOException {
        final String prompt = String.format("%s: ", message);
        String value;
        do {
            value = console.readLine(prompt, mask);
            if (mask != null) {
                log.debug("Read value: '" + value + "'");
            }
            else {
                log.debug("Read masked chars: " + value.length());
            }
        }
        while (StringUtils.isBlank(value));
        return value;
    }

    private String readString(final String prompt) throws IOException {
        return readString(prompt, null);
    }

    private String readStringWithDefaultValue(final String message, final String defaultValue) throws IOException {
        final String prompt = String.format("%s [%s]: ", message, defaultValue);
        String value = console.readLine(prompt);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        return value;
    }
}
