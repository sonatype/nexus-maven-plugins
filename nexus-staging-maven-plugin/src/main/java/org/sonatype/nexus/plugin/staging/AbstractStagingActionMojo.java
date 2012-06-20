package org.sonatype.nexus.plugin.staging;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.sonatype.nexus.client.srv.staging.StagingWorkflowV2Service;
import org.sonatype.nexus.plugin.AbstractStagingMojo;

import com.sun.jersey.api.client.UniformInterfaceException;

/**
 * Super class of non-RC Actions. These mojos are "plain" non-aggregator ones, and will use the property file from
 * staged repository to get the repository ID they need. This way, you can integrate these mojos in your build directly
 * (ie. to release or promote even).
 * 
 * @author cstamas
 */
public abstract class AbstractStagingActionMojo
    extends AbstractStagingMojo
{
    @Override
    public final void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( shouldExecute() )
        {
            createTransport( getNexusUrl() );
            createNexusClient();

            final StagingWorkflowV2Service stagingWorkflow = getStagingWorkflowService();

            try
            {
                doExecute( stagingWorkflow );
            }
            catch ( UniformInterfaceException e )
            {
                // dump the response until no smarter error handling
                getLog().error( e.getResponse().getEntity( String.class ) );
                // fail the build
                throw new MojoExecutionException( "Could not perform action: "
                    + e.getResponse().getClientResponseStatus().getReasonPhrase(), e );
            }
        }
        else
        {
            getLog().info( "Execution skipped..." );
        }
    }

    protected boolean shouldExecute()
    {
        return true;
    }

    protected abstract void doExecute( final StagingWorkflowV2Service stagingWorkflow )
        throws MojoExecutionException, MojoFailureException;
}
