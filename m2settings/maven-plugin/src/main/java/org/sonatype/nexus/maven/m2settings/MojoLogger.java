package org.sonatype.nexus.maven.m2settings;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.logging.Log;
import org.sonatype.gossip.Event;
import org.sonatype.gossip.Level;
import org.sonatype.gossip.LoggerSupport;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Adapts {@link org.apache.maven.plugin.logging.Log} to {@link org.slf4j.Logger}.
 *
 * @since 1.4
 */
public class MojoLogger
    extends LoggerSupport
{
    private final Log log;

    public MojoLogger(final Log log) {
        this.log = checkNotNull(log);
    }

    public MojoLogger(final Mojo mojo) {
        this(mojo.getLog());
    }

    @Override
    protected boolean isEnabled(final Level level) {
        switch (level) {
            case ALL:
            case TRACE:
            case DEBUG:
                return log.isDebugEnabled();
            case INFO:
                return log.isInfoEnabled();
            case WARN:
                return log.isWarnEnabled();
            case ERROR:
                return log.isErrorEnabled();
            default:
                return false;
        }
    }

    @Override
    protected void doLog(final Event event) {
        switch (event.getLevel()) {
            case TRACE:
            case DEBUG:
                log.debug(event.getMessage(), event.getCause());
                break;
            case INFO:
                log.info(event.getMessage(), event.getCause());
                break;
            case WARN:
                log.warn(event.getMessage(), event.getCause());
                break;
            case ERROR:
                log.error(event.getMessage(), event.getCause());
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }
}
