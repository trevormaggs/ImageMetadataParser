package png;

import common.MetadataStrategy;
import tif.tagspecs.Taggable;

public interface PngStrategy extends MetadataStrategy<PngDirectory>
{
    public PngDirectory getDirectory(Taggable key);
    public PngDirectory getDirectory(ChunkType.Category key);
    @Override
    public boolean hasExifData();
    @Override
    public boolean hasTextualData();
}