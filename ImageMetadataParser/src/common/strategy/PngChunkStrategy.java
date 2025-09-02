package common.strategy;

import png.ChunkType;
import png.PngDirectory;
import tif.TagEntries.Taggable;

public interface PngChunkStrategy extends MetadataStrategy<PngDirectory>
{
    public PngDirectory getDirectory(ChunkType.Category key);
    public PngDirectory getDirectory(Taggable key);
    public boolean hasExifData();
    public boolean hasTextualData();
}