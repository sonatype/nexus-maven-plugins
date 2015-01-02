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
package org.sonatype.maven.mojo.logback;

import java.net.URL;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import com.google.inject.AbstractModule;
import org.apache.maven.plugin.logging.Log;
import org.slf4j.ILoggerFactory;

public class LogbackModule
    extends AbstractModule
{
  private final LoggerContext loggerContext;

  private final ErrorNoticeAppender errorDetector;

  public LogbackModule(final Log log) {
    loggerContext = new LoggerContext();
    fillContext(loggerContext);

    final URL logbackConfigurationUrl = getLogbackConfiguration();

    if (logbackConfigurationUrl != null) {
      try {
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(loggerContext);
        configurator.doConfigure(logbackConfigurationUrl);
      }
      catch (JoranException e) {
        // swallow, should have been recorded as error status and gets reported below
      }
    }

    errorDetector = new ErrorNoticeAppender();
    errorDetector.setContext(loggerContext);
    errorDetector.start();

    MavenAppender mavenBridge = new MavenAppender(log);
    mavenBridge.setContext(loggerContext);
    mavenBridge.start();

    Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
    LogbackUtils.syncLogLevelWithMaven(root, log);
    root.addAppender(errorDetector);
    root.addAppender(mavenBridge);

    StatusPrinter.printInCaseOfErrorsOrWarnings(loggerContext);
  }

  public boolean hasErrors() {
    return errorDetector.hasErrors();
  }

  protected void fillContext(final LoggerContext loggerContext) {
    // override if needed
  }

  protected URL getLogbackConfiguration() {
    // override if needed
    return null;
  }

  protected void configureRootLogger(final Logger rootLogger) {
    // override if needed
  }

  @Override
  protected void configure() {
    binder().bind(ILoggerFactory.class).toInstance(loggerContext);
  }
}