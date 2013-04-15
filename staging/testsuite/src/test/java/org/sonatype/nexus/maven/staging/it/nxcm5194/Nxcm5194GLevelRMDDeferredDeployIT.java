/*
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
package org.sonatype.nexus.maven.staging.it.nxcm5194;

import java.io.File;
import java.io.IOException;

import org.apache.maven.it.VerificationException;
import org.sonatype.nexus.maven.staging.it.PreparedVerifier;

/**
 * IT for https://issues.sonatype.org/browse/NXCM-5194
 * <p>
 * It builds Snapshot of Maven Plugin, hence uses Deferred Deploy to deploy them directly into "snapshots" repo.
 * 
 * @author cstamas
 */
public class Nxcm5194GLevelRMDDeferredDeployIT
    extends Nxcm5194GLevelRepositoryMetadataSupport
{

    public Nxcm5194GLevelRMDDeferredDeployIT( final String nexusBundleCoordinates )
    {
        super( nexusBundleCoordinates );
    }

    // ==

    @Override
    protected PreparedVerifier createMavenVerifier( final String mavenVersion, final File projectDirectory )
        throws VerificationException, IOException
    {
        return createMavenVerifier( getClass().getSimpleName(), mavenVersion,
            testData().resolveFile( "preset-nexus-maven-settings.xml" ), projectDirectory, "1.0-SNAPSHOT" );
    }

    @Override
    protected String getTargetedRepositoryId()
    {
        return "snapshots";
    }
}
