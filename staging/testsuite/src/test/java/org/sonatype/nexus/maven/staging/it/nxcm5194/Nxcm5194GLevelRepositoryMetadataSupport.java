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
package org.sonatype.nexus.maven.staging.it.nxcm5194;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.annotation.Nullable;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Plugin;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.it.VerificationException;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Test;
import org.sonatype.nexus.client.core.subsystem.content.Content;
import org.sonatype.nexus.client.core.subsystem.content.Location;
import org.sonatype.nexus.maven.staging.it.PreparedVerifier;
import org.sonatype.nexus.maven.staging.it.SimpleRoundtripMatrixSupport;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

/**
 * IT Support for https://issues.sonatype.org/browse/NXCM-5194
 * <p>
 * It verifies that G level repository metadata is properly deployed and contains the proper bits about the maven plugin
 * being built.
 * 
 * @author cstamas
 */
public abstract class Nxcm5194GLevelRepositoryMetadataSupport
    extends SimpleRoundtripMatrixSupport
{

    public Nxcm5194GLevelRepositoryMetadataSupport( final String nexusBundleCoordinates )
    {
        super( nexusBundleCoordinates );
    }

    // == the tests

    /**
     * Maven Plugin Project set up in m2-way with m2.
     * 
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM2ProjectUsingM2()
        throws VerificationException, IOException
    {
        roundtrip( createMavenVerifier( M2_VERSION, new File( getBasedir(),
            "target/test-classes/maven2-maven-plugin-project" ) ) );
    }

    /**
     * Maven Plugin Project set up in m2-way with m3.
     * 
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM2ProjectUsingM3()
        throws VerificationException, IOException
    {
        roundtrip( createMavenVerifier( M3_VERSION, new File( getBasedir(),
            "target/test-classes/maven2-maven-plugin-project" ) ) );
    }

    /**
     * Maven Plugin Project set up in m3-way using m3.
     * 
     * @throws VerificationException
     * @throws IOException
     */
    @Test
    public void roundtripWithM3ProjectUsingM3()
        throws VerificationException, IOException
    {
        roundtrip( createMavenVerifier( M3_VERSION, new File( getBasedir(),
            "target/test-classes/maven3-maven-plugin-project" ) ) );
    }

    // == Scenario specific methods

    @Override
    protected abstract PreparedVerifier createMavenVerifier( final String mavenVersion, final File projectDirectory )
        throws VerificationException, IOException;

    protected abstract String getTargetedRepositoryId();

    @Override
    protected abstract void invokeMaven( final PreparedVerifier verifier )
        throws VerificationException;

    // == Assertions

    @Override
    protected void preNexusAssertions( final PreparedVerifier verifier )
    {
        assertThat( getAllStagingRepositories().toString(), getAllStagingRepositories(), hasSize( 0 ) );
    }

    @Override
    protected void postNexusAssertions( final PreparedVerifier verifier )
    {
        assertThat( getAllStagingRepositories().toString(), getAllStagingRepositories(), hasSize( 0 ) );
        checkGLevelMD( verifier );
    }

    protected void checkGLevelMD( final PreparedVerifier verifier )
    {
        final Content content = getNexusClient().getSubsystem( Content.class );
        final File target = util.createTempFile();
        FileInputStream fis = null;
        try
        {
            content.download(
                Location.repositoryLocation( getTargetedRepositoryId(), "/"
                    + getClass().getPackage().getName().replaceAll( "\\.", "/" ) + "/maven-metadata.xml" ), target );
            fis = new FileInputStream( target );
            final Metadata md = new MetadataXpp3Reader().read( fis );
            // depending on test execution, this collection might have size of 1, 2 or 3
            assertThat( md.getPlugins(), not( empty() ) );
            for ( Plugin plugin : md.getPlugins() )
            {
                // see raw-pom.xml, the prefix is set to artifactId to avoid 3 deploys of different GAVs with same
                // prefix
                if ( StringUtils.equals( plugin.getArtifactId(), verifier.getProjectArtifactId() )
                    && StringUtils.equals( plugin.getPrefix(), verifier.getProjectArtifactId() ) )
                {
                    // we got it, good
                    return;
                }
            }
            throw new AssertionError( "The maven-plugin with artifact ID " + verifier.getProjectArtifactId()
                + " should be contained in this Plugin list: "
                + Lists.transform( md.getPlugins(), new Function<Plugin, String>()
                {
                    @Override
                    public String apply( @Nullable Plugin input )
                    {
                        return input.getArtifactId();
                    }
                } ) );
        }
        catch ( XmlPullParserException e )
        {
            throw new AssertionError( "The metadata parse failed: " + e.getMessage() );
        }
        catch ( IOException e )
        {
            throw new AssertionError( "The metadata download failed: " + e.getMessage() );
        }
        finally
        {
            IOUtil.close( fis );
        }
    }
}