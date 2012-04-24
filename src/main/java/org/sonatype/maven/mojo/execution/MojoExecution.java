package org.sonatype.maven.mojo.execution;

import java.util.ArrayList;
import java.util.Collections;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginContainer;
import org.apache.maven.project.MavenProject;

public class MojoExecution
{
    /**
     * Returns true if the current project is located at the Execution Root Directory (where mvn was launched).
     * 
     * @return true if execution root.
     */
    public static boolean isThisTheExecutionRoot( final MavenSession mavenSession, final String basedir )
    {
        return mavenSession.getExecutionRootDirectory().equalsIgnoreCase( basedir );
    }

    /**
     * Returns true if the current project is the last one being executed in this build.
     * 
     * @return true if last project is being built.
     */
    public static boolean isThisLastProjectInExecution( final MavenSession mavenSession )
    {
        final MavenProject currentProject = mavenSession.getCurrentProject();
        final MavenProject lastProject =
            mavenSession.getSortedProjects().get( mavenSession.getSortedProjects().size() - 1 );

        return currentProject == lastProject;
    }

    /**
     * Returns true if the current project is the last one being executed in this build that has this Mojo defined.
     * 
     * @return true if last project with given plugin is being built.
     */
    public static boolean isThisLastProjectWithMojoInExecution( final MavenSession mavenSession,
                                                                final String pluginGroupId,
                                                                final String pluginArtifactId )
    {
        final ArrayList<MavenProject> projects = new ArrayList<MavenProject>( mavenSession.getSortedProjects() );
        Collections.reverse( projects );
        MavenProject lastWithThisMojo = null;
        for ( MavenProject project : projects )
        {
            if ( null != findPlugin( project.getBuild(), pluginGroupId, pluginArtifactId ) )
            {
                lastWithThisMojo = project;
                break;
            }
        }
        return mavenSession.getCurrentProject() == lastWithThisMojo;
    }

    /**
     * Searches for plugin in passed in PluginContainer.
     * 
     * @param container
     * @param pluginGroupId
     * @param pluginArtifactId
     * @return the plugin or null if not found.
     */
    public static Plugin findPlugin( final PluginContainer container, final String pluginGroupId,
                                     final String pluginArtifactId )
    {
        if ( container != null )
        {
            for ( Plugin plugin : container.getPlugins() )
            {
                if ( pluginGroupId.equals( plugin.getGroupId() ) && pluginArtifactId.equals( plugin.getArtifactId() ) )
                {
                    return plugin;
                }
            }
        }
        return null;
    }
}
