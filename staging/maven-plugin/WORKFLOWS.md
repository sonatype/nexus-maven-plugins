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
# Staging Workflows

Maven Plugin to control Nexus Staging supports several "scenarios" or "workflows" to perform staging. 
These are:

* [One Shot](#one-shot)
* [Two Shots](#two-shots)

As for profile selection multiple strategies might be used

* [Server Side Matching](#server-side-matching)
* [Targeted Profile](#targeted-profile)

One special (advanced!) mode that takes over control on both, workflow and staging repository management:

* [Targeted Repository](#targeted-repository)

Finally, some actions you might perform post staging:

* [Post Staging](#post-staging)


## One Shot

This is the simplest, most used, and _recommended_ way to use Staging. "One Shot" refers to case where Maven invocation
results as one or more (closed) staging repository on Nexus. Naturally, this covers single and multi module (aka "reactor build")
builds (note: depending on matching used, there might be _multiple_ staging repositories
created, but that would be an advanced use of this plugin! In majority of the cases the one-build one-staging-repo
ratio stands).

Example invocation:
```
$ mvn clean deploy -Ddescription="Description of the staged repository"
```


## Two Shots

This workflow basically defers the deploy/close steps from "One Shot" workflow. It also means you will have two Maven
invocations (1st to build and "locally stage", 2nd to perform "remote staging").

Example invocation:
```
$ mvn clean deploy -DskipRemoteStaging=true
...
$ mvn nexus:deploy-staged -Ddescription="Description of the staged repository"
```


## Server Side Matching

This is the recommended matching mode to be used by users.

This strategy assumes that Nexus (server side) has all the profiles set up properly, matching set up 
on them and all the matching is basically delegated to Nexus Staging Profiles. Client (maven build) does
not expose any "wish" and does not have any knowledge for whereabout or existence of the profiles and
their IDs. It is Nexus Admin, who manages and "routes" which GA should land in which profile, using
Repository Target and Profile Selection Strategy associated with Profiles, and tuning profile order.

Note: while this resembles the "old way" of Staging, this plugin and Nexus 2.2+ introduces
improvements in this area: matching will happen _per module of the build_, hence all the
known problems of "old staging" (artifacts from same module landing in different staging 
repository, or multiple concurrently running but unrelated builds land in same staging repository)
are impossible to happen anymore.


## Targeted Profile

A bit advanced mode with pros and cons.

In case above, profile matching still happens per module, as Nexus Maven Plugin "asked" 
Nexus Staging Plugin for a matching profile. In other words, Nexus Admin controlled in "centralised way" 
which GA should land in which profile. There are cases when this is not possible (like overlapping GAs 
produced by different teams), or we do not want to store this information on Nexus side but rather in 
POM (basically removing the need to _maintain_ the profiles in Nexus, solely creating them on Nexus is sufficient).

In Targeted profile mode user explicitly provides the profile ID preemptively saying "This deploy should 
go into this profile". There is no "matching" happening in this mode, explicit selection happens on server
side. Still, the profile ID _must exists_ on server side! The configuration can exist in POM or 
have it passed over CLI parameter. This might be also used to _occasionally override_ server side
profile settings (ie. exceptionally, some other profile should be used instead of the one prepared
for this build).

The profile ID might configured as plugin configuration section, but it also might be passed
in as CLI parameter (then it gets applied to whole reactor build). Also, you might have a multi
module build that has modules specifying different profiles in plugin configuration section.

Example invocation in CLI:
```
$ mvn clean deploy -DstagingProfileId=12928995ef7eaecc -Ddescription="Description of the staged repository"
```

Note: direct consequence of using this mode is lack of need to manage profiles on server side
(making sure what targets they use, proper ordering, etc), at the cost that profile
ID has to exists, meaning, if you store the ID in POM, build will be not portable between
different Nexus instances! (Staging profile ID is generated by Nexus, user cannot specify it).
TODO: we need an indirection level here maybe?


## Targeted Repository

Advanced use case.

In all cases above, the build still, selected a profile, created and managed a staging repository: 
it selected profile by matching or by user specified ID, it created staging repository, deployed to it 
and finally closed it, so it _managed the staging repository lifecycle_. In this mode, 
some 3rd party entity invokes the staging repository creation, shares the created repository ID among as
many Maven invocations as needed (even on different hosts) but _the plugin does not manage the staging 
repository lifecycle_: the entity creating it should manage it too, so is responsible to closing it too.

Example workflow:

1) create the staging repository in some profile. Here, profile with ID "12928995ef7eaecc" is used,
and it happens using cURL on REST service:
```
  $ curl -H "Content-Type: application/json" --data-binary "{\"data\":{\"description\":\"This is description\"}}" -v -X POST --user deployment:deployment123 http://localhost:8081/nexus/service/local/staging/profiles/12928995ef7eaecc/start
  * About to connect() to localhost port 8081 (#0)
  *   Trying ::1... connected
  * Connected to localhost (::1) port 8081 (#0)
  * Server auth using Basic with user 'deployment'
  > POST /nexus/service/local/staging/profiles/12928995ef7eaecc/start HTTP/1.1
  > Authorization: Basic ZGVwbG95bWVudDpkZXBsb3ltZW50MTIz
  > User-Agent: curl/7.21.4 (universal-apple-darwin11.0) libcurl/7.21.4 OpenSSL/0.9.8r zlib/1.2.5
  > Host: localhost:8081
  > Accept: */*
  > Content-Type: application/json
  > Content-Length: 46
  > 
  < HTTP/1.1 201 Created
  < Date: Tue, 29 May 2012 10:15:46 GMT
  < Set-Cookie: rememberMe=deleteMe; Path=/nexus; Max-Age=0; Expires=Mon, 28-May-2012 10:15:46 GMT
  < Content-Type: application/json; charset=UTF-8
  < Date: Tue, 29 May 2012 10:15:47 GMT
  < Server: Noelios-Restlet-Engine/1.1.6-SONATYPE-5348-V4
  < Content-Length: 79
  < 
  * Connection #0 to host localhost left intact
  * Closing connection #0
  {"data":{"stagedRepositoryId":"test1-004","description":"This is description"}}
```

2) share the newly created staging repository ID across multiple builds, as needed,
invoke all the builds (even on separate hosts!) that you want to "land" in one staging
repository:
```
$ mvn clean deploy -DstagingRepositoryId=test1-004
```
Note: all the Maven limitations stand about deploys! Hence, it's up to you to decide 
do you want to execute builds in parallel or sequentially one by one...

3) when all the builds you needed were invoked and are done, close the staging repository:
```
  $ curl -H "Content-Type: application/json" --data-binary "{\"data\":{\"description\":\"This is description\",\"stagedRepositoryId\":\"test1-004\"}}" -v -X POST --user deployment:deployment123 http://localhost:8081/nexus/service/local/staging/profiles/12928995ef7eaecc/finish
  * About to connect() to localhost port 8081 (#0)
  *   Trying ::1... connected
  * Connected to localhost (::1) port 8081 (#0)
  * Server auth using Basic with user 'deployment'
  > POST /nexus/service/local/staging/profiles/12928995ef7eaecc/finish HTTP/1.1
  > Authorization: Basic ZGVwbG95bWVudDpkZXBsb3ltZW50MTIz
  > User-Agent: curl/7.21.4 (universal-apple-darwin11.0) libcurl/7.21.4 OpenSSL/0.9.8r zlib/1.2.5
  > Host: localhost:8081
  > Accept: */*
  > Content-Type: application/json
  > Content-Length: 79
  > 
  < HTTP/1.1 201 Created
  < Date: Tue, 29 May 2012 10:23:06 GMT
  < Set-Cookie: rememberMe=deleteMe; Path=/nexus; Max-Age=0; Expires=Mon, 28-May-2012 10:23:06 GMT
  < Date: Tue, 29 May 2012 10:23:06 GMT
  < Server: Noelios-Restlet-Engine/1.1.6-SONATYPE-5348-V4
  < Content-Length: 0
  < 
  * Connection #0 to host localhost left intact
  * Closing connection #0
```

This results in _artifacts of all maven invocations happened in step 2 being deployed into one staging repository_.


## Post Staging

When you have successfully staged remotely (like after successfully finishing the "One shots" or "Two shots" 2nd pass steps), 
you have a "context" file in root of local staging repository. Nexus Staging Maven Plugin makes use of this file, and you can 
easily perform "post staging steps without setting any parameters on CLI. 

To release:
```
mvn nexus-staging:release -Ddescription="Yippie!"
```

To give up on staged repository:
```
mvn nexus-staging:drop -Ddescription="Bah, failed QA"
```

To close (in case you used `-DskipStagingRepositoryClose=true` only!):
```
mvn nexus-staging:close -Ddescription="Supercool description"
```



==
Have fun,  
~t~
