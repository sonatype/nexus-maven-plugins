package org.sonatype.nexus.maven.staging.deploy.strategy;

import java.io.File;
import java.util.Map;

public interface Parameters
{
    String getPluginGav();

    String getNexusUrl();

    String getServerId();

    File getStagingDirectoryRoot();
    
    boolean isKeepStagingRepositoryOnCloseRuleFailure();

    boolean isKeepStagingRepositoryOnFailure();

    boolean isSkipStagingRepositoryClose();

    String getStagingProfileId();

    String getStagingRepositoryId();

    String getUserDescriptionOfAction();

    Map<String, String> getTags();

    String getDefaultedUserDescriptionOfAction( String action );
}
