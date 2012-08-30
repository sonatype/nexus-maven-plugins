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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.List;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;

/**
 * A simple "wrapper" class that carried the {@link Verifier} but also some extra data about the project being built by
 * Verifier that makes easier the post-build assertions.
 *
 * @author cstamas
 */
public class PreparedVerifier
    extends Verifier
{

    private final String projectGroupId;

    private final String projectArtifactId;

    private final String projectVersion;

    private int numberOfRuns;

    private final String logNameTemplate;

    public PreparedVerifier( final File baseDir,
                             final String projectGroupId, final String projectArtifactId, final String projectVersion,
                             final String logNameTemplate )
        throws VerificationException
    {
        super( checkNotNull( baseDir ).getAbsolutePath(), false );

        this.numberOfRuns = 0;
        this.projectGroupId = checkNotNull( projectGroupId );
        this.projectArtifactId = checkNotNull( projectArtifactId );
        this.projectVersion = checkNotNull( projectVersion );
        this.logNameTemplate = checkNotNull( logNameTemplate );
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

    @Override
    public void executeGoals( final List goals )
        throws VerificationException
    {
        setLogFileName( String.format( logNameTemplate, ++numberOfRuns ) );
        super.executeGoals( goals );
    }

    public int getNumberOfRuns()
    {
        return numberOfRuns;
    }

}
