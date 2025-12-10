package png;

import common.MetadataStrategy;
import xmp.XmpDirectory;

public interface PngMetadataStrategy extends MetadataStrategy<PngDirectory>
{
    public PngDirectory getDirectory(ChunkType.Category key);
    public void addXmpDirectory(XmpDirectory dir);
    public XmpDirectory getXmpDirectory();
}