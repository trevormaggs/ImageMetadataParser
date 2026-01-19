package tif;

import common.Metadata;
import xmp.XmpDirectory;

public interface TifMetadataProvider extends Metadata<DirectoryIFD>
{
    public DirectoryIFD getDirectory(DirectoryIdentifier dirKey);
    public void addXmpDirectory(XmpDirectory dir);
    public XmpDirectory getXmpDirectory();
}