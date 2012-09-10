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
package org.sonatype.nexus.maven.staging.deploy;

import java.util.List;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginContainer;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Lifecycle participant that is meant to kick in Maven3 to lessen the needed POM changes. It will silently "scan" the
 * projects, disable all executions of maven-deploy-plugin, and "install" itself instead. It will NOT kick in if detects
 * and <b>bounded execution</b> of himself anywhere in the projects being built, assuming the "Maven2 way" is applied
 * then (by manual editing of POMs, and configuring all by hand).
 * 
 * @author cstamas
 */
@Component( role = AbstractMavenLifecycleParticipant.class, hint = "org.sonatype.nexus.maven.staging.deploy.DeployLifecycleParticipant" )
public class DeployLifecycleParticipant
    extends AbstractMavenLifecycleParticipant
    implements LogEnabled
{
    /**
     * We have to have some access to GAV of the plugin, but lifecycle participant is loaded up very early, so we cannot
     * use MojoExecutions (actually, we do not even have one in scope) and other Maven Core stuff to get them. Any way
     * to discover these?
     */
    public static String _THIS_GROUP_ID = "org.sonatype.plugins";

    /**
     * We have to have some access to GAV of the plugin, but lifecycle participant is loaded up very early, so we cannot
     * use MojoExecutions (actually, we do not even have one in scope) and other Maven Core stuff to get them. Any way
     * to discover these?
     */
    public static String _THIS_ARTIFACT_ID = "nexus-staging-maven-plugin";

    public static String MAVEN_DEPLOY_PLUGIN_GROUP_ID = "org.apache.maven.plugins";

    public static String MAVEN_DEPLOY_PLUGIN_ARTIFACT_ID = "maven-deploy-plugin";

    private Logger logger;

    protected String getPluginGroupId()
    {
        // TODO: GA is wired in, should be discovered!
        return _THIS_GROUP_ID;
    }

    protected String getPluginArtifactId()
    {
        // TODO: GA is wired in, should be discovered!
        return _THIS_ARTIFACT_ID;
    }

    @Override
    public void enableLogging( final Logger logger )
    {
        this.logger = logger;
    }

    public void afterProjectsRead( final MavenSession session )
        throws MavenExecutionException
    {
        try
        {
            final int totalModules = session.getProjects().size();
            logger.info( "Inspecting build with total of " + totalModules + " modules..." );

            int stagingGoalsFoundInModules = 0;
            // check do we need to do anything at all?
            // should not find any nexus-staging-maven-plugin deploy goal executions in any project
            // otherwise, assume it's "manually done"
            for ( MavenProject project : session.getProjects() )
            {
                final Plugin nexusMavenPlugin = getBuildPluginsNexusMavenPlugin( project.getModel() );
                if ( nexusMavenPlugin != null )
                {
                    if ( !nexusMavenPlugin.getExecutions().isEmpty() )
                    {
                        for ( PluginExecution pluginExecution : nexusMavenPlugin.getExecutions() )
                        {
                            final List<String> goals = pluginExecution.getGoals();
                            if ( goals.contains( "deploy" ) || goals.contains( "deploy-staged" )
                                || goals.contains( "staging-close" ) || goals.contains( "staging-release" )
                                || goals.contains( "staging-promote" ) )
                            {
                                stagingGoalsFoundInModules++;
                                break;
                            }
                        }
                    }
                }
            }

            if ( stagingGoalsFoundInModules > 0 )
            {
                logger.info( "Not installing Nexus Staging features:" );
                if ( stagingGoalsFoundInModules > 0 )
                {
                    logger.info( " * Preexisting staging related goal bindings found in " + stagingGoalsFoundInModules
                        + " modules." );
                }
                return;
            }

            logger.info( "Installing Nexus Staging features:" );

            // make maven-deploy-plugin to be skipped and install us instead
            int skipped = 0;
            for ( MavenProject project : session.getProjects() )
            {
                final Plugin nexusMavenPlugin = getBuildPluginsNexusMavenPlugin( project.getModel() );
                if ( nexusMavenPlugin != null )
                {
                    // skip the maven-deploy-plugin
                    final Plugin mavenDeployPlugin = getBuildPluginsMavenDeployPlugin( project.getModel() );
                    if ( mavenDeployPlugin != null )
                    {
                        // TODO: better would be to remove them targeted?
                        // But this mojo has only 3 goals, but only one of them is usable in builds ("deploy")
                        mavenDeployPlugin.getExecutions().clear();

                        // add executions to nexus-staging-maven-plugin
                        final PluginExecution execution = new PluginExecution();
                        execution.setId( "injected-nexus-deploy" );
                        execution.getGoals().add( "deploy" );
                        execution.setPhase( "deploy" );
                        execution.setConfiguration( nexusMavenPlugin.getConfiguration() );
                        nexusMavenPlugin.getExecutions().add( execution );

                        // count this in
                        skipped++;
                    }
                }
            }
            if ( skipped > 0 )
            {
                logger.info( "  ... total of " + skipped + " executions of maven-deploy-plugin replaced with "
                    + getPluginArtifactId() );
            }
        }
        catch ( IllegalStateException e )
        {
            // thrown by getPluginByGAFromContainer
            throw new MavenExecutionException( e.getMessage(), e );
        }
    }

    /**
     * Returns the nexus-staging-maven-plugin from build/plugins section of model or {@code null} if not present.
     * 
     * @param model
     * @return
     */
    protected Plugin getBuildPluginsNexusMavenPlugin( final Model model )
    {
        if ( model.getBuild() != null )
        {
            return getNexusMavenPluginFromContainer( model.getBuild() );
        }
        return null;
    }

    /**
     * Returns the maven-deploy-plugin from build/plugins section of model or {@code null} if not present.
     * 
     * @param model
     * @return
     */
    protected Plugin getBuildPluginsMavenDeployPlugin( final Model model )
    {
        if ( model.getBuild() != null )
        {
            return getMavenDeployPluginFromContainer( model.getBuild() );
        }
        return null;
    }

    /**
     * Returns the nexus-staging-maven-plugin from pluginContainer or {@code null} if not present.
     * 
     * @param pluginContainer
     * @return
     */
    protected Plugin getNexusMavenPluginFromContainer( final PluginContainer pluginContainer )
    {
        return getPluginByGAFromContainer( getPluginGroupId(), getPluginArtifactId(), pluginContainer );
    }

    /**
     * Returns the maven-deploy-plugin from pluginContainer or {@code null} if not present.
     * 
     * @param pluginContainer
     * @return
     */
    protected Plugin getMavenDeployPluginFromContainer( final PluginContainer pluginContainer )
    {
        return getPluginByGAFromContainer( MAVEN_DEPLOY_PLUGIN_GROUP_ID, MAVEN_DEPLOY_PLUGIN_ARTIFACT_ID,
            pluginContainer );
    }

    // ==

    protected void setPluginScalarConfigurationValueEverywhere( final String key, final String value,
                                                                final boolean override, final Plugin plugin )
    {
        setPluginScalarConfigurationValueInAllExecutions( key, value, override, plugin );
        Xpp3Dom pluginConfiguration =
            setPluginScalarConfigurationValue( key, value, override, (Xpp3Dom) plugin.getConfiguration() );
        plugin.setConfiguration( pluginConfiguration );
    }

    protected void setPluginScalarConfigurationValueInAllExecutions( final String key, final String value,
                                                                     final boolean override, final Plugin plugin )
    {
        for ( PluginExecution execution : plugin.getExecutions() )
        {
            Xpp3Dom executionConfiguration =
                setPluginScalarConfigurationValue( key, value, override, (Xpp3Dom) execution.getConfiguration() );
            execution.setConfiguration( executionConfiguration );
        }
    }

    protected Xpp3Dom setPluginScalarConfigurationValue( final String key, final String value, final boolean override,
                                                         final Xpp3Dom originalConfiguration )
    {
        Xpp3Dom configuration = originalConfiguration;
        if ( configuration == null )
        {
            configuration = new Xpp3Dom( "configuration" );
        }
        Xpp3Dom changed = configuration.getChild( key );
        if ( changed != null && !override )
        {
            return originalConfiguration; // is present, we do not override it
        }
        if ( changed == null )
        {
            changed = new Xpp3Dom( key );
            configuration.addChild( changed );
        }
        changed.setValue( value );
        return configuration;
    }

    protected Plugin getPluginByGAFromContainer( final String groupId, final String artifactId,
                                                 final PluginContainer pluginContainer )
    {
        Plugin result = null;
        for ( Plugin plugin : pluginContainer.getPlugins() )
        {
            if ( StringUtils.equals( groupId, plugin.getGroupId() )
                && StringUtils.equals( artifactId, plugin.getArtifactId() ) )
            {
                if ( result != null )
                {
                    throw new IllegalStateException( "The build contains multiple versions of plugin " + groupId + ":"
                        + artifactId );
                }
                result = plugin;
            }

        }
        return result;
    }
}
