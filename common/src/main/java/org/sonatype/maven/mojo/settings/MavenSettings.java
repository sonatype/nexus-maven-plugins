package org.sonatype.maven.mojo.settings;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.StringTokenizer;

import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

public class MavenSettings
{
    private MavenSettings()
    {
    }

    public static Server selectServer( final Settings settings, final String serverId )
    {
        final Server server = settings.getServer( serverId );
        if ( server != null )
        {
            return Clone.copy( server );
        }
        return null;
    }

    public static Proxy selectProxy( final Settings settings, final String serverUrl )
        throws MalformedURLException
    {
        URL url = new URL( serverUrl );
        String host = url.getHost();

        Proxy httpProxy = null;
        Proxy httpsProxy = null;
        Collection<Proxy> proxies = settings.getProxies();
        for ( Proxy proxy : proxies )
        {
            if ( proxy.isActive() && !isNonProxyHosts( host, proxy.getNonProxyHosts() ) )
            {
                if ( "http".equalsIgnoreCase( proxy.getProtocol() ) && httpProxy == null )
                {
                    httpProxy = proxy;
                }
                else if ( "https".equalsIgnoreCase( proxy.getProtocol() ) && httpsProxy == null )
                {
                    httpsProxy = proxy;
                }
            }
        }

        Proxy proxy = httpProxy;
        if ( "https".equalsIgnoreCase( url.getProtocol() ) && httpsProxy != null )
        {
            proxy = httpsProxy;
        }

        return proxy;
    }

    public static Server decrypt( final SecDispatcher secDispatcher, final Server server )
        throws SecDispatcherException
    {
        final Server result = Clone.copy( server );

        result.setUsername( decrypt( secDispatcher, server.getUsername() ) );
        result.setPassword( decrypt( secDispatcher, server.getPassword() ) );

        return result;
    }

    public static Proxy decrypt( final SecDispatcher secDispatcher, final Proxy server )
        throws SecDispatcherException
    {
        final Proxy result = Clone.copy( server );

        result.setUsername( decrypt( secDispatcher, server.getUsername() ) );
        result.setPassword( decrypt( secDispatcher, server.getPassword() ) );

        return result;
    }

    // ==

    private static boolean isNonProxyHosts( String host, String nonProxyHosts )
    {
        if ( host != null && nonProxyHosts != null && nonProxyHosts.length() > 0 )
        {
            for ( StringTokenizer tokenizer = new StringTokenizer( nonProxyHosts, "|" ); tokenizer.hasMoreTokens(); )
            {
                String pattern = tokenizer.nextToken();
                pattern = pattern.replace( ".", "\\." ).replace( "*", ".*" );
                if ( host.matches( pattern ) )
                {
                    return true;
                }
            }
        }

        return false;
    }

    private static String decrypt( final SecDispatcher secDispatcher, final String str )
        throws SecDispatcherException
    {
        if ( secDispatcher == null )
        {
            return str;
        }

        return secDispatcher.decrypt( str );
    }
}
