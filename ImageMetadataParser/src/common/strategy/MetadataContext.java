package common.strategy;

import java.util.Iterator;
import png.ChunkType;
import png.PngChunkDirectory;
import png.TextKeyword;
import tif.DirectoryIFD;
import tif.DirectoryIdentifier;
import tif.TagEntries.Taggable;

public class MetadataContext<T>
{
    private final MetadataStrategy<T> strategy;

    public MetadataContext(MetadataStrategy<T> strategy)
    {
        this.strategy = strategy;
    }

    public boolean containsMetadata()
    {
        return strategy.hasMetadata();
    }

    public boolean hasNoMetadata()
    {
        return strategy.isEmpty();
    }

    public Iterator<T> getIterator()
    {
        return strategy.iterator();
    }

    public DirectoryIFD getDirectory(DirectoryIdentifier key)
    {
        return ((ExifStrategy) strategy).getDirectory(key);
    }

    public PngChunkDirectory getDirectory(ChunkType.Category category)
    {
        return ((PngChunkStrategy) strategy).getDirectory(category);
    }

    public PngChunkDirectory getDirectory(Taggable tag)
    {
        return ((PngChunkStrategy) strategy).getDirectory(tag);
    }

    public PngChunkDirectory getDirectory(TextKeyword keyword)
    {
        return ((PngChunkStrategy) strategy).getDirectory(keyword);
    }
}