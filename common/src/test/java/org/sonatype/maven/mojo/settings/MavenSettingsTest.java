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

package org.sonatype.maven.mojo.settings;


import org.sonatype.sisu.litmus.testsupport.TestSupport;

import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * UT for {@link MavenSettings}
 */
public class MavenSettingsTest
    extends TestSupport
{
  @Test
  public void proxySelectionNonStrict() throws Exception {
    final Settings settings = new Settings();
    {
      Proxy httpProxy = new Proxy();
      httpProxy.setId("http");
      httpProxy.setActive(true);
      httpProxy.setProtocol("http");
      httpProxy.setHost("somehost");
      httpProxy.setPort(8080);
      httpProxy.setNonProxyHosts("localhost");
      settings.addProxy(httpProxy);
    }
    {
      Proxy httpProxy = new Proxy();
      httpProxy.setId("https");
      httpProxy.setActive(true);
      httpProxy.setProtocol("https");
      httpProxy.setHost("somehost");
      httpProxy.setPort(8081);
      httpProxy.setNonProxyHosts("localhost");
      settings.addProxy(httpProxy);
    }
    {
      // is non proxy host
      final Proxy proxy = MavenSettings.selectProxy(settings, "http://localhost/", false);
      assertThat(proxy, nullValue());
    }
    {
      // is defined
      final Proxy proxy = MavenSettings.selectProxy(settings, "http://remote/", false);
      assertThat(proxy, notNullValue());
      assertThat(proxy.getId(), is("http"));
    }
    {
      // is defined
      final Proxy proxy = MavenSettings.selectProxy(settings, "https://remote/", false);
      assertThat(proxy, notNullValue());
      assertThat(proxy.getId(), is("https"));
    }
  }

  @Test
  public void proxySelectionNonStrictHttpOnly() throws Exception {
    final Settings settings = new Settings();
    {
      Proxy httpProxy = new Proxy();
      httpProxy.setId("http");
      httpProxy.setActive(true);
      httpProxy.setProtocol("http");
      httpProxy.setHost("somehost");
      httpProxy.setPort(8080);
      httpProxy.setNonProxyHosts("localhost");
      settings.addProxy(httpProxy);
    }
    {
      // is non proxy host
      final Proxy proxy = MavenSettings.selectProxy(settings, "http://localhost/", false);
      assertThat(proxy, nullValue());
    }
    {
      // is defined
      final Proxy proxy = MavenSettings.selectProxy(settings, "http://remote/", false);
      assertThat(proxy, notNullValue());
      assertThat(proxy.getId(), is("http"));
    }
    {
      // is fallback
      final Proxy proxy = MavenSettings.selectProxy(settings, "https://remote/", false);
      assertThat(proxy, notNullValue());
      assertThat(proxy.getId(), is("http")); // falls back
    }
  }

  @Test
  public void proxySelectionNonStrictHttpsOnly() throws Exception {
    final Settings settings = new Settings();
    {
      Proxy httpProxy = new Proxy();
      httpProxy.setId("https");
      httpProxy.setActive(true);
      httpProxy.setProtocol("https");
      httpProxy.setHost("somehost");
      httpProxy.setPort(8081);
      httpProxy.setNonProxyHosts("localhost");
      settings.addProxy(httpProxy);
    }
    {
      // is non proxy host
      final Proxy proxy = MavenSettings.selectProxy(settings, "http://localhost/", false);
      assertThat(proxy, nullValue());
    }
    {
      // is not defined
      final Proxy proxy = MavenSettings.selectProxy(settings, "http://remote/", false);
      assertThat(proxy, nullValue());
    }
    {
      // is defined
      final Proxy proxy = MavenSettings.selectProxy(settings, "https://remote/", false);
      assertThat(proxy, notNullValue());
      assertThat(proxy.getId(), is("https"));
    }
  }

  @Test
  public void proxySelectionStrict() throws Exception {
    final Settings settings = new Settings();
    {
      Proxy httpProxy = new Proxy();
      httpProxy.setId("http");
      httpProxy.setActive(true);
      httpProxy.setProtocol("http");
      httpProxy.setHost("somehost");
      httpProxy.setPort(8080);
      httpProxy.setNonProxyHosts("localhost");
      settings.addProxy(httpProxy);
    }
    {
      Proxy httpProxy = new Proxy();
      httpProxy.setId("https");
      httpProxy.setActive(true);
      httpProxy.setProtocol("https");
      httpProxy.setHost("somehost");
      httpProxy.setPort(8081);
      httpProxy.setNonProxyHosts("localhost");
      settings.addProxy(httpProxy);
    }
    {
      // is non proxy host
      final Proxy proxy = MavenSettings.selectProxy(settings, "http://localhost/", true);
      assertThat(proxy, nullValue());
    }
    {
      // is defined
      final Proxy proxy = MavenSettings.selectProxy(settings, "http://remote/", true);
      assertThat(proxy, notNullValue());
      assertThat(proxy.getId(), is("http"));
    }
    {
      // is defined
      final Proxy proxy = MavenSettings.selectProxy(settings, "https://remote/", true);
      assertThat(proxy, notNullValue());
      assertThat(proxy.getId(), is("https"));
    }
  }

  @Test
  public void proxySelectionStrictHttpOnly() throws Exception {
    final Settings settings = new Settings();
    {
      Proxy httpProxy = new Proxy();
      httpProxy.setId("http");
      httpProxy.setActive(true);
      httpProxy.setProtocol("http");
      httpProxy.setHost("somehost");
      httpProxy.setPort(8080);
      httpProxy.setNonProxyHosts("localhost");
      settings.addProxy(httpProxy);
    }
    {
      // is non proxy host
      final Proxy proxy = MavenSettings.selectProxy(settings, "http://localhost/", true);
      assertThat(proxy, nullValue());
    }
    {
      // is defined
      final Proxy proxy = MavenSettings.selectProxy(settings, "http://remote/", true);
      assertThat(proxy, notNullValue());
      assertThat(proxy.getId(), is("http"));
    }
    {
      // is not defined
      final Proxy proxy = MavenSettings.selectProxy(settings, "https://remote/", true);
      assertThat(proxy, nullValue());
    }
  }

  @Test
  public void proxySelectionStrictHttpsOnly() throws Exception {
    final Settings settings = new Settings();
    {
      Proxy httpProxy = new Proxy();
      httpProxy.setId("https");
      httpProxy.setActive(true);
      httpProxy.setProtocol("https");
      httpProxy.setHost("somehost");
      httpProxy.setPort(8081);
      httpProxy.setNonProxyHosts("localhost");
      settings.addProxy(httpProxy);
    }
    {
      // is non proxy host
      final Proxy proxy = MavenSettings.selectProxy(settings, "http://localhost/", true);
      assertThat(proxy, nullValue());
    }
    {
      // is not defined
      final Proxy proxy = MavenSettings.selectProxy(settings, "http://remote/", true);
      assertThat(proxy, nullValue());
    }
    {
      // is defined
      final Proxy proxy = MavenSettings.selectProxy(settings, "https://remote/", true);
      assertThat(proxy, notNullValue());
      assertThat(proxy.getId(), is("https"));
    }
  }
}
