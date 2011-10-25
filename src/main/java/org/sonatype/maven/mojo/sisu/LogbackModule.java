package org.sonatype.maven.mojo.sisu;

import java.net.URL;

import org.apache.maven.plugin.logging.Log;
import org.slf4j.ILoggerFactory;
import org.sonatype.maven.mojo.logback.LogbackUtils;
import org.sonatype.maven.mojo.logback.MavenAppender;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

import com.google.inject.AbstractModule;

/**
 * A Logback Guice module, adding support and bindings for Logback from within a Mojo. Also, it automatically "syncs"
 * with the logging level of Maven where current Mojo is executing.
 */
public class LogbackModule
    extends AbstractModule
{

    private final LoggerContext loggerContext;

    public LogbackModule( final Log log )
    {
        loggerContext = new LoggerContext();
        fillContext( loggerContext );

        final URL logbackConfigurationUrl = getLogbackConfiguration();

        if ( logbackConfigurationUrl != null )
        {
            try
            {
                JoranConfigurator configurator = new JoranConfigurator();
                configurator.setContext( loggerContext );
                configurator.doConfigure( logbackConfigurationUrl );
            }
            catch ( JoranException e )
            {
                // swallow, should have been recorded as error status and gets reported below
            }
        }

        MavenAppender mavenBridge = new MavenAppender( log );
        mavenBridge.setContext( loggerContext );
        mavenBridge.start();

        Logger root = loggerContext.getLogger( Logger.ROOT_LOGGER_NAME );
        LogbackUtils.syncLogLevelWithMaven( root, log );
        root.addAppender( mavenBridge );

        configureRootLogger( root );

        StatusPrinter.printInCaseOfErrorsOrWarnings( loggerContext );
    }

    protected void fillContext( final LoggerContext loggerContext )
    {
        // override if needed
    }

    protected URL getLogbackConfiguration()
    {
        // override if needed
        return null;
    }

    protected void configureRootLogger( final Logger rootLogger )
    {
        // override if needed
    }

    @Override
    protected void configure()
    {
        binder().bind( ILoggerFactory.class ).toInstance( loggerContext );
    }

}