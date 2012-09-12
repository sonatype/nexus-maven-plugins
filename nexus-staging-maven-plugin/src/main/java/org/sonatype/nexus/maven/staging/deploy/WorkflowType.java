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
