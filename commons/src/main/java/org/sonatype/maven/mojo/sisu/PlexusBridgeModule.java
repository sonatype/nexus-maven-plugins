package org.sonatype.maven.mojo.sisu;

import org.codehaus.plexus.PlexusContainer;

import com.google.inject.AbstractModule;

class PlexusBridgeModule
    extends AbstractModule
{
    private final PlexusContainer plexus;

    private final PlexusKey[] plexusKeys;

    public PlexusBridgeModule( final PlexusContainer plexus, final PlexusKey... plexusKeys )
    {
        this.plexus = plexus;
        this.plexusKeys = plexusKeys;
    }

    @Override
    protected void configure()
    {
        for ( PlexusKey key : plexusKeys )
        {
            try
            {
                bind( key.getRole() ).toInstance( lookup( key.getRole(), key.getHint() ) );
            }
            catch ( Exception e )
            {
                addError( e );
            }
        }
    }

    private <T> T lookup( Class<T> type, String hint )
        throws Exception
    {
        return type.cast( plexus.lookup( type.getName(), hint ) );
    }
}