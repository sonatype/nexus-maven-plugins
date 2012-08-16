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
import com.sonatype.nexus.plugins.smartproxy.internal.messaging.broker.*
import org.sonatype.sisu.goodies.common.ByteSize
import org.sonatype.sisu.goodies.common.Time

// Force typing (for sanity & reference)
def service = (ExtBrokerService)service
def owner = (ExtBrokerServiceFactory)owner

// Enable advisory support
// service.advisorySupport=true

// Disable JMX
// service.useJmx=false

service.plugins = [
    // new org.apache.activemq.broker.util.LoggingBrokerPlugin(),
    // new org.apache.activemq.broker.util.TimeStampingBrokerPlugin(),
    // new org.apache.activemq.plugin.StatisticsBrokerPlugin()
]

// Configure system limits
// service.memoryUsageLimit = ByteSize.megaBytes(64)
// service.tempUsageLimit = ByteSize.gigaBytes(10)
// service.storeUsageLimit = ByteSize.gigaBytes(100)
