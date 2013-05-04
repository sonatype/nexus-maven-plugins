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
package org.sonatype.nexus.maven.m2settings.template;

import com.google.common.annotations.VisibleForTesting;
import com.sonatype.nexus.usertoken.client.UserToken;
import com.sonatype.nexus.usertoken.plugin.rest.model.AuthTicketXO;
import com.sonatype.nexus.usertoken.plugin.rest.model.UserTokenXO;
import org.codehaus.plexus.component.annotations.Component;
import org.jetbrains.annotations.NonNls;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.rest.UsernamePasswordAuthenticationInfo;

/**
 * User-token {@link TemplateInterpolatorCustomizer}.
 *
 * @since 1.4
 */
@Component(role=TemplateInterpolatorCustomizer.class, hint="usertoken", instantiationStrategy="per-lookup")
public class UserTokenCustomizer
    extends MasterPasswordCustomizerSupport
{
    public static final char SEPARATOR = ':';

    @NonNls
    public static final String USER_TOKEN = "userToken";

    @NonNls
    public static final String USER_TOKEN_NAME_CODE = USER_TOKEN + ".nameCode";

    @NonNls
    public static final String USER_TOKEN_PASS_CODE = USER_TOKEN + ".passCode";

    // Constructor for Plexus
    public UserTokenCustomizer() {
        super();
    }

    @VisibleForTesting
    public UserTokenCustomizer(final MasterPasswordEncryption encryption,
                               final NexusClient nexusClient)
    {
        super(encryption, nexusClient);
    }

    @Override
    protected String getPlainValue(final String expression) {
        String result = null;
        if (expression.equalsIgnoreCase(USER_TOKEN)) {
            result = renderUserToken();
        }
        else if (expression.equalsIgnoreCase(USER_TOKEN_NAME_CODE)) {
            result = getNameCode();
        }
        else if (expression.equalsIgnoreCase(USER_TOKEN_PASS_CODE)) {
            result = getPassCode();
        }
        return result;
    }

    /**
     * Cached user-token details, as more than one interpolation key may need to use this data.
     *
     * Component using instantiationStrategy="per-lookup" to try and avoid holding on to this for too long.
     */
    private UserTokenXO cachedToken;

    private UserTokenXO getUserToken() {
        if (cachedToken == null) {
            UserToken userToken = getNexusClient().getSubsystem(UserToken.class);
            UsernamePasswordAuthenticationInfo auth = getAuthenticationInfo();
            AuthTicketXO ticket = userToken.authenticate(auth.getUsername(), auth.getPassword());
            cachedToken = userToken.get(ticket.getT());
        }
        return cachedToken;
    }

    private String renderUserToken() {
        return getNameCode() + SEPARATOR + getPassCode();
    }

    private String getNameCode() {
        return getUserToken().getNameCode();
    }

    private String getPassCode() {
        return getUserToken().getPassCode();
    }
}
