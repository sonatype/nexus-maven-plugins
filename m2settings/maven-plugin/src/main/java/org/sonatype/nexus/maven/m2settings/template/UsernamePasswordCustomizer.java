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
package org.sonatype.nexus.maven.m2settings.template;

import org.sonatype.nexus.client.core.NexusClient;

import com.google.common.annotations.VisibleForTesting;
import org.codehaus.plexus.component.annotations.Component;
import org.jetbrains.annotations.NonNls;

/**
 * Username-password {@link TemplateInterpolatorCustomizer}.
 *
 * @since 1.4
 */
@Component(role = TemplateInterpolatorCustomizer.class, hint = "username-password",
    instantiationStrategy = "per-lookup")
public class UsernamePasswordCustomizer
    extends MasterPasswordCustomizerSupport
{
  @NonNls
  public static final String USER_ID = "userId";

  @NonNls
  public static final String USER_NAME = "userName";

  @NonNls
  public static final String PASSWORD = "password";

  // Constructor for Plexus
  public UsernamePasswordCustomizer() {
    super();
  }

  @VisibleForTesting
  public UsernamePasswordCustomizer(final MasterPasswordEncryption encryption,
                                    final NexusClient nexusClient)
  {
    super(encryption, nexusClient);
  }

  @Override
  protected String getPlainValue(final String expression) {
    String result = null;
    if (expression.equalsIgnoreCase(USER_NAME) || expression.equalsIgnoreCase(USER_ID)) {
      result = getUsername();
    }
    else if (expression.equalsIgnoreCase(PASSWORD)) {
      result = getPassword();
    }
    return result;
  }

  private String getUsername() {
    return getAuthenticationInfo().getUsername();
  }

  private String getPassword() {
    return getAuthenticationInfo().getPassword();
  }
}

