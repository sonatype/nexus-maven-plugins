/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2015 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.maven.mojo.sisu;

import com.google.inject.AbstractModule;
import org.codehaus.plexus.PlexusContainer;

class PlexusBridgeModule
    extends AbstractModule
{
  private final PlexusContainer plexus;

  private final PlexusKey[] plexusKeys;

  public PlexusBridgeModule(final PlexusContainer plexus, final PlexusKey... plexusKeys) {
    this.plexus = plexus;
    this.plexusKeys = plexusKeys;
  }

  @Override
  protected void configure() {
    for (PlexusKey key : plexusKeys) {
      try {
        bind(key.getRole()).toInstance(lookup(key.getRole(), key.getHint()));
      }
      catch (Exception e) {
        addError(e);
      }
    }
  }

  private <T> T lookup(Class<T> type, String hint)
      throws Exception
  {
    return type.cast(plexus.lookup(type.getName(), hint));
  }
}