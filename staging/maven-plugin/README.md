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
# Nexus Staging Maven Plugin

Maven Plugin to control Nexus Staging workflow. While the maven plugin ("staging client") part is OSS, to use staging features it need a Sonatype Nexus Professional instance 2.1+ on the server side!

Plugin is compatible with Maven 2.2.1 and Maven 3.0.x.

This plugin replaces any staging functionality from the deprecated Nexus Maven Plugin. 

Features:
 * it is meant to _completely replace_ (or buy out) the maven-deploy-plugin
 * since version 1.1, it _transparently supports_ release and snapshot builds
 * supports multiple operation modes: _direct deploy_, _deferred deploy_, _staging_ and _image upload_ (see below).
 
Notes:
 * to _build this plugin_ from sources you need access to Sonatype commercial components!
 * to _use this plugin_ in your build, access to Central only is enough!

# Vulnerabilities

## Dependency Vulnerabilities Not Impacting nexus-staging-maven-plugin

Some vulnerabilities may be reported against this plugin by security analysis tools. Sonatype investigation has determined the following are not applicable.

### CVE-2017-1000487

The identified vulnerabilities are in classes which are not used.

#### Dependency

org.codehaus.plexus:plexus-utils:3.0.8

### CVE-2017-5929

The out-of-the-box logback configuration used is not vulnerable.

#### Dependency

ch.qos.logback:logback-classic:1.1.2

### sonatype-2015-0173

The identified vulnerabilities are in methods not used.

#### Dependency

org.codehaus.plexus : plexus-utils : 3.0.8

### sonatype-2016-0398

The identified vulnerabilities are in classes which are not used directly or in any of the plugin dependency code.

#### Dependency

org.codehaus.plexus : plexus-utils : 3.0.8

### sonatype-2020-0926

The identified vulnerabilities are in methods not used.

#### Dependency

com.google.guava : guava : 14.0.1

### CVE-2018-10237

The identified vulnerabilities are in classes not used.

#### Dependency

com.google.guava : guava : 14.0.1

# Documentation

Nexus Staging V2 focuses more on client-server automated interaction.
Hence, the `nexus-staging-maven-plugin` is introduced, with vastly enhanced support.

*Warning: the "artifactId" of the plugin is newly introduced! The old nexus-maven-plugin is deprecated!*

Further user documentation is also available in the staging chapter of the free book Repository Management with Nexus:

http://books.sonatype.com/nexus-book/reference/staging.html

## Operating modes

Nexus Staging Maven plugin automatically (or explicitly configured) chooses the "mode" of operation.

### Direct deploy

Basically, this mode is 100% same as maven-deploy-plugin works. Remote deploys happens at `deploy` phase of each module,
and the deployment repository is "sourced" in same way, from POMs `distributionManagement` entry (which node is used, 
depends is the project being built a release or snapshot naturally).

Direct deploy is used when you skip local staging (so to say, the staging functionality altogether), for example by
using the `-DskipLocalStaging=true` CLI parameter.

This mode is new in version 1.1.

### Deferred deploy

This mode performs "local staging" to a local staging repository that is within the build (and is cleaned by `mvn clean`, but
the location of it might be explicitly configured too, see `altStagingDirectory` plugin flag). Actual remote deployment 
happens at the end of the build, in one-shot. The deployment repository is "sourced" as for maven-deploy-plugin, 
from POMs `distributionManagement` entry (which node is used, depends is the project being built a release or snapshot naturally).

This mode is _automatically used_ for snapshot builds (unless overridden by configuration 
to perform"direct" deploys). Same "known issues" stands for concurrent deploys as they stand for
maven-deploy-plugin, except that the "window" to hit deploy clashes is way less, as the actual deploy happens at the very
end (literally last moments) of your build, instead "throughout" the build (where the clash window pretty much equals
the duration of the build).

As said above, this mode is used when you either deploy snapshots (the project you build and deploy has snapshot version)
or you skip staging, for example by using the `-DskipStaging=true` CLI parameter. Note: multi project builds where one
project is release version and other is snapshot might result in unpredictable results and such cases are not supported!

This mode is new in version 1.1.

### Staging

This mode is present since version 1.0. It is _automatically used_ for release builds (unless overridden by configuration 
to perform "deferred" or "direct" deploys). Staging happens against a staging directory created on Nexus instance
that's baseURL is set plugin configuration.

This mode performs full staging workflow, from repository creation to closing it in case of success or dropping the repository
in case of (transport or staging rule) failure.

This mode is used when you did not specify any of the parameters mentioned in previous modes, and the project you deploy
has release version.

For supported staging workflows, refer to [Staging Workflows](WORKFLOWS.md) page.

### Image upload

This mode performs full staging workflow, from repository creation to closing it in case of success or dropping the repository
in case of (transport or staging rule) failure. But in contrary to all modes above, it transfers the source in _unmodified form_,
where it is expected that you stage the "exact image" of the source to the staging repository, like some locally deployed
repository produced by maven-deploy-plugin + altDeploymentRepository switch. Staging happens against a staging directory created 
on Nexus instance that's baseURL is set plugin configuration.

This mode is used only by it's own special goal, the `deploy-staged-repository`. The "plain" `deploy` goal will never
use this mode.

This mode is new in version 1.1.

## Adding the plugin to your build

Using this plugin in build might be done in multiple ways, depending what version of Maven you use.

### Maven3 Only

In Maven3, the simplest needed to be done is to add the plugin to the build, and define it as
extension and add wanted configuration. Plugin's `LifecycleParticipant` (a new Maven3 feature) will "automagically" do everything for you:
it will _disable_ `maven-deploy-plugin:deploy` executions, _inject_ `nexus-staging-maven-plugin:deploy` executions instead.
This is the simplest way to use the plugin, but in case more control is needed, even in Maven3 it is possible to use the
plugin in Maven2-way (manually configure all the binding).

Example snippet of plugin entry with minimal required configuration in build:

		<build>
			<plugins>
				<plugin>
					<groupId>org.sonatype.plugins</groupId>
					<artifactId>nexus-staging-maven-plugin</artifactId>
					<version>1.1</version>
					<extensions>true</extensions>
					<configuration>
  					<!-- The Base URL of Nexus instance where we want to stage -->
						<nexusUrl>http://localhost:8081/nexus/</nexusUrl>
						<!-- The server "id" element from settings to use authentication from -->
						<serverId>local-nexus</serverId>
					</configuration>
				</plugin>
			</plugins>
			...
		</build>

The lifecycle participant will kick in *only when no binding is detected in modules for `nexus-staging-maven-plugin`*,
hence, in latter case, it will remain dormant (as it will assume you "manually bound it", see below). 
Example log outputs when plugin kicks in:

		[cstamas@marvin sisu-goodies (master)]$ mvn clean deploy
		[INFO] Scanning for projects...
		[INFO] Installing Nexus Staging features:
		[INFO] ... total of 8 executions of maven-deploy-plugin replaced with nexus-staging-maven-plugin.
		[INFO] ------------------------------------------------------------------------
		[INFO] Reactor Build Order:
		[INFO]
		[INFO] Goodies
		...

Plugin not kicking in:

		cstamas@marvin sisu-goodies (master)]$ mvn clean -Pstaging-test
		[INFO] Scanning for projects...
		[INFO] Not installing Nexus Staging features, some Staging related goal bindings already present.
		[INFO] ------------------------------------------------------------------------
		[INFO] Reactor Build Order:
		[INFO]
		[INFO] Goodies
		...

Above, in both cases Maven3 is used (as lifecycle participant works with it only). In first case,
lifecycle participant activated itself, and performed 8 "swaps" on the fly. In second case, it
found `nexus-staging-maven-plugin` bindings already present (hence, the Maven2-way of configuration was applied), and it just stay put.

### Maven 2 + 3 Living Together

Fortunately there is a simple approach ( with one caveat ) that will allow a parent pom to
define the configuration for both scenarios using profiles that are automatically activated.

Add the following to any parent pom ( not aggregator ) of your project to be built using Maven 2.2.1 or 3.x:


    <profile>
      <id>nexus-staging-maven2</id>
      <activation>
        <file>
          <missing>${basedir}</missing>
        </file>
      </activation>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-deploy-plugin</artifactId>
            <configuration>
              <skip>true</skip>
            </configuration>
          </plugin>
          <plugin>
            <groupId>org.sonatype.plugins</groupId>
            <artifactId>nexus-staging-maven-plugin</artifactId>
            <version>1.1</version>
            <executions>
              <execution>
                <id>default-deploy</id>
                <phase>deploy</phase>
                <goals>
                  <goal>deploy</goal>
                </goals>
              </execution>
            </executions>
            <configuration>
              <serverId>local-nexus</serverId>
              <nexusUrl>http://localhost:8081/nexus/</nexusUrl>
              <!-- Profile Id override profile matching -->
              <!--stagingProfileId></stagingProfileId-->
              <!-- By having none of those above, we actually use Staging V2 in "auto" mode, profile will be matched server side -->
              <!-- Tags -->
              <tags>
                <localUsername>${env.USER}</localUsername>
                <javaVersion>${java.version}</javaVersion>
              </tags>
            </configuration>
          </plugin>
        </plugins>
      </build>
    </profile>
    <profile>
      <id>nexus-staging-maven3</id>
      <activation>
        <file>
          <exists>${basedir}</exists>
        </file>
      </activation>
      <build>
        <plugins>
          <plugin>
            <groupId>org.sonatype.plugins</groupId>
            <artifactId>nexus-staging-maven-plugin</artifactId>
            <version>1.1</version>
            <extensions>true</extensions>
            <configuration>
              <serverId>local-nexus</serverId>
              <nexusUrl>http://localhost:8081/nexus/</nexusUrl>
              <!-- Profile Id override profile matching -->
              <!--stagingProfileId></stagingProfileId-->
              <!-- By having none of those above, we actually use Staging V2 in "auto" mode, profile will be matched server side -->
              <!-- Tags -->
              <tags>
                <localUsername>${env.USER}</localUsername>
                <javaVersion>${java.version}</javaVersion>
              </tags>
            </configuration>
          </plugin>
        </plugins>
      </build>
    </profile>


We define here two profiles controlled by Maven activation.

CAVEAT: The one caveat using this approach is profiles that would have been active by <activeByDefault/> config will not be since
one of these profiles for staging will override the default profile.

Depending, is it used in Maven3 or Maven2, it almost needs same configuration applied, but Maven2 needs more labour.

### Maven2 Only (or "explicit" Maven3 mode)

In Maven2, or even with Maven3 if you want more control (for example to bind goals to different phases), 
explicit configuration is needed.
First, you would want to `skip=true` of "vanilla" `maven-deploy-plugin` or even the best, completely remove
it from the build (Note: skip parameter is present since version 2.4). Second, you'd want
to add the plugin `org.sonatype.plugins:nexus-staging-maven-plugin:1.1` to the build.

Example snippet of having the plugin in the build bound explicitly:

		<build>
			<plugins>
				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-deploy-plugin</artifactId>
					<configuration>
						<skip>true</skip>
					</configuration>
				</plugin>
				<plugin>
					<groupId>org.sonatype.plugins</groupId>
					<artifactId>nexus-staging-maven-plugin</artifactId>
					<version>1.1</version>
					<executions>
						<execution>
							<id>default-deploy</id>
							<phase>deploy</phase>
							<!-- By default, this is the phase deploy goal will bind to -->
							<goals>
								<goal>deploy</goal>
							</goals>
						</execution>
					</executions>
					<configuration>
  					<!-- The Base URL of Nexus instance where we want to stage -->
						<nexusUrl>http://localhost:8081/nexus/</nexusUrl>
						<!-- The server "id" element from settings to use authentication from -->
						<serverId>local-nexus</serverId>
					</configuration>
				</plugin>
			</plugins>
		</build>


This above is "minimum" configuration of Nexus, and it will trigger the use of "Staging V2 implicit" mode.

## Configuring the plugin

Minimal requirement for configuration are two entries: `nexusUrl` and `serverId`.

| Configuration | Meaning |
|---------------|---------|
| `nexusUrl` | *Mandatory*. Has to point to the *base URL of target Nexus*. |
| `serverId` | *Mandatory*. Has to hold an ID of a `<server>` section from Maven's `settings.xml` to pick authentication information from. |

With configuration as above, if you have suitable profile in Nexus that will be matched, everything should work. If not profile match possible, staging will fail naturally.

### Staging workflow related flags

As noted above, with "minimal configuration" you are all set, but many implicit things will happen on server side: the profile will be "matched" for you on server side (similarly as it happens in "old" V1 staging, but a big difference is that matching of a profile in V2 happens per-module, while V1 did per-deploy request, hence, sub-artifacts of same module theoretically might end up in different profiles in case of misconfiguration). Also, a staging repository will be created (visible and usable only by you, so no more "overlapping deploys" are possible). Still, you can narrow configuration (profile selection or repository selection) by following staging workflow flags:

|Configuration (additional to mandatory ones)|Performs staging?|Profile matching happens?|Staging repository created?|Post-staging repository management (close/drop)?|Remarks|
|--------------------------------------------|-----------------|-------------------------|---------------------------|------------------------------------------------|-------|
| nothing more than mandatory ones | Yes | Yes | Yes | Yes | This is the "V2 implicit" way of using Staging V2. Matches the profile once, and manages the staging repository from it's creation to it's end (close or drop) |
| `stagingProfileId` | Yes | No | Yes | Yes | This is the "V2 explicit" way of using Staging V2: targeted profile, so no match is done. Naturally, the repository type of the profile should match of that being deployed. Manages the staging repository from it's creation to it's end (close or drop) |
| `stagingProfileId` and `stagingRepositoryId` | Yes | No | No | No | Advanced V2 usage. This is usable when some "external component" (script? CI?) performs V2 actions of repository creation etc, and only "targeted" deploy happens against given (open) staging repository. Since repository is created by some other entity, it will be NOT managed by client (the one creating it should close it too). For example: multi machine build should end up in _same staging repository_ (not doable with V1, see "Oracle problem"). |

This table also presents the "order" how configuration is interpreted: last wins. For example, if both `stagingProfileId` and `stagingRepositoryId` is present, `stagingRepositoryId` wins. With `stagingRepositoryId` being present, this plugin _will not manage that repository_ (to close it in case of success, or drop it in case of failure) as it is assumed that some "external component" is managing that staging repository! This makes possible to stage _multiple unrelated builds_ into same staging repository, if needed.

### Plugin flags

These "flags" are usually passed in from CLI (`-D...`).

|Flag type|CLI (`-D`)|Default value|Meaning|
|---------|----------|-------------|-------|
| Alternate local staging directory (FS directory path) | `altStagingDirectory` | `null` | Possibility to _explicitly_ define a directory on local FS to use for local staging. Passing in this flag will prevent the "logic" of proper `target` folder selection |
| Description (plain text)| `description` | `null` | Free text, message or explanation to be added for staging operations like when staging repository is created or closed (as part of whole V2 process) |
| Keep staging repository in case of failure (boolean) | `keepStagingRepositoryOnFailure` | `false` | Nexus Staging Maven Plugin always tries to "clean up" after itself, hence, in case of _upload failure_ (and potentially having "partially uploaded" artifacts to staging repository) it always tries to drop that same repository. If this flag is set to `true`, it will not drop it. |
| Keep staging repository in case of a rule failure (boolean) | `keepStagingRepositoryOnCloseRuleFailure` | `false` | Nexus Staging Maven Plugin will close the staging repository for you at the end of the build (no need to "visit" Nexus UI to do that anymore!). By default, in case of repository close failure due to failing Staging Rule, it will drop that same failed repository. If this flag is set to `true`, it will not drop it, letting you to inspect the binaries to figure out why did a rule fail. |
| Skip whole Deploy mojo (boolean) | `skipNexusStagingDeployMojo` | `false` | Completely skips the `deploy` Mojo (similar as `maven.deploy.skip`). In multi-module builds using the deploy-at-end feature, the deployment of all components is performed in the last module based on the reactor order. If this property is set to true in the last module, all staging deployment for all modules will be skipped. In some cases, it may be necessary to add a dummy module. |
| Skip local staging (boolean) | `skipLocalStaging` | `false` | If `true`, will not locally stage. This triggers basically equivalent behaviour to maven-deploy-plugin, each deploy will happen "directly", at the end of the each module build.|
| Skip remote staging (boolean) | `skipRemoteStaging` | `false` | If `true`, performs "local staging" only, and skips the remote deploy. Hence, no stage repository created, no deploy happened.|
| Skip closing of staging repository (boolean) | `skipStagingRepositoryClose` | `false` | If true, the plugin _will not close_ the staging repository even after a successful staging. It makes you able to continue to deploy to same repository (see `stagingRepositoryId` workflow flag), or simply use the UI to do the same. |
| Skip use of staging features (boolean) | `skipStaging` | `false` | If `true`, plugin will skip staging features completely, and will operate in "deferred deploy" mode.|
| <a name="autoReleaseAfterClose"></a>Immediately release on successful close operation (boolean) | `autoReleaseAfterClose` | `false` | If `true`, plugin will immediately perform a release of staging repository (or repositories) after a successful close Be aware that this flag affects all goals that perform "close" staging action, such as `deploy` (unless `skipStagingRepositoryClose` set), `close` and `rc-close`!|
| Drop released staging repositories (boolean) | `autoDropAfterRelease` | `true` | If `true`, plugin will instruct Nexus to drop the staging repositories that were successfully released. If `false`, released repositories will be kept on server side, cleanup over UI will be needed.|

### Tagging staging repositories

User is able to simply "decorate" the Staging repository with key-value pairs, that will get stored along Staged repository configuration.
Simply add following section to `nexus-staging-maven-plugin` configuration section (in other words, create a "map" in plugin configuration, see configuring Maven Mojos):

		<configuration>
			<!-- The Base URL of Nexus instance where we want to stage -->
			<nexusUrl>http://localhost:8081/nexus/</nexusUrl>
			<!-- The server "id" element from settings to use authentication from -->
			<serverId>local-nexus</serverId>
			<tags>
				<localUsername>${env.USER}</localUsername>
				<javaVersion>${java.version}</javaVersion>
			</tags>
		</configuration>

Configuration is plain "Mojo map", element names will become "keys", and their content the "value".
Values are evaluated using standard Maven way, so you can source them from system properties, env variables, etc.

## Plugin Goals

Below is a short list of existing plugin goals and description how they are intended to be used.

### Build Action goals

These goals are the ones that should be bound (either in Maven3-way "magically" or explicitly in Maven2-way, does not matter)
to phases of your build. Using these goals, one can combine almost any staging solution they need. Examples:

* using the `deploy` (or `deploy-staged` when `deploy` had set `skipRemoteStaging` parameter) one ends with *closed staging repository*. Still, you can incorporate these goals into your build, and have the repository *released or promoted* even, from your builds.
* in case of "targeted repository ID" scenario, where the Staging repository is created by some external entity (script or some other entity), and this plugin *does not manage the staging repository* (will just deploy to it but will not try to close it), you can still have it closed from one of your CI nodes by having some conditions met (ie. from a profile or so).

Hence, their configuration usually sits in the POM (at least the minimal ones, like `serverId` and `nexusUrl`).

Goals except `deploy` and `deploy-staged` all has one parameter: `stagingRepositoryId`. These goals may receive that parameter from CLI, but in case is not given, will take that value from the properties file ("context") saved to the root of locally staged repository.

#### `deploy`

This goal actually performs the whole deployment together with "staging workflow":

* locally stages,
* selects a profile (either by server-side matching or using user provided profileId)
* selects a staging repository (either by creating one "private" or using user provided repositoryId)
* performs atomic upload into staging repository
* closes it (or drops if upload failed)

With this goal, the user has no need for the other ones. It might "skip" the remote staging, then only 1st step is executed.

On successful remote staging, this goal (and the next two) creates a small Java properties file on level above where the locally staged artifacts are that contains useful information not only for humans, but also for later automation (and is used by some of these goals, see "build action goals") as source of "defaults". Example of the created file:

    #Generated by org.sonatype.plugins:nexus-staging-maven-plugin:1.1
    #Fri Sep 14 11:53:03 CEST 2012
    stagingRepository.managed=true
    stagingRepository.profileId=12a8aae9889b7403
    stagingRepository.id=test01-002
    stagingRepository.url=http\://localhost\:8081/nexus/content/repositories/test01-002

Keys and their meanings:

 * `managed` - if `true`, it means that nexus-staging-maven-plugin did create the repository in the build from where it was remotely staged. Also, it will "manage" it (close or drop), unless explicitly configured to not do so (see plugin flags).
 * `profileId` - shows the Staging Profile ID against which staging happened (it was either matched by Nexus or preset in plugin configuration).
 * `id` - the staging repository ID that remote staging happened against.
 * `url` - the direct URL (the repository might be accessible over some group too!) to access the staged repository.

#### `deploy-staged`

This goal performs the "staging workflow" only for previously ran local staging (ie. `mvn clean deploy -DskipRemoteStaging=true`).

#### `deploy-staged-repository`

This goal is meant to be used in cases when you want to stage a complete locally deployed repository, like _locally deployed using maven-deploy-plugin and it's "altDeploymentRepository" switch pointing to local filesystem_. 

Typical use case is when you check out a tag from a "foreign" project (not managed by you, otherwise you'd edit POMs and simply apply nexus-staging-maven-plugin) to build and stage it. In this case, you perform `mvn clean deploy -DaltDeploymentRepository=local::default::file://some/path` as first step, and then using this goal you stage the complete locally deployed repository to Nexus. For this goal, a _mandatory_ parameter is `repositoryDirectory`, that accepts the _FS directory, the root of locally deployed repository_. Example invocation `mvn nexus-staging:deploy-staged-repository -DrepositoryDirectory=/some/path`.

This goal, as is meant to be run "outside" of a project, does not need one, but in turn, it needs to be fully configured (as there is no POM to source value). It has to have following parameters passed in (on CLI, using "-D..."):

 * `repositoryDirectory` - the existing directory where the locally deployed repository (the "image") exists.
 * `nexusUrl` - the baseUrl of the Nexus instance you want to stage to.
 * `serverId` - the ID of the server entry from Maven Settings to get authentication information from.
 * `stagingProfileId` - the Staging Profile ID to stage against

and optionally:
 
 * `stagingRepositoryId` - the (existing) staging repository to stage into, if needed. If not given, staging will happen against newly created empty staging repository, as usual.
 

#### `close`

Closes the staging repository. After successful remote staging, it will pick up defaults from the create properties file, so if you mean "close staging repository created by the build of this project", no need for any extra parameters.

#### `drop`

Drops the staging repository. After successful remote staging, it will pick up defaults from the create properties file, so if you mean "drop staging repository created by the build of this project", no need for any extra parameters.

#### `release`

Releases the staging repository. After successful remote staging, it will pick up defaults from the create properties file, so if you mean "release staging repository created by the build of this project", no need for any extra parameters.

#### `promote`

Promotes the staging repository. For advanced use only. After successful remote staging, it will pick up defaults from the create properties file, so if you mean "promote staging repository created by the build of this project", no need for any extra parameters, except for the needed one.

It needs _extra mandatory parameter_: `buildPromotionProfileId`

### RC Action goals

These goals are "remote controlling" (RC) goals, and they *do not need a project to be executed*, and can be *directly invoked from CLI only*.

Hence, they are "RC" (as "remote control") goals, made for convenience only to perform some Staging
Workflow operations using your favorite tool (Maven) running it from a CLI (just for fun, or
because you are in a headless environment and have no access to Nexus UI).

All of them expect *explicit configuration* usually passed in over CLI parameters (`-Dfoo=bar...`).

All of them accept mandatory parameters to connect to Nexus, the `nexusUrl` and `serverId`.

All of them accept mandatory `stagingRepositoryId` parameter, similarly to other staging goals,
*with exception that in this case the parameter is split using "," (comma) as delimiter*.
Hence, all these goals might operate against *one or more staging repository* (bulk operation).

All of them accept optional `description` parameter, but it's not mandatory. If not specified, a default description will be applied.
#### `rc-open` (since 1.6.7)

Creates a new staging repository from a staging profile. Example invocation:
   
   mvn nexus-staging:rc-open -DserverId=local-nexus -DnexusUrl=http://localhost:8081/nexus -DstagingProfileId=72c1cc10566951 -DopenedRepositoryMessageFormat='The name of created repository is: %s' -DstagingDescription="The reason I open it is..."

Would create a new staging repository on the remote Nexus and log its name using the string format requested via openedRepositoryMessageFormat parameter. Parameter openedRepositoryMessageFormat is optional, if not provided the name of the newly created repository will be logged in a default format. 

#### `rc-list-profiles` (since 1.5)

Lists the staging profiles accessible by current user. Example invocation:

    mvn nexus-staging:rc-list-profiles -DserverId=local-nexus -DnexusUrl=http://localhost:8081/nexus

Would list all the profiles (ID, accept mode, name) of the remote Nexus accessible to user.

#### `rc-list` (since 1.5)

Lists the staging repositories accessible by current user. Example invocation:

    mvn nexus-staging:rc-list -DserverId=local-nexus -DnexusUrl=http://localhost:8081/nexus

Would list all the staging repositories (ID, state, description) of the remote Nexus accessible to user. By default, RELEASED staging repositories are filtered out,
to have them list them too, use `-DshowReleased=true` property.

#### `rc-close`

Closes the specified staging repositories.

Example invocation:

		mvn nexus-staging:rc-close -DserverId=local-nexus -DnexusUrl=http://localhost:8081/nexus -DstagingRepositoryId=repo1,repo2 -DstagingDescription="The reason I close these is..."


#### `rc-drop`

Drops the specified staging repositories.

Example invocation:

		mvn nexus-staging:rc-drop -DserverId=local-nexus -DnexusUrl=http://localhost:8081/nexus -DstagingRepositoryId=repo1,repo2 -DstagingDescription="The reason I drop these is..."

#### `rc-release`

Releases the specified closed staging repositories.

Example invocation:

		mvn nexus-staging:rc-release -DserverId=local-nexus -DnexusUrl=http://localhost:8081/nexus -DstagingRepositoryId=repo1,repo2 -DstagingDescription="The reason I release these is..."

#### `rc-promote`

Performs a build profile promotion on the specified closed staging repositories. This goal *has one extra mandatory parameter*: The `buildPromotionProfileId`.

Example invocation:


		mvn nexus-staging:rc-promote -DserverId=local-nexus -DnexusUrl=http://localhost:8081/nexus -DbuildPromotionProfileId=foo -DstagingRepositoryId=repo1,repo2 -DstagingDescription="The reason I promote these is..."
