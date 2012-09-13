package org.sonatype.nexus.maven.staging.deploy.strategy;

/**
 * "Registry" of known, default strategies.
 * 
 * @author cstamas
 */
public interface Strategies
{
    /**
     * Performs a direct deploy, same as "maven-deploy-plugin".
     */
    String DIRECT = "direct";

    /**
     * Performs a deferred deploy, where deployable artifacts are gathered locally thruout the build, and deployed
     * remotely only at the end of the build.
     */
    String DEFERRED = "deferred";

    /**
     * Similar to deferred, but staging features are used "around" the deploy, and Nexus REST calls drive the staging
     * workflow (creating a staging repo, closing or dropping, etc). Due to these (deferred + staging), this results
     * actually in "atomic" deploy.
     */
    String STAGING = "staging";

    /**
     * Performs an "image" upload (directory contents are uploades as-is, as "image" of stuff you prepared), and employs
     * Nexus REST Calls to drive the staging workflow. Also, behaves as "atomic" upload.
     */
    String IMAGE = "image";
}
