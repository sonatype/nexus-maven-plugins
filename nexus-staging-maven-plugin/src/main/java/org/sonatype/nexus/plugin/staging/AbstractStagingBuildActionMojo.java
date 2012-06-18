package org.sonatype.nexus.plugin.staging;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.sonatype.nexus.plugin.deploy.AbstractDeployMojo;

/**
 * Super class for "RC" mojos, that are always configured from CLI, as none of them requires project.
 * 
 * @author cstamas
 */
public abstract class AbstractStagingBuildActionMojo
    extends AbstractStagingActionMojo
{
    /**
     * Specifies the (opened) staging repository ID (or multiple ones comma separated) on remote Nexus against which
     * staging action should happen. If not given, mojo will fail.
     * 
     * @parameter expression="${stagingRepositoryId}"
     */
    private String stagingRepositoryId;

    protected String getStagingRepositoryId()
        throws MojoExecutionException
    {
        String result = null;
        if ( stagingRepositoryId != null )
        {
            // explicitly configured either via config or CLI, use that
            result = stagingRepositoryId;
        }
        if ( result == null )
        {
            // try the properties file from the staging folder
            final File stagingRepositoryPropertiesFile =
                new File( getStagingDirectory(), AbstractDeployMojo.STAGING_REPOSITORY_PROPERTY_FILE_NAME );
            // it will exist only if remote staging happened!
            if ( stagingRepositoryPropertiesFile.isFile() )
            {
                final Properties stagingRepositoryProperties = new Properties();
                FileInputStream fis;
                try
                {
                    fis = new FileInputStream( stagingRepositoryPropertiesFile );
                    stagingRepositoryProperties.load( fis );
                    result = stagingRepositoryProperties.getProperty( AbstractDeployMojo.STAGING_REPOSITORY_ID );
                }
                catch ( IOException e )
                {
                    throw new MojoExecutionException(
                        "Unexpected IO exception while loading up staging properties from "
                            + stagingRepositoryPropertiesFile.getAbsolutePath(), e );
                }
            }
        }

        // check did we get any result at all
        if ( result == null || result.trim().length() == 0 )
        {
            throw new MojoExecutionException(
                "The staging repository to operate against is not defined! (use \"-DstagingRepositoryId=foo1,foo2\" on CLI)" );
        }

        return result;
    }
}
