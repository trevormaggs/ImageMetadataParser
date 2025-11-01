package tif;

import common.MetadataStrategy;

// D is bound to DirectoryIFD here
public interface ExifStrategy extends MetadataStrategy<DirectoryIFD>
{
    public DirectoryIFD getDirectory(DirectoryIdentifier dirKey);
    @Override
    public boolean hasExifData();
}