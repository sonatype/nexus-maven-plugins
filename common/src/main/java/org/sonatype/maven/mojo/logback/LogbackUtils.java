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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.apache.maven.plugin.logging.Log;
import org.slf4j.LoggerFactory;

public class LogbackUtils
{

  /**
   * Synchronizes Logback's root log level with the level configured for the Maven log. Sisu internals have some
   * static calls to SLF4J and as long as Maven core doesn't provide a SLF4J binding, Sisu will pick our Logback
   * binding so make sure it behaves nicely. The static configuration parts are provided via our logback.xml.
   */
  public static void syncLogLevelWithMaven(Log log) {
    Object factory = LoggerFactory.getILoggerFactory();
    if (factory instanceof LoggerContext) {
      Logger logger = ((LoggerContext) factory).getLogger(Logger.ROOT_LOGGER_NAME);
      syncLogLevelWithMaven(logger, log);
    }
  }

  /**
   * Synchronizes Logback's root log level with the level passed in as parameter.
   */
  public static void syncLogLevelWithLevel(Level level) {
    Object factory = LoggerFactory.getILoggerFactory();
    if (factory instanceof LoggerContext) {
      Logger logger = ((LoggerContext) factory).getLogger(Logger.ROOT_LOGGER_NAME);
      syncLogLevelWithLevel(logger, level);
    }
  }

  /**
   * Syncs the passed in logback logger with the passed in Mojo Log level.
   */
  public static void syncLogLevelWithMaven(Logger logger, Log log) {
    if (log.isDebugEnabled()) {
      syncLogLevelWithLevel(logger, Level.DEBUG);
    }
    else if (log.isInfoEnabled()) {
      syncLogLevelWithLevel(logger, Level.INFO);
    }
    else if (log.isWarnEnabled()) {
      syncLogLevelWithLevel(logger, Level.WARN);
    }
    else {
      syncLogLevelWithLevel(logger, Level.ERROR);
    }
  }

  public static void syncLogLevelWithLevel(Logger logger, Level level) {
    logger.setLevel(level);
  }
}