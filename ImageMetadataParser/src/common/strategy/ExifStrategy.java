package common.strategy;

import tif.DirectoryIFD;
import tif.DirectoryIdentifier;

public interface ExifStrategy extends MetadataStrategy<DirectoryIFD>
{
    public DirectoryIFD getDirectory(DirectoryIdentifier dirKey);
    public boolean hasExifData();
}
