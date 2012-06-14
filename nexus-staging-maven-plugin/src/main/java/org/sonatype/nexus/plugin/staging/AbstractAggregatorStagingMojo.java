package org.sonatype.nexus.plugin.staging;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.nexus.client.srv.staging.StagingWorkflowService;
import org.sonatype.nexus.plugin.AbstractStagingMojo;

import com.sun.jersey.api.client.UniformInterfaceException;

public abstract class AbstractAggregatorStagingMojo
    extends AbstractStagingMojo
{
    /**
     * Specifies the (opened) staging repository ID (or multiple ones comma separated) on remote Nexus against which
     * staging action should happen. If not given, mojo will fail.
     * 
     * @parameter expression="${stagingRepositoryId}"
     */
    private String stagingRepositoryId;

    protected String[] getStagingRepositoryId()
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

        return null;
    }

    @Override
    public final void execute()
        throws MojoExecutionException, MojoFailureException
    {
        createTransport( getNexusUrl() );
        createNexusClient();

        final StagingWorkflowService stagingWorkflow = getStagingWorkflowService();

        try
        {
            doExecute( stagingWorkflow );
        }
        catch ( UniformInterfaceException e )
        {
            // dump the response until no smarter error handling
            getLog().error( e.getResponse().getEntity( String.class ) );
            // fail the build
            throw new MojoExecutionException( "Could not perform action against staging repository with ID="
                + getStagingRepositoryId() + ": " + e.getResponse().getClientResponseStatus().getReasonPhrase(), e );
        }
    }

    protected abstract void doExecute( final StagingWorkflowService stagingWorkflow )
        throws MojoExecutionException, MojoFailureException;
}
