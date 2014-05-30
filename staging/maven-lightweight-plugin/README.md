<!--

    Sonatype Nexus (TM) Open Source Version
    Copyright (c) 2007-2013 Sonatype, Inc.
    All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.

    This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
    which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.

    Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
    of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
    Eclipse Foundation. All other trademarks are the property of their respective owners.

-->
# Nexus Lightweight Staging Maven Plugin

Maven Plugin to perform deploys combined with Nexus Staging workflow. While the maven plugin ("staging client") part is OSS, to use staging features it need a Sonatype Nexus Professional instance 2.1+ on the server side!

**Plugin is compatible with Maven 3.2.0 and newer!**

Features:
 * it is meant as lightweight alternative to nexus-staging-maven-plugin
 * interferes minimally with your build
 * sound support for parallel builds
 * only drives the REST API of remote Nexus, does not take over deploys

Limitations:
 * **Experimental** use at own risk
 * the project being built will land in one single staging repository
 * sends an extra status resource request at the beginning of the build (as NexusClient, used in here too, always does)
 * on build failures, the opened staging repository is not cleaned up (it seems Maven 3.2 does not invoke newly added
 afterSessionEnd on lifecycle listener in some cases, see http://jira.codehaus.org/browse/MNG-5640)
 

# Documentation

Just enable this plugin as extension, for example from a profile in your `settings.xml`, set the required properties, and deploy/release as you usually would with Maven!

Required properties: following properties are *required* and must exist in top level project of the POMs (or be defined in a profile in settings).

| Configuration | Meaning |
|---------------|---------|
| `staging.nexusUrl` | *Mandatory*. Has to point to the *base URL of target Nexus*. |
| `staging.serverId` | *Mandatory*. Has to hold an ID of a `<server>` section from Maven's `settings.xml` to pick authentication information from. |
| `staging.profileId` | *Optional*. Has to hold a Nexus Staging Profile ID to stage against. The profile has to exists, and user performing staging has to have access to it. If not given, TLP will be matched for a profile. |

# Behind the curtain

This plugin extension simply install a lifecycle participant, that performs following steps:
 * at build start creates a staging repository on remote Nexus
 * "redirects" all the distributionManagement release repositories to deploy to newly created staging repository
 * at build end closes or drops the repository (depends on outcome, and overall build success)
 
 
 
