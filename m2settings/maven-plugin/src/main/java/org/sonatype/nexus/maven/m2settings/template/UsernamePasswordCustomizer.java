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
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.interpolation.AbstractValueSource;
import org.codehaus.plexus.interpolation.Interpolator;
import org.jetbrains.annotations.NonNls;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.rest.UsernamePasswordAuthenticationInfo;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Username-password {@link TemplateInterpolatorCustomizer}.
 *
 * @since 1.4
 */
@Component(role=TemplateInterpolatorCustomizer.class, hint="username-password", instantiationStrategy="per-lookup")
public class UsernamePasswordCustomizer
    implements TemplateInterpolatorCustomizer
{
    @NonNls
    public static final String USER_ID = "userId";

    @NonNls
    public static final String USER_NAME = "userName";

    @NonNls
    public static final String PASSWORD = "password";

    @NonNls
    public static final String ENCRYPTED_SUFFIX = ".encrypted";

    @Requirement
    private MasterPasswordEncryption encryption;

    private NexusClient nexusClient;

    // Constructor for Plexus
    public UsernamePasswordCustomizer() {
        super();
    }

    @VisibleForTesting
    public UsernamePasswordCustomizer(final MasterPasswordEncryption encryption,
                                      final NexusClient nexusClient)
    {
        this.encryption = checkNotNull(encryption);
        this.nexusClient = checkNotNull(nexusClient);
    }

    @Override
    public void customize(final NexusClient client, final Interpolator interpolator) {
        this.nexusClient = checkNotNull(client);
        checkNotNull(interpolator);

        interpolator.addValueSource(new AbstractValueSource(false)
        {
            @Override
            public Object getValue(String expression) {
                // Check for encryption flag
                boolean encrypt = false;
                if (expression.toLowerCase().endsWith(ENCRYPTED_SUFFIX)) {
                    encrypt = true;

                    // Strip off suffix and continue
                    expression = expression.substring(0, expression.length() - ENCRYPTED_SUFFIX.length());
                }

                String result = null;
                if (expression.equalsIgnoreCase(USER_NAME)) {
                    result = getUsername();
                }
                else if (expression.equalsIgnoreCase(USER_ID)) {
                    result = getUsername();
                }
                else if (expression.equalsIgnoreCase(PASSWORD)) {
                    result = getPassword();
                }

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
        });
    }

    private UsernamePasswordAuthenticationInfo getAuthenticationInfo() {
        return (UsernamePasswordAuthenticationInfo) nexusClient.getConnectionInfo().getAuthenticationInfo();
    }

    private String getUsername() {
        return getAuthenticationInfo().getUsername();
    }

    private String getPassword() {
        return getAuthenticationInfo().getPassword();
    }
}

