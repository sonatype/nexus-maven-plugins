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

package org.sonatype.maven.mojo.execution;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginContainer;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;

/**
 * This class in meant to operate on MavenSession, that contains already loaded and interpolated model.
 *
 * @author cstamas
 */
public class MojoExecution
{
  /**
   * Returns true if the current project is located at the Execution Root Directory (from where mvn was launched).
   *
   * @param mavenSession the MavenSession
   * @param basedir      the basedir
   * @return true if execution root equals to the passed in basedir.
   */
  public static boolean isThisTheExecutionRoot(final MavenSession mavenSession, final File basedir) {
    return isThisTheExecutionRoot(mavenSession, basedir.getAbsolutePath());
  }

  /**
   * Returns true if the current project is located at the Execution Root Directory (from where mvn was launched).
   *
   * @param mavenSession the MavenSession
   * @param basedir      the basedir
   * @return true if execution root equals to the passed in basedir.
   */
  public static boolean isThisTheExecutionRoot(final MavenSession mavenSession, final String basedir) {
    return mavenSession.getExecutionRootDirectory().equalsIgnoreCase(basedir);
  }

  /**
   * Returns true if the current project is the last one being executed in this build.
   *
   * @param mavenSession the MavenSession
   * @return true if last project is being built.
   */
  public static boolean isCurrentTheLastProjectInExecution(final MavenSession mavenSession) {
    final MavenProject currentProject = mavenSession.getCurrentProject();
    final MavenProject lastProject =
        mavenSession.getSortedProjects().get(mavenSession.getSortedProjects().size() - 1);

    return currentProject == lastProject;
  }

  /**
   * Returns true if the current project is the first one being executed in this build that has this Mojo defined.
   *
   * @param mavenSession     the MavenSession.
   * @param pluginGroupId    the plugin's groupId.
   * @param pluginArtifactId the plugin's artifactId.
   * @param goal             the goal to search for and have execution or {@code null} if just interested in plugin
   *                         presence.
   * @return true if last project with given plugin is being built.
   */
  public static boolean isCurrentTheFirstProjectWithMojoInExecution(final MavenSession mavenSession,
                                                                    final String pluginGroupId,
                                                                    final String pluginArtifactId, final String goal)
  {
    return mavenSession.getCurrentProject() == getFirstProjectWithMojoInExecution(mavenSession, pluginGroupId,
        pluginArtifactId, goal);
  }

  /**
   * Returns true if the current project is the last one being executed in this build that has this Mojo defined.
   *
   * @param mavenSession     the MavenSession.
   * @param pluginGroupId    the plugin's groupId.
   * @param pluginArtifactId the plugin's artifactId.
   * @param goal             the goal to search for and have execution or {@code null} if just interested in plugin
   *                         presence.
   * @return true if last project with given plugin is being built.
   */
  public static boolean isCurrentTheLastProjectWithMojoInExecution(final MavenSession mavenSession,
                                                                   final String pluginGroupId,
                                                                   final String pluginArtifactId, final String goal)
  {
    return mavenSession.getCurrentProject() == getLastProjectWithMojoInExecution(mavenSession, pluginGroupId,
        pluginArtifactId, goal);
  }

  /**
   * Returns first MavenProject from projects being built that has this Mojo defined.
   *
   * @param mavenSession     the MavenSession.
   * @param pluginGroupId    the plugin's groupId.
   * @param pluginArtifactId the plugin's artifactId.
   * @param goal             the goal to search for and have execution or {@code null} if just interested in plugin
   *                         presence.
   * @return MavenProject of first project with given plugin is being built or null.
   */
  public static MavenProject getFirstProjectWithMojoInExecution(final MavenSession mavenSession,
                                                                final String pluginGroupId,
                                                                final String pluginArtifactId, final String goal)
  {
    final ArrayList<MavenProject> projects = new ArrayList<MavenProject>(mavenSession.getSortedProjects());
    MavenProject firstWithThisMojo = null;
    for (MavenProject project : projects) {
      if (null != findPlugin(project.getBuild(), pluginGroupId, pluginArtifactId, goal)) {
        firstWithThisMojo = project;
        break;
      }
    }
    return firstWithThisMojo;
  }

  /**
   * Returns last MavenProject from projects being built that has this Mojo defined.
   *
   * @param mavenSession     the MavenSession.
   * @param pluginGroupId    the plugin's groupId.
   * @param pluginArtifactId the plugin's artifactId.
   * @param goal             the goal to search for and have execution or {@code null} if just interested in plugin
   *                         presence.
   * @return MavenProject of last project with given plugin is being built or null.
   */
  public static MavenProject getLastProjectWithMojoInExecution(final MavenSession mavenSession,
                                                               final String pluginGroupId,
                                                               final String pluginArtifactId, final String goal)
  {
    final ArrayList<MavenProject> projects = new ArrayList<MavenProject>(mavenSession.getSortedProjects());
    Collections.reverse(projects);
    MavenProject lastWithThisMojo = null;
    for (MavenProject project : projects) {
      if (null != findPlugin(project.getBuild(), pluginGroupId, pluginArtifactId, goal)) {
        lastWithThisMojo = project;
        break;
      }
    }
    return lastWithThisMojo;
  }

  /**
   * Searches for plugin in passed in PluginContainer. If parameter "goal" is {@code null}, it performs GA search
   * only. If parameter "goal" is given, it will search for GA and ensure passed in "goal" is having an execution
   * scheduled.
   *
   * @return the plugin or null if not found.
   */
  public static Plugin findPlugin(final PluginContainer container, final String pluginGroupId,
                                  final String pluginArtifactId, final String goal)
  {
    if (container != null) {
      for (Plugin plugin : container.getPlugins()) {
        if (pluginGroupId.equals(plugin.getGroupId()) && pluginArtifactId.equals(plugin.getArtifactId())) {
          if (goal != null) {
            for (PluginExecution execution : plugin.getExecutions()) {
              if (execution.getGoals().contains(goal)) {
                return plugin;
              }
            }
          }
          else {
            return plugin;
          }
        }
      }
    }
    return null;
  }
}
