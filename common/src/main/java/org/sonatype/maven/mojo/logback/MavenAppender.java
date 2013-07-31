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
package org.sonatype.maven.mojo.logback;

import org.apache.maven.plugin.logging.Log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;

/**
 * An appender for Logback that forwards logging into Maven's logging system.
 */
public class MavenAppender
    extends AppenderBase<ILoggingEvent>
{

    private final Log log;

    public MavenAppender( final Log log )
    {
        this.log = log;
    }

    @Override
    protected void append( ILoggingEvent event )
    {
        if ( event.getLevel().isGreaterOrEqual( Level.ERROR ) )
        {
            log.error( event.getFormattedMessage(), getThrowable( event ) );
        }
        else if ( event.getLevel().isGreaterOrEqual( Level.WARN ) )
        {
            log.warn( event.getFormattedMessage(), getThrowable( event ) );
        }
        else if ( event.getLevel().isGreaterOrEqual( Level.INFO ) )
        {
            log.info( event.getFormattedMessage(), getThrowable( event ) );
        }
        else
        {
            log.debug( event.getFormattedMessage(), getThrowable( event ) );
        }
    }

    private Throwable getThrowable( ILoggingEvent event )
    {
        IThrowableProxy proxy = event.getThrowableProxy();
        if ( proxy instanceof ThrowableProxy )
        {
            return ( (ThrowableProxy) proxy ).getThrowable();
        }
        return null;
    }

}