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

import com.sonatype.nexus.usertoken.client.UserToken;
import com.sonatype.nexus.usertoken.plugin.rest.model.AuthTicketXO;
import com.sonatype.nexus.usertoken.plugin.rest.model.UserTokenXO;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.rest.ConnectionInfo;
import org.sonatype.nexus.client.rest.UsernamePasswordAuthenticationInfo;
import org.sonatype.sisu.litmus.testsupport.TestSupport;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonatype.nexus.maven.m2settings.DownloadMojo.END_EXPR;
import static org.sonatype.nexus.maven.m2settings.DownloadMojo.START_EXPR;
import static org.sonatype.nexus.maven.m2settings.template.UserTokenCustomizer.ENCRYPTED_SUFFIX;
import static org.sonatype.nexus.maven.m2settings.template.UserTokenCustomizer.USER_TOKEN;
import static org.sonatype.nexus.maven.m2settings.template.UserTokenCustomizer.USER_TOKEN_NAME_CODE;
import static org.sonatype.nexus.maven.m2settings.template.UserTokenCustomizer.USER_TOKEN_PASS_CODE;

/**
 * Tests for {@link UserTokenCustomizer}.
 */
public class UserTokenTemplateInterpolatorCustomizerTest
    extends TestSupport
{
    @Mock
    private NexusClient nexusClient;

    @Mock
    private UserToken userToken;

    @Mock
    private MasterPasswordEncryption encryption;

    private UserTokenCustomizer customizer;

    private StringSearchInterpolator interpolator;

    @Before
    public void setUp() throws Exception {
        UserTokenXO token = new UserTokenXO();
        token.setNameCode("nc");
        token.setPassCode("pc");
        token.setCreated(new Date());

        when(nexusClient.getSubsystem(UserToken.class)).thenReturn(userToken);

        AuthTicketXO ticket = new AuthTicketXO().withT("some-ticket-blah-blah-blah");
        when(userToken.authenticate(anyString(), anyString())).thenReturn(ticket);

        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        when(nexusClient.getConnectionInfo()).thenReturn(connectionInfo);

        UsernamePasswordAuthenticationInfo auth = new UsernamePasswordAuthenticationInfo("foo", "bar");
        when(connectionInfo.getAuthenticationInfo()).thenReturn(auth);

        when(userToken.get(anyString())).thenReturn(token);
        when(encryption.encrypt(any(String.class))).thenReturn("{foo}");

        customizer = new UserTokenCustomizer(encryption, nexusClient);
        interpolator = new StringSearchInterpolator(START_EXPR, END_EXPR);

        customizer.customize(nexusClient, interpolator);
    }

    private String interpolate(final String expr) throws InterpolationException {
        return interpolator.interpolate(START_EXPR + expr + END_EXPR);
    }

    // FIXME: Add tests for authticket bits

    @Test
    public void interpolate_userToken() throws Exception {
        String result = interpolate(USER_TOKEN);
        assertEquals("nc:pc", result);
        verify(userToken, times(1)).get(anyString());
    }

    @Test
    public void interpolate_userToken_encrypted() throws Exception {
        String result = interpolate(USER_TOKEN + ENCRYPTED_SUFFIX);
        assertEquals("{foo}", result);
        verify(userToken, times(1)).get(anyString());
        verify(encryption, times(1)).encrypt(any(String.class));
    }

    @Test
    public void interpolate_userToken_nameCode() throws Exception {
        String result = interpolate(USER_TOKEN_NAME_CODE);
        assertEquals("nc", result);
        verify(userToken, times(1)).get(anyString());
    }

    @Test
    public void interpolate_userToken_nameCode_encrypted() throws Exception {
        String result = interpolate(USER_TOKEN_NAME_CODE + ENCRYPTED_SUFFIX);
        assertEquals("{foo}", result);
        verify(userToken, times(1)).get(anyString());
        verify(encryption, times(1)).encrypt(any(String.class));
    }

    @Test
    public void interpolate_userToken_passCode() throws Exception {
        String result = interpolate(USER_TOKEN_PASS_CODE);
        assertEquals("pc", result);
        verify(userToken, times(1)).get(anyString());
    }

    @Test
    public void interpolate_userToken_passCode_encrypted() throws Exception {
        String result = interpolate(USER_TOKEN_PASS_CODE + ENCRYPTED_SUFFIX);
        assertEquals("{foo}", result);
        verify(userToken, times(1)).get(anyString());
        verify(encryption, times(1)).encrypt(any(String.class));
    }
}
