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
package org.sonatype.nexus.maven.staging.deploy.strategy;

import java.io.File;
import java.util.Map;

import org.codehaus.plexus.util.StringUtils;
import com.google.common.base.Preconditions;

public class StagingParametersImpl
    extends ParametersImpl
    implements StagingParameters
{

    private final String nexusUrl;

    private final String serverId;

    private final boolean keepStagingRepositoryOnCloseRuleFailure;

    private final boolean keepStagingRepositoryOnFailure;

    private final boolean skipStagingRepositoryClose;

    private final String stagingProfileId;

    private final String stagingRepositoryId;

    private final String userDescriptionOfAction;

    private final Map<String, String> tags;

    public StagingParametersImpl( String pluginGav, String nexusUrl, String serverId, File stagingDirectoryRoot,
        boolean keepStagingRepositoryOnCloseRuleFailure, boolean keepStagingRepositoryOnFailure,
        boolean skipStagingRepositoryClose, String stagingProfileId, String stagingRepositoryId,
        String userDescriptionOfAction, Map<String, String> tags )
    {
        super( pluginGav, stagingDirectoryRoot );
        this.nexusUrl = Preconditions.checkNotNull( nexusUrl );
        this.serverId = Preconditions.checkNotNull( serverId );
        this.keepStagingRepositoryOnCloseRuleFailure = keepStagingRepositoryOnCloseRuleFailure;
        this.keepStagingRepositoryOnFailure = keepStagingRepositoryOnFailure;
        this.skipStagingRepositoryClose = skipStagingRepositoryClose;
        this.stagingProfileId = stagingProfileId;
        this.stagingRepositoryId = stagingRepositoryId;
        this.userDescriptionOfAction = userDescriptionOfAction;
        this.tags = tags;
    }

    @Override
    public String getNexusUrl()
    {
        return nexusUrl;
    }

    @Override
    public String getServerId()
    {
        return serverId;
    }

    @Override
    public boolean isKeepStagingRepositoryOnCloseRuleFailure()
    {
        return keepStagingRepositoryOnCloseRuleFailure;
    }

    @Override
    public boolean isKeepStagingRepositoryOnFailure()
    {
        return keepStagingRepositoryOnFailure;
    }

    @Override
    public boolean isSkipStagingRepositoryClose()
    {
        return skipStagingRepositoryClose;
    }

    @Override
    public String getStagingProfileId()
    {
        return stagingProfileId;
    }

    @Override
    public String getStagingRepositoryId()
    {
        return stagingRepositoryId;
    }

    @Override
    public String getUserDescriptionOfAction()
    {
        return userDescriptionOfAction;
    }

    public Map<String, String> getTags()
    {
        return tags;
    }

    @Override
    public String getDefaultedUserDescriptionOfAction( String action )
    {
        String result = getUserDescriptionOfAction();
        if ( StringUtils.isBlank( result ) )
        {
            result = action + " by " + getPluginGav();
        }
        return result;
    }

    @Override
    public String toString()
    {
        return "ParametersImpl{" +
            "pluginGav='" + getPluginGav() + '\'' +
            ", nexusUrl='" + nexusUrl + '\'' +
            ", serverId='" + serverId + '\'' +
            ", stagingDirectoryRoot=" + getStagingDirectoryRoot() +
            ", keepStagingRepositoryOnCloseRuleFailure=" + keepStagingRepositoryOnCloseRuleFailure +
            ", keepStagingRepositoryOnFailure=" + keepStagingRepositoryOnFailure +
            ", skipStagingRepositoryClose=" + skipStagingRepositoryClose +
            ", stagingProfileId='" + stagingProfileId + '\'' +
            ", stagingRepositoryId='" + stagingRepositoryId + '\'' +
            ", userDescriptionOfAction='" + userDescriptionOfAction + '\'' +
            ", tags=" + tags +
            '}';
    }
}
