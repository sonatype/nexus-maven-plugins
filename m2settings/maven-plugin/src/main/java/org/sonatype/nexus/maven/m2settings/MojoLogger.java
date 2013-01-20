package org.sonatype.nexus.maven.m2settings;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(MojoLogger.class);

    private final Mojo owner;

    public MojoLogger(final Mojo owner) {
        this.owner = checkNotNull(owner);
    }

    public Mojo getOwner() {
        return owner;
    }

    @Override
    protected boolean isEnabled(final Level level) {
        Log mojoLog = getOwner().getLog();
        if (mojoLog == null) {
            log.warn("Mojo.log not configured; owner: {}", owner);
            return false;
        }

        switch (level) {
            case ALL:
            case TRACE:
            case DEBUG:
                return mojoLog.isDebugEnabled();
            case INFO:
                return mojoLog.isInfoEnabled();
            case WARN:
                return mojoLog.isWarnEnabled();
            case ERROR:
                return mojoLog.isErrorEnabled();
            default:
                return false;
        }
    }

    @Override
    protected void doLog(final Event event) {
        Log mojoLog = getOwner().getLog();
        if (mojoLog == null) {
            log.warn("Mojo.log not configured; owner: {}, event: {}", owner, event);
            return;
        }

        switch (event.getLevel()) {
            case TRACE:
            case DEBUG:
                mojoLog.debug(event.getMessage(), event.getCause());
                break;
            case INFO:
                mojoLog.info(event.getMessage(), event.getCause());
                break;
            case WARN:
                mojoLog.warn(event.getMessage(), event.getCause());
                break;
            case ERROR:
                mojoLog.error(event.getMessage(), event.getCause());
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }
}
