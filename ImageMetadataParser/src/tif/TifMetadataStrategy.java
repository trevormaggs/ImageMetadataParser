package tif;

import common.MetadataStrategy;

public interface TifMetadataStrategy extends MetadataStrategy<DirectoryIFD>
{
    public DirectoryIFD getDirectory(DirectoryIdentifier dirKey);
}