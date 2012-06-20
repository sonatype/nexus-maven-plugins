package org.sonatype.nexus.plugin.staging;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
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

    /**
     * Execute only in last module (to not drop/release same repo over and over, as many times as modules exist in
     * project).
     */
    @Override
    protected boolean shouldExecute()
    {
        return isThisLastProjectWithThisPluginInExecution();
    }
}
