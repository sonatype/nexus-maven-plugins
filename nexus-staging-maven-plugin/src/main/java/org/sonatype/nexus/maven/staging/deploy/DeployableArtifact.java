package org.sonatype.nexus.maven.staging.deploy;

import java.io.File;

import org.apache.maven.artifact.Artifact;

import com.google.common.base.Preconditions;

public class DeployableArtifact
{
    private final File file;

    private final Artifact artifact;

    public DeployableArtifact( final File file, final Artifact artifact )
    {
        this.file = Preconditions.checkNotNull( file, "DeployableArtifact.file is null!" );
        this.artifact = Preconditions.checkNotNull( artifact, "DeployableArtifact.artifact is null!" );
    }

    public File getFile()
    {
        return file;
    }

    public Artifact getArtifact()
    {
        return artifact;
    }
}
