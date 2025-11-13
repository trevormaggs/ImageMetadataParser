package png;

import common.MetadataStrategy;

public interface PngStrategy extends MetadataStrategy<PngDirectory>
{
    public PngDirectory getDirectory(ChunkType.Category key);
}