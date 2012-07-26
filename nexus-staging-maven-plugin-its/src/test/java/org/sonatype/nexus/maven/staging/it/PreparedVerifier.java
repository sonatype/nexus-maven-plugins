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
package org.sonatype.nexus.maven.staging.it;

import java.io.File;

import org.apache.maven.it.Verifier;

import com.google.common.base.Preconditions;

/**
 * A simple "wrapper" class that carried the {@link Verifier} but also some extra data about the project being built by
 * Verifier that makes easier the post-build assertions.
 * 
 * @author cstamas
 */
public class PreparedVerifier
{
    private final Verifier verifier;

    private final File baseDir;

    private final String projectGroupId;

    private final String projectArtifactId;

    private final String projectVersion;

    public PreparedVerifier( final Verifier verifier, final File baseDir, final String projectGroupId,
                             final String projectArtifactId, final String projectVersion )
    {
        this.verifier = Preconditions.checkNotNull( verifier );
        this.baseDir = Preconditions.checkNotNull( baseDir );
        this.projectGroupId = Preconditions.checkNotNull( projectGroupId );
        this.projectArtifactId = Preconditions.checkNotNull( projectArtifactId );
        this.projectVersion = Preconditions.checkNotNull( projectVersion );
    }

    public Verifier getVerifier()
    {
        return verifier;
    }

    public File getBaseDir()
    {
        return baseDir;
    }

    public String getProjectGroupId()
    {
        return projectGroupId;
    }

    public String getProjectArtifactId()
    {
        return projectArtifactId;
    }

    public String getProjectVersion()
    {
        return projectVersion;
    }
}
