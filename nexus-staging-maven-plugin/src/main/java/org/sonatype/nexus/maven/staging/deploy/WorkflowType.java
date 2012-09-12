package org.sonatype.nexus.maven.staging.deploy;

public enum WorkflowType
{
    /**
     * This mode means basically that the plugin will work in "maven-deploy-plugin" mode. At the end of every module
     * build, deploy will happen.
     */
    DIRECT_DEPLOY,

    /**
     * Deferred deploy mode means that deployables will be gathered in local staging repository, and they will get
     * deployed at the end of the reactor build, but no Staging features will be used at all.
     */
    DEFERRED_DEPLOY,

    /**
     * Staging deploy mode means that full staging suite will be used: deployables will be gathered in local staging
     * repository, and they will get staged at the end of the reactor build using StagingV2 features.
     */
    STAGING_DEPLOY;
}
