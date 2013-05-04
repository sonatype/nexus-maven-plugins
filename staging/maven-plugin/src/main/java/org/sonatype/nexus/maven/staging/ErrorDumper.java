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

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.logging.Logger;
import org.sonatype.nexus.client.core.exception.NexusClientErrorResponseException;
import com.sonatype.nexus.staging.client.StagingRuleFailures;
import com.sonatype.nexus.staging.client.StagingRuleFailures.RuleFailure;
import com.sonatype.nexus.staging.client.StagingRuleFailuresException;

/**
 * A simple helper class to "dump" meaningful console error messages using various output methods.
 * 
 * @author cstamas
 */
public class ErrorDumper
{
    public static void dumpErrors( final Logger log, final StagingRuleFailuresException e )
    {
        dumpErrors( new PlexusLoggerWriter( log ), e );
    }

    public static void dumpErrors( final Logger log, final NexusClientErrorResponseException e )
    {
        dumpErrors( new PlexusLoggerWriter( log ), e );
    }

    public static void dumpErrors( final Log log, final StagingRuleFailuresException e )
    {
        dumpErrors( new MojoLoggerWriter( log ), e );
    }

    public static void dumpErrors( final Log log, final NexusClientErrorResponseException e )
    {
        dumpErrors( new MojoLoggerWriter( log ), e );
    }

    // ==

    public static void dumpErrors( final Writer writer, final StagingRuleFailuresException e )
    {
        writer.writeln( "" );
        writer.writeln( "Nexus Staging Rules Failure Report" );
        writer.writeln( "==================================" );
        writer.writeln( "" );
        for ( StagingRuleFailures failure : e.getFailures() )
        {
            writer.writeln( String.format( "Repository \"%s\" failures", failure.getRepositoryId() ) );
            for ( RuleFailure ruleFailure : failure.getFailures() )
            {
                writer.writeln( String.format( "  Rule \"%s\" failures", ruleFailure.getRuleName() ) );
                for ( String message : ruleFailure.getMessages() )
                {
                    writer.writeln( String.format( "    * %s", unfick( message ) ) );
                }
            }
            writer.writeln( "" );
        }
        writer.writeln( "" );
    }

    public static void dumpErrors( final Writer writer, final NexusClientErrorResponseException e )
    {
        writer.writeln( "" );
        writer.writeln( String.format( "Nexus Error Response: %s - %s", e.getResponseCode(), e.getReasonPhrase() ) );
        for ( NexusClientErrorResponseException.ErrorMessage errorEntry : e.errors() )
        {
            writer.writeln( String.format( "  %s - %s", unfick( errorEntry.getId() ),
                                           unfick( errorEntry.getMessage() ) ) );
        }
        writer.writeln( "" );
    }

    // ==

    protected static String unfick( final String str )
    {
        if ( str != null )
        {
            return str.replace( "&quot;", "" ).replace( "&lt;b&gt;", "" ).replace( "&lt;/b&gt;", "" );
        }
        return str;
    }

    // ==

    public static interface Writer
    {
        void writeln( final String string );
    }

    public static class PlexusLoggerWriter
        implements Writer
    {
        private final Logger logger;

        public PlexusLoggerWriter( final Logger logger )
        {
            this.logger = logger;
        }

        @Override
        public void writeln( final String string )
        {
            logger.error( string );
        }
    }

    public static class MojoLoggerWriter
        implements Writer
    {
        private final Log logger;

        public MojoLoggerWriter( final Log logger )
        {
            this.logger = logger;
        }

        @Override
        public void writeln( final String string )
        {
            logger.error( string );
        }
    }
}
