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

package org.sonatype.nexus.maven.staging.deploy;

import com.sonatype.nexus.staging.client.Profile;

import com.google.common.base.Preconditions;

/**
 * Encapsulation of Nexus side staging repository.
 *
 * @author cstamas
 */
public class StagingRepository
{
  private final Profile profile;

  private final String repositoryId;

  private final String url;

  private final boolean managed;

  public StagingRepository(final Profile profile, final String repositoryId, final String url, final boolean managed) {
    this.profile = Preconditions.checkNotNull(profile);
    this.repositoryId = Preconditions.checkNotNull(repositoryId);
    this.url = Preconditions.checkNotNull(url);
    this.managed = managed;
  }

  public Profile getProfile() {
    return profile;
  }

  public String getRepositoryId() {
    return repositoryId;
  }

  public String getUrl() {
    return url;
  }

  public boolean isManaged() {
    return managed;
  }
}
