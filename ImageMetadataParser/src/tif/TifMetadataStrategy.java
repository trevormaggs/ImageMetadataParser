package tif;

import common.MetadataStrategy;
import xmp.XmpDirectory;

public interface TifMetadataStrategy extends MetadataStrategy<DirectoryIFD>
{
    public DirectoryIFD getDirectory(DirectoryIdentifier dirKey);
    public void addXmpDirectory(byte[] payload);
    public XmpDirectory getXmpDirectory();
}