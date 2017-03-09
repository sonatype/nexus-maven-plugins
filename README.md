<!--

    Sonatype Nexus (TM) Open Source Version
    Copyright (c) 2007-2015 Sonatype, Inc.
    All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.

    This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
    which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.

    Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
    of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
    Eclipse Foundation. All other trademarks are the property of their respective owners.

-->
# Nexus Maven Plugins

Collection of Apache Maven plugins supporting Nexus Suite. 

## Plugins available

* [Nexus Staging Plugin](https://github.com/sonatype/nexus-maven-plugins/tree/master/staging/maven-plugin) - Maven Plugin to perform Sonatype Nexus Staging Workflow steps from your build. 
* [Nexus M2Settings Maven Plugin](https://github.com/sonatype/nexus-maven-plugins/tree/master/m2settings/maven-plugin) - Maven Plugin to download a settings.xml file templated stored in Nexus and potentially replace user token values.

## Runtime requirements

Unless noted otherwise in corresponding plugin documentation, following are the minimal requirements:

* Apache Maven: supported versions are 2.2.1 or 3.0.4+
* Java: Java 1.6+

## Issue tracking

Please use Sonatype JIRA to report issues (Component: Build Tooling) and *please specify plugin version you use and Maven version you use it with*.

https://issues.sonatype.org/browse/NEXUS

## Warning

Please note that the Nexus Maven Plugin is deprecated and replaced by the plugins in this project.
