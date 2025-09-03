package png;

import common.MetadataStrategy;
import tif.TagEntries.Taggable;

public interface PngStrategy extends MetadataStrategy<PngDirectory>
{
    public PngDirectory getDirectory(ChunkType.Category key);
    public PngDirectory getDirectory(Taggable key);
    public boolean hasExifData();
    public boolean hasTextualData();
}