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

package org.sonatype.nexus.maven.m2settings;

import org.sonatype.nexus.client.core.exception.NexusClientException;

import com.google.common.base.Throwables;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import static com.google.common.base.Preconditions.checkState;

/**
 * Support for {@link Mojo} implementations.
 *
 * @since 1.4
 */
public abstract class MojoSupport
    extends AbstractMojo
{
  protected final MojoLogger log = new MojoLogger(this);

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    checkState(getLog() != null, "Mojo.log not installed");
    try {
      doExecute();
    }
    catch (Exception e) {
      Throwables.propagateIfPossible(e, MojoExecutionException.class, MojoFailureException.class);
      throw Throwables.propagate(e);
    }
    finally {
      doCleanup();
    }
  }

  /**
   * Fail execution.
   */
  protected Exception fail(final String message) throws Exception {
    log.debug("Failing: {}", message);
    throw new MojoExecutionException(message);
  }

  /**
   * Fail execution and try to clean up cause hierarchy.
   */
  protected Exception fail(final String message, Throwable cause) throws Exception {
    log.debug("Failing: {}", message, cause);

    // Try to decode exception stack for more meaningful and terse error messages
    if (cause instanceof NexusClientException) {
      cause = cause.getCause();

      // FIXME: This should probably be handled by the nexus-client jersey adapter
      if (cause instanceof com.sun.jersey.api.client.ClientHandlerException) {
        cause = cause.getCause();
      }

      // TODO: decode anything else?
    }

    throw new MojoExecutionException(message, cause);
  }

  protected abstract void doExecute() throws Exception;

  protected abstract void doCleanup();
}
