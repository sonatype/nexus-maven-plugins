/*
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
package org.sonatype.nexus.maven.staging.workflow;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;

/**
 * Super class for "RC" mojos, that are always configured from CLI, as none of them requires project.
 *
 * @author cstamas
 */
public abstract class AbstractStagingRcActionMojo
    extends AbstractStagingActionMojo
{

    /**
     * Specifies the (opened) staging repository ID (or multiple ones comma separated) on remote Nexus against which RC
     * staging action should happen. If not given, mojo will fail.
     */
    @Parameter( property = "stagingRepositoryId", required = true )
    private String stagingRepositoryId;

    protected String[] getStagingRepositoryIds()
        throws MojoExecutionException
    {
        if ( stagingRepositoryId == null )
        {
            throw new MojoExecutionException(
                "The staging repository to operate against is not defined! (use \"-DstagingRepositoryId=foo1,foo2\" on CLI)" );
        }

        final String[] result = StringUtils.split( stagingRepositoryId, "," );

        if ( result == null || result.length == 0 )
        {
            throw new MojoExecutionException(
                "The staging repository to operate against is not defined! (use \"-DstagingRepositoryId=foo1,foo2\" on CLI)" );
        }

        return result;
    }
}
