package png;

import common.MetadataStrategy;
import xmp.XmpDirectory;

public interface PngStrategy extends MetadataStrategy<PngDirectory>
{
    public PngDirectory getDirectory(ChunkType.Category key);
    public XmpDirectory getXmpDirectory();
}