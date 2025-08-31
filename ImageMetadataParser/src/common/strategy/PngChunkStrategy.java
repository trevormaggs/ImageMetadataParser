package common.strategy;

import png.ChunkType;
import png.PngChunkDirectory;
import png.TextKeyword;
import tif.TagEntries.Taggable;

public interface PngChunkStrategy extends MetadataStrategy<PngChunkDirectory>, Iterable<PngChunkDirectory>
{
    public PngChunkDirectory getDirectory(ChunkType.Category key);
    public PngChunkDirectory getDirectory(Taggable key);
    public PngChunkDirectory getDirectory(TextKeyword key);
    public boolean hasExifData();
    public boolean hasTextualData();
}