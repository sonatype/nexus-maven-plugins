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

import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

public class ParametersImpl
    implements Parameters
{
  private final String pluginGav;

  private final File deferredDirectoryRoot;

  private final File stagingDirectoryRoot;

  public ParametersImpl(final String pluginGav, final File deferredDirectoryRoot, final File stagingDirectoryRoot) {
    this.pluginGav = checkNotNull(pluginGav);
    this.deferredDirectoryRoot = checkNotNull(deferredDirectoryRoot);
    this.stagingDirectoryRoot = checkNotNull(stagingDirectoryRoot);
  }

  @Override
  public String getPluginGav() {
    return pluginGav;
  }

  @Override
  public File getDeferredDirectoryRoot() {
    return deferredDirectoryRoot;
  }

  @Override
  public File getStagingDirectoryRoot() {
    return stagingDirectoryRoot;
  }

  @Override
  public String toString() {
    return "ParametersImpl{" +
        "pluginGav='" + pluginGav + '\'' +
        ", deferredDirectoryRoot=" + deferredDirectoryRoot +
        ", stagingDirectoryRoot=" + stagingDirectoryRoot +
        '}';
  }
}
