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
package org.sonatype.nexus.maven.m2settings.template;

import com.google.common.annotations.VisibleForTesting;
import org.codehaus.plexus.component.annotations.Requirement;
import org.jetbrains.annotations.NonNls;
import org.sonatype.nexus.client.core.NexusClient;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Support for {@link TemplateInterpolatorCustomizer} instances which need {@link MasterPasswordEncryption} integration.
 *
 * @since 1.4
 */
public abstract class MasterPasswordCustomizerSupport
    extends TemplateInterpolatorCustomizerSupport
{
    @NonNls
    public static final String ENCRYPTED_SUFFIX = ".encrypted";

    @Requirement
    private MasterPasswordEncryption encryption;

    // Constructor for Plexus
    public MasterPasswordCustomizerSupport() {
        super();
    }

    @VisibleForTesting
    public MasterPasswordCustomizerSupport(final MasterPasswordEncryption encryption,
                                           final NexusClient nexusClient)
    {
        super(nexusClient);
        this.encryption = checkNotNull(encryption);
    }

    protected MasterPasswordEncryption getEncryption() {
        return encryption;
    }

    /**
     * Handles encryption of plain-values for entries suffixed with {@link #ENCRYPTED_SUFFIX}.
     *
     * @see #getPlainValue(String)
     */
    @Override
    protected String getValue(@NonNls String expression) {
        // Check for encryption flag
        boolean encrypt = false;
        if (expression.toLowerCase().endsWith(ENCRYPTED_SUFFIX)) {
            encrypt = true;

            // Strip off suffix and continue
            expression = expression.substring(0, expression.length() - ENCRYPTED_SUFFIX.length());
        }

        String result = getPlainValue(expression);

        // Attempt to encrypt
        if (encrypt && result != null) {
            try {
                result = encryption.encrypt(result);
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to encrypt result; Master-password encryption configuration may be missing or invalid", e);
            }
        }

        return result;
    }

    protected abstract String getPlainValue(@NonNls String expression);
}

