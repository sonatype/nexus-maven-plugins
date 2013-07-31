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