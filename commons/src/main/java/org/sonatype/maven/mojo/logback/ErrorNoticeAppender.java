package org.sonatype.maven.mojo.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

class ErrorNoticeAppender
    extends UnsynchronizedAppenderBase<ILoggingEvent>
{
    private boolean errors = false;

    @Override
    protected void append( final ILoggingEvent logEvent )
    {
        if ( Level.WARN.isGreaterOrEqual( logEvent.getLevel() ) )
        {
            errors = true;
        }
    }

    public boolean hasErrors()
    {
        return errors;
    }
}