package org.sonatype.maven.mojo.logback;

import org.apache.maven.plugin.logging.Log;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

public class LogbackUtils
{

    /**
     * Synchronizes Logback's root log level with the level configured for the Maven log. Sisu internals have some
     * static calls to SLF4J and as long as Maven core doesn't provide a SLF4J binding, Sisu will pick our Logback
     * binding so make sure it behaves nicely. The static configuration parts are provided via our logback.xml.
     */
    public static void syncLogLevelWithMaven( Log log )
    {
        Object factory = LoggerFactory.getILoggerFactory();
        if ( factory instanceof LoggerContext )
        {
            Logger logger = ( (LoggerContext) factory ).getLogger( Logger.ROOT_LOGGER_NAME );
            syncLogLevelWithMaven( logger, log );
        }
    }

    public static void syncLogLevelWithMaven( Logger logger, Log log )
    {
        if ( log.isDebugEnabled() )
        {
            logger.setLevel( Level.DEBUG );
        }
        else if ( log.isInfoEnabled() )
        {
            logger.setLevel( Level.INFO );
        }
        else if ( log.isWarnEnabled() )
        {
            logger.setLevel( Level.WARN );
        }
        else
        {
            logger.setLevel( Level.ERROR );
        }
    }

}