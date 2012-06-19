package org.sonatype.nexus.plugin.staging;

import org.apache.maven.plugin.MojoExecutionException;
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
     * 
     * @parameter expression="${stagingRepositoryId}"
     */
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
