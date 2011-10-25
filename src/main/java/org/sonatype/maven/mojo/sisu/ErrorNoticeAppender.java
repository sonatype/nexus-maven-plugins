package org.sonatype.maven.mojo.sisu;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

public class ErrorNoticeAppender
    extends UnsynchronizedAppenderBase<ILoggingEvent>
{

    private boolean errors = false;

    @Override
    protected void append( ILoggingEvent logEvent )
    {
        if ( logEvent.getLevel().isGreaterOrEqual( Level.WARN ) )
        {
            errors = true;
        }
    }

    public boolean hasErrors()
    {
        return errors;
    }

}