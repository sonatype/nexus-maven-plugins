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
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Default {@link ProgressMonitor} implementation.
 *
 * @since 1.4
 */
public class ProgressMonitorImpl
    implements ProgressMonitor
{
    protected final Logger logger;
    
    protected boolean needsNewline;

    public ProgressMonitorImpl(final Logger logger) {
        this.logger = checkNotNull(logger);
    }

    public ProgressMonitorImpl(final Log log) {
        int level = Logger.LEVEL_INFO;
        if (log.isDebugEnabled()) {
            level = Logger.LEVEL_DEBUG;
        }
        this.logger = new ConsoleLogger(level, getClass().getName());
    }

    protected void maybePrintln() {
        if (needsNewline) {
            System.out.println();
            needsNewline = false;
        }
    }

    @Override
    public void start() {
        if (logger.isDebugEnabled()) {
            logger.debug("START");
        }
        else {
            System.out.println();
            System.out.print("Waiting for operation to complete...");
        }
    }

    @Override
    public void tick() {
        if (logger.isDebugEnabled()) {
            logger.debug("TICK");
        }
        else {
            needsNewline = true;
            System.out.print(".");
        }
    }

    @Override
    public void pause() {
        logger.debug("PAUSE");
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
        if (logger.isDebugEnabled()) {
            logger.debug("STOP");
        }
        else {
            maybePrintln();
            System.out.println();
        }
    }

    @Override
    public void timeout() {
        maybePrintln();
        logger.warn("TIMEOUT");
    }

    @Override
    public void interrupted() {
        maybePrintln();
        logger.warn("INTERRUPTED");
    }
}
