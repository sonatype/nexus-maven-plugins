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

import java.io.File;

import org.apache.maven.artifact.Artifact;

import com.google.common.base.Preconditions;

/**
 * Encapsulation of a deployable "pair": the {@link File} and the {@link Artifact}.
 * 
 * @author cstamas
 * @since 1.1
 */
public class DeployableArtifact
{
    private final File file;

    private final Artifact artifact;

    public DeployableArtifact( final File file, final Artifact artifact )
    {
        this.file = Preconditions.checkNotNull( file, "DeployableArtifact.file is null!" );
        this.artifact = Preconditions.checkNotNull( artifact, "DeployableArtifact.artifact is null!" );
    }

    public File getFile()
    {
        return file;
    }

    public Artifact getArtifact()
    {
        return artifact;
    }
}
