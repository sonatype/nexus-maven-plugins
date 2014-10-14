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

package org.sonatype.nexus.maven.staging;

import com.sonatype.nexus.staging.client.StagingWorkflowV3Service.ProgressMonitor;

import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link ProgressMonitor} implementation.
 *
 * @since 1.4
 */
public class ProgressMonitorImpl
    implements ProgressMonitor
{
  protected final Logger logger;

  protected final Stopwatch stopwatch;

  protected boolean needsNewline;

  public ProgressMonitorImpl() {
    this.logger = LoggerFactory.getLogger(getClass());
    this.stopwatch = new Stopwatch();
  }

  protected void maybePrintln() {
    if (needsNewline) {
      System.out.println();
      needsNewline = false;
    }
  }

  @Override
  public void start() {
    stopwatch.reset().start();
    if (logger.isDebugEnabled()) {
      logger.debug("START");
    }
    else {
      System.out.println();
      System.out.println("Waiting for operation to complete...");
    }
  }

  @Override
  public void tick() {
    if (logger.isDebugEnabled()) {
      logger.debug("TICK at {}", stopwatch);
    }
    else {
      needsNewline = true;
      System.out.print(".");
    }
  }

  @Override
  public void pause() {
    logger.debug("PAUSE at {}", stopwatch);
  }

  @Override
  public void info(final String message) {
    logger.debug(message);
  }

  @Override
  public void error(final String message) {
    logger.debug(message);
  }

  @Override
  public void stop() {
    stopwatch.stop();
    if (logger.isDebugEnabled()) {
      logger.debug("STOP after {}", stopwatch);
    }
    else {
      maybePrintln();
      System.out.println();
    }
  }

  @Override
  public void timeout() {
    maybePrintln();
    logger.warn("TIMEOUT after {}", stopwatch);
  }

  @Override
  public void interrupted() {
    maybePrintln();
    logger.warn("INTERRUPTED after {}", stopwatch);
  }
}
