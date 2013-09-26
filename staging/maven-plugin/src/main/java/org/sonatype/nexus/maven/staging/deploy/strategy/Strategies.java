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

package org.sonatype.nexus.maven.staging.deploy.strategy;

/**
 * "Registry" of default strategies.
 *
 * @author cstamas
 * @since 1.1
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
   * Performs an "image" upload (directory contents are uploades as-is, as "image" of stuff you prepared), and
   * employs
   * Nexus REST Calls to drive the staging workflow. Also, behaves as "atomic" upload.
   */
  String IMAGE = "image";
}
