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
package org.sonatype.nexus.maven.staging.it.nxcm4527;

import org.apache.maven.it.VerificationException;
import org.sonatype.nexus.maven.staging.it.PreparedVerifier;

/**
 * See NXCM-4527, this IT implements it's verification part for Nexus Staging Maven Plugin side when the defaults are
 * overridden. Similar to {@link NXCM4527DropOnCloseRuleFailureIT} IT, but here we assert that staging repository is NOT
 * dropped, it should still exists.
 * 
 * @author cstamas
 */
public class NXCM4527DropOnCloseRuleFailureOverrideIT
    extends NXCM4527DropOnCloseRuleFailureIT
{

    public NXCM4527DropOnCloseRuleFailureOverrideIT( final String nexusBundleCoordinates )
    {
        super( nexusBundleCoordinates );
    }

    /**
     * Validates nexus side of affairs post maven invocations.
     */
    @Override
    protected void postNexusAssertions( final PreparedVerifier verifier )
    {
        assertOverrides( verifier );
    }

    @Override
    protected void invokeMaven( final PreparedVerifier verifier )
        throws VerificationException
    {
        verifier.addCliOption( "-DkeepStagingRepositoryOnCloseRuleFailure=true" );
        super.invokeMaven( verifier );
    }
}