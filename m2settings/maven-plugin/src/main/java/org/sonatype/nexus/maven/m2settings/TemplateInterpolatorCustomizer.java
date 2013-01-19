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
package org.sonatype.nexus.maven.m2settings;

import org.codehaus.plexus.interpolation.Interpolator;
import org.sonatype.nexus.client.core.NexusClient;

/**
 * Allows customization of the template {@link Interpolator}.
 *
 * @since 1.4
 */
public interface TemplateInterpolatorCustomizer
{
    // TODO: Maybe add a context object to wrap client + anything else?

    void customize(NexusClient client, Interpolator interpolator);
}
