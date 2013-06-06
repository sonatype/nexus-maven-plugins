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
package org.sonatype.nexus.maven.staging.zapper;

import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.codehaus.plexus.component.annotations.Component;
import org.sonatype.spice.zapper.Client;
import org.sonatype.spice.zapper.IOSourceListable;
import org.sonatype.spice.zapper.Parameters;
import org.sonatype.spice.zapper.ParametersBuilder;
import org.sonatype.spice.zapper.client.hc4.Hc4ClientBuilder;
import org.sonatype.spice.zapper.fs.DirectoryIOSource;

import com.google.common.base.Strings;

/**
 * Default imple of Zapper encapsulating component.
 * 
 * @author cstamas
 * @since 1.0
 */
@Component( role = Zapper.class )
public class ZapperImpl
    implements Zapper
{
    @Override
    public void deployDirectory( final ZapperRequest zapperRequest )
        throws IOException
    {
        try
        {
            HttpHost proxyServer = null;
            BasicCredentialsProvider credentialsProvider = null;
            if ( !Strings.isNullOrEmpty( zapperRequest.getProxyProtocol() ) )
            {
                proxyServer =
                    new HttpHost( zapperRequest.getProxyHost(), zapperRequest.getProxyPort(),
                        zapperRequest.getProxyProtocol() );

                if ( !Strings.isNullOrEmpty( zapperRequest.getProxyUsername() ) )
                {
                    UsernamePasswordCredentials proxyCredentials =
                        new UsernamePasswordCredentials( zapperRequest.getProxyUsername(),
                            zapperRequest.getProxyPassword() );
                    credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials( new AuthScope( proxyServer.getHostName(),
                        proxyServer.getPort(), AuthScope.ANY_REALM, proxyServer.getSchemeName() ), proxyCredentials );
                }
            }

            if ( !Strings.isNullOrEmpty( zapperRequest.getRemoteUsername() ) )
            {
                UsernamePasswordCredentials remoteCredentials =
                    new UsernamePasswordCredentials( zapperRequest.getRemoteUsername(),
                        zapperRequest.getRemotePassword() );

                if ( credentialsProvider == null )
                {
                    credentialsProvider = new BasicCredentialsProvider();
                }

                credentialsProvider.setCredentials( AuthScope.ANY, remoteCredentials );
            }

            final Parameters parameters = ParametersBuilder.defaults().build();
            final Hc4ClientBuilder clientBuilder = new Hc4ClientBuilder( parameters, zapperRequest.getRemoteUrl() );
            if ( credentialsProvider != null )
            {
                clientBuilder.withRealm( credentialsProvider );
            }
            if ( proxyServer != null )
            {
                clientBuilder.withProxy( proxyServer );
            }
            final Client client = clientBuilder.build();
            final IOSourceListable deployables = new DirectoryIOSource( zapperRequest.getStageRepository() );

            try
            {
                client.upload( deployables );
            }
            finally
            {
                client.close();
            }
        }
        catch ( IOException e )
        {
            throw e;
        }
        catch ( Exception e )
        {
            throw new IOException( "Unable to deploy!", e );
        }
    }
}
