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
package org.sonatype.nexus.plugin.staging;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.nexus.plugin.deploy.AbstractDeployMojo;
import org.sonatype.nexus.plugin.deploy.DeployMojo;

/**
 * Super class for "non RC" mojos, that are usable from within the build (if you want more than default V2 actions to
 * happen, ie. to release at the end of the build). These goals will happen within the "context" of {@link DeployMojo},
 * as it will use the properties file saved in root of local staging repository. When executed in multi module build,
 * these mojos will be skipped until the very last module having the plugin defined (very same technique as
 * {@link DeployMojo} uses.
 * 
 * @author cstamas
 */
public abstract class AbstractStagingBuildActionMojo
    extends AbstractStagingActionMojo
{
    /**
     * Specifies the staging repository ID on remote Nexus against which staging action should happen. If not given,
     * mojo will fail. If not given, the properties file from local staging repository will be consulted.
     * 
     * @parameter expression="${stagingRepositoryId}"
     */
    private String stagingRepositoryId;

    protected String[] getStagingRepositoryIds()
        throws MojoExecutionException
    {
        String[] result = null;
        if ( stagingRepositoryId != null )
        {
            // explicitly configured either via config or CLI, use that
            result = StringUtils.split( stagingRepositoryId, "," );
        }
        if ( result == null )
        {
            // collect all the repositories we created
            final ArrayList<String> resultNames = new ArrayList<String>();
            final File stageRoot = getStagingDirectoryRoot();
            final File[] localStageRepositories = stageRoot.listFiles();
            if ( localStageRepositories == null )
            {
                getLog().info( "We have nothing locally staged, bailing out." );
                result = null; // this will fail the build later below
            }
            else
            {
                for ( File profileDirectory : localStageRepositories )
                {
                    if ( !( profileDirectory.isFile() && profileDirectory.getName().endsWith(
                        AbstractDeployMojo.STAGING_REPOSITORY_PROPERTY_FILE_NAME_SUFFIX ) ) )
                    {
                        continue;
                    }
                    final String managedStagingRepositoryId =
                        readStagingRepositoryIdFromPropertiesFile( profileDirectory );
                    if ( managedStagingRepositoryId != null )
                    {
                        resultNames.add( managedStagingRepositoryId );
                    }
                }
            }
            result = resultNames.toArray( new String[resultNames.size()] );
        }

        // check did we get any result at all
        if ( result == null || result.length == 0 )
        {
            throw new MojoExecutionException(
                "The staging repository to operate against is not defined! (use \"-DstagingRepositoryId=foo1,foo2\" on CLI)" );
        }
        return result;
    }

    protected String readStagingRepositoryIdFromPropertiesFile( final File stagingRepositoryPropertiesFile )
        throws MojoExecutionException
    {
        // it will exist only if remote staging happened!
        if ( stagingRepositoryPropertiesFile.isFile() )
        {
            final Properties stagingRepositoryProperties = new Properties();
            FileInputStream fis;
            try
            {
                fis = new FileInputStream( stagingRepositoryPropertiesFile );
                stagingRepositoryProperties.load( fis );
                final boolean managed =
                    Boolean.valueOf( stagingRepositoryProperties.getProperty( AbstractDeployMojo.STAGING_REPOSITORY_MANAGED ) );
                if ( managed )
                {
                    return stagingRepositoryProperties.getProperty( AbstractDeployMojo.STAGING_REPOSITORY_ID );
                }
                else
                {
                    return null;
                }
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Unexpected IO exception while loading up staging properties from "
                    + stagingRepositoryPropertiesFile.getAbsolutePath(), e );
            }
        }
        else
        {
            throw new MojoExecutionException( "Unexpected input: this is not a properties file: "
                + stagingRepositoryPropertiesFile.getAbsolutePath() );
        }
    }

    /**
     * Execute only in last module (to not drop/release same repo over and over, as many times as modules exist in
     * project). This should cover both cases: "direct CLI invocation" will still work (see NXCM-4399) but also works in
     * "bound to phase" case, as in that case, user has to ensure he bounds this goal to a leaf module's lifecycle.
     */
    @Override
    protected boolean shouldExecute()
    {
        final boolean shouldExecute = isThisLastProjectWithThisMojoInExecution();
        if ( !shouldExecute )
        {
            getLog().info( "Execution skipped to the last project having scheduled execution of this Mojo..." );
        }
        return shouldExecute;
    }
}
