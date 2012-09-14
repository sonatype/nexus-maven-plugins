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

import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.sonatype.nexus.maven.staging.AbstractStagingMojo;
import org.sonatype.nexus.maven.staging.deploy.strategy.DeployStrategy;
import org.sonatype.nexus.maven.staging.deploy.strategy.Parameters;

//import com.sun.jersey.api.client.UniformInterfaceException;

/**
 * Abstract class for deploy related mojos.
 * 
 * @author cstamas
 * @since 1.0
 */
public abstract class AbstractDeployMojo
    extends AbstractStagingMojo
{
    /**
     * Deploy strategies.
     * 
     * @component role="org.sonatype.nexus.maven.staging.deploy.strategy.DeployStrategy"
     */
    private Map<String, DeployStrategy> deployStrategies;

    // User configurable parameters

    /**
     * Specifies the profile ID on remote Nexus against which staging should happen. If not given, Nexus will be asked
     * to perform a "match" and that profile will be used.
     * 
     * @parameter expression="${stagingProfileId}"
     */
    private String stagingProfileId;

    /**
     * Specifies the (opened) staging repository ID on remote Nexus against which staging should happen. If not given,
     * Nexus will be asked to create one for us and that will be used.
     * 
     * @parameter expression="${stagingRepositoryId}"
     */
    private String stagingRepositoryId;

    /**
     * The key-value pairs to "tag" the staging repository.
     * 
     * @parameter
     */
    private Map<String, String> tags;

    /**
     * Controls whether the plugin remove or keep the staging repository that performed an IO exception during upload,
     * hence, it's contents are partial Defaults to {{false}}. If {{true}}, even in case of upload failure, the staging
     * repository (with partial content) will be left as is, left to the user to do whatever he wants.
     * 
     * @parameter expression="${keepStagingRepositoryOnFailure}"
     */
    private boolean keepStagingRepositoryOnFailure = false;

    /**
     * Set this to {@code true} to bypass staging repository closing at the workflow end.
     * 
     * @parameter expression="${skipStagingRepositoryClose}"
     */
    private boolean skipStagingRepositoryClose = false;

    /**
     * Set this to {@code true} to bypass staging features, and use deferred deploy features only.
     * 
     * @parameter expression="${deferredDeployOnly}"
     */
    private boolean deferredDeployOnly = false;

    // ==

    protected DeployStrategy getDeployStrategy( final String key )
        throws MojoExecutionException
    {
        final DeployStrategy deployStrategy = deployStrategies.get( key );
        if ( deployStrategy == null )
        {
            throw new MojoExecutionException( "DeployStrategy " + key + " not found!" );
        }
        return deployStrategy;
    }

    protected abstract Parameters buildParameters()
        throws MojoExecutionException;

    // ==

    protected String getStagingProfileId()
    {
        return stagingProfileId;
    }

    protected String getStagingRepositoryId()
    {
        return stagingRepositoryId;
    }

    protected Map<String, String> getTags()
    {
        return tags;
    }

    protected boolean isKeepStagingRepositoryOnFailure()
    {
        return keepStagingRepositoryOnFailure;
    }

    protected boolean isSkipStagingRepositoryClose()
    {
        return skipStagingRepositoryClose;
    }

    protected boolean isDeferredDeployOnly()
    {
        return deferredDeployOnly;
    }
}