/**
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
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

import java.util.Map;

import org.apache.maven.plugin.logging.Log;
import org.sonatype.nexus.client.core.NexusErrorMessageException;

import com.sonatype.nexus.staging.client.StagingRuleFailures;
import com.sonatype.nexus.staging.client.StagingRuleFailures.RuleFailure;
import com.sonatype.nexus.staging.client.StagingRuleFailuresException;

public class ErrorDumper
{
    public static void dumpErrors( final Log log, final StagingRuleFailuresException e )
    {
        log.error( "" );
        log.error( "Nexus Staging Rules Failure Report" );
        log.error( "==================================" );
        log.error( "" );
        for ( StagingRuleFailures failure : e.getFailures() )
        {
            log.error( String.format( "Repository \"%s\" (id=%s) failures", failure.getRepositoryName(),
                failure.getRepositoryId() ) );
            for ( RuleFailure ruleFailure : failure.getFailures() )
            {
                log.error( String.format( "  Rule \"%s\" failures", ruleFailure.getRuleName() ) );
                for ( String message : ruleFailure.getMessages() )
                {
                    log.error( String.format( "    * %s", unfick( message ) ) );
                }
            }
            log.error( "" );
        }
        log.error( "" );
    }

    public static void dumpErrors( final Log log, final NexusErrorMessageException e )
    {
        log.error( "" );
        log.error( String.format( "Nexus Error Response: %s - %s", e.getStatusCode(), e.getStatusMessage() ) );
        for ( Map.Entry<String, String> errorEntry : e.getErrors().entrySet() )
        {
            log.error( String.format( "  %s - %s", unfick( errorEntry.getKey() ), unfick( errorEntry.getValue() ) ) );
        }
        log.error( "" );
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
}
