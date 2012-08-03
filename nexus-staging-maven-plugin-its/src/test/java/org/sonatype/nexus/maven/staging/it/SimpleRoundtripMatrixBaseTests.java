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

import static org.sonatype.sisu.filetasks.builder.FileRef.file;
import static org.sonatype.sisu.filetasks.builder.FileRef.path;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.maven.it.VerificationException;
import org.junit.Test;
import org.sonatype.nexus.bundle.launcher.NexusBundleConfiguration;
import org.sonatype.sisu.filetasks.FileTaskBuilder;

/**
 * Basic tests for Staging V2, using maven 2.2.1 and 3.0.4 on projects set up for maven 2 and 3.
 */
public abstract class SimpleRoundtripMatrixBaseTests
    extends SimpleRoundtripMatrixSupport
{

    /**
     * Project set up in m2-way with m2.
     *
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM2ProjectUsingM2()
        throws VerificationException, IOException
    {
        roundtrip( createMavenVerifier( M2_VERSION, new File( getBasedir(), "target/test-classes/maven2-project" ) ) );
    }

    /**
     * Project set up in m2-way with m3.
     *
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM2ProjectUsingM3()
        throws VerificationException, IOException
    {
        roundtrip( createMavenVerifier( M3_VERSION, new File( getBasedir(), "target/test-classes/maven2-project" ) ) );
    }

    /**
     * Project set up in m3-way using m3.
     * 
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM3ProjectUsingM3()
        throws VerificationException, IOException
    {
        roundtrip( createMavenVerifier( M3_VERSION, new File( getBasedir(), "target/test-classes/maven3-project" ) ) );
    }
}
