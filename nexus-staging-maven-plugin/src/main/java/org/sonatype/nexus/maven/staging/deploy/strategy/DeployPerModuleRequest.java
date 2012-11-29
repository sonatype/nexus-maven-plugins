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
package org.sonatype.nexus.maven.staging.deploy.strategy;

import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.sonatype.nexus.maven.staging.deploy.DeployableArtifact;

import com.google.common.base.Preconditions;

public class DeployPerModuleRequest
    extends AbstractDeployRequest
{
    private final List<DeployableArtifact> deployableArtifacts;

    public DeployPerModuleRequest( final MavenSession mavenSession, final Parameters parameters,
                                   final List<DeployableArtifact> deployableArtifacts )
    {
        super( mavenSession, parameters );
        this.deployableArtifacts = Preconditions.checkNotNull( deployableArtifacts );
    }

    public List<DeployableArtifact> getDeployableArtifacts()
    {
        return deployableArtifacts;
    }
}
