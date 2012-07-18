package org.sonatype.nexus.maven.staging.deploy;

import com.sonatype.nexus.staging.client.Profile;

public class StagingRepository
{
    private final Profile profile;

    private final String repositoryId;

    private final boolean managed;

    public StagingRepository( final Profile profile, final String repositoryId, final boolean managed )
    {
        this.profile = profile;
        this.repositoryId = repositoryId;
        this.managed = managed;
    }

    protected Profile getProfile()
    {
        return profile;
    }

    protected String getRepositoryId()
    {
        return repositoryId;
    }

    protected boolean isManaged()
    {
        return managed;
    }
}
