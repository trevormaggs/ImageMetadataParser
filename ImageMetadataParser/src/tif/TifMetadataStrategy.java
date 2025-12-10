package tif;

import common.MetadataStrategy;
import xmp.XmpDirectory;

public interface TifMetadataStrategy extends MetadataStrategy<DirectoryIFD>
{
    public DirectoryIFD getDirectory(DirectoryIdentifier dirKey);
    public void addXmpDirectory(XmpDirectory dir);
    public XmpDirectory getXmpDirectory();
}