package org.sonatype.maven.mojo.sisu;

public class PlexusKey
{
    private final Class role;

    private final String hint;

    public PlexusKey( final Class role, final String hint )
    {
        this.role = role;
        this.hint = hint;
    }

    protected Class getRole()
    {
        return role;
    }

    protected String getHint()
    {
        return hint;
    }
}
