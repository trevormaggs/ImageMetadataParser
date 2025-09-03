package tif;

import common.MetadataStrategy;

public interface ExifStrategy extends MetadataStrategy<DirectoryIFD>
{
    public DirectoryIFD getDirectory(DirectoryIdentifier dirKey);
    public boolean hasExifData();
}
