/*
 * Copyright (c) 2008-2012 Sonatype, Inc.
 *
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/pro/attributions
 * Sonatype and Sonatype Nexus are trademarks of Sonatype, Inc. Apache Maven is a trademark of the Apache Foundation.
 * M2Eclipse is a trademark of the Eclipse Foundation. All other trademarks are the property of their respective owners.
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
