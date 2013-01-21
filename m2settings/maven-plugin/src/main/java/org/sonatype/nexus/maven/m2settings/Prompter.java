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

import jline.console.ConsoleReader;
import jline.console.completer.StringsCompleter;
import org.apache.commons.lang.StringUtils;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Prompting helper.
 *
 * @since 1.4
 */
@Component(role=Prompter.class, instantiationStrategy="per-lookup")
public class Prompter
{
    private static final Logger log = LoggerFactory.getLogger(Prompter.class);

    private final ConsoleReader console;

    public Prompter() throws IOException {
        this.console = new ConsoleReader();
        console.setHistoryEnabled(false);
    }

    public ConsoleReader getConsole() {
        return console;
    }

    // FIXME: CTRL-D corrupts the prompt slightly

    /**
     * Prompt user for a string, optionally masking the input.
     */
    public String prompt(final String message, final @Nullable Character mask) throws IOException {
        checkNotNull(message);

        final String prompt = String.format("%s: ", message);
        String value;
        do {
            value = console.readLine(prompt, mask);

            // Do not log values read when masked
            if (mask == null) {
                log.debug("Read value: '{}'", value);
            }
            else {
                log.debug("Read masked chars: {}", value.length());
            }
        }
        while (StringUtils.isBlank(value));
        return value;
    }

    /**
     * Prompt user for a string.
     */
    public String prompt(final String message) throws IOException {
        checkNotNull(message);

        return prompt(message, null);
    }

    /**
     * Prompt user for a string; if user response is blank use a default value.
     */
    public String promptWithDefaultValue(final String message, final String defaultValue) throws IOException {
        checkNotNull(message);
        checkNotNull(defaultValue);

        final String prompt = String.format("%s [%s]: ", message, defaultValue);
        String value = console.readLine(prompt);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Helper to parse an integer w/o exceptions being thrown.
     */
    private @Nullable Integer parseInt(final String value) {
        try {
            return Integer.parseInt(value);
        }
        catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * Prompt user for a string out of a set of available choices.
     */
    public String promptChoice(final String header, final String message, final List<String> choices) throws IOException {
        checkNotNull(header);
        checkNotNull(message);
        checkArgument(choices.size() > 1, "2 or more choices are required");

        // TODO: Sort out if we want zero or one based indexes

        // display header
        console.println(header + ":");
        for (int i = 0; i < choices.size(); i++) {
            console.println(String.format("  %2d) %s", i, choices.get(i)));
        }
        console.flush();

        // setup completer
        StringsCompleter completer = new StringsCompleter(choices);
        console.addCompleter(completer);

        try {
            String value;
            while (true) {
                value = prompt(message).trim();

                // check if value is an index
                Integer i = parseInt(value);
                if (i != null) {
                    if (i >= 0 && i < choices.size()) {
                        value = choices.get(i);
                    }
                    else {
                        // out of range
                        value = null;
                    }
                }

                // check if choice is valid
                if (choices.contains(value)) {
                    break;
                }

                console.println("Invalid selection");
            }
            return value;
        }
        finally {
            console.removeCompleter(completer);
        }
    }
}
