package png;

import common.Metadata;
import xmp.XmpDirectory;

public interface PngMetadataProvider extends Metadata<PngDirectory>
{
    public PngDirectory getDirectory(ChunkType.Category key);
    public void addXmpDirectory(XmpDirectory dir);
    public XmpDirectory getXmpDirectory();
}