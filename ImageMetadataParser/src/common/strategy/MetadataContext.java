package common.strategy;

import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import png.ChunkType;
import png.PngDirectory;
import tif.DirectoryIFD;
import tif.DirectoryIdentifier;
import tif.TagEntries.Taggable;

public class MetadataContext
{
    private final MetadataStrategy<?> strategy;

    public MetadataContext(MetadataStrategy<?> strategy)
    {
        this.strategy = strategy;
    }

    public boolean containsMetadata()
    {
        return strategy.hasMetadata();
    }

    public boolean metadataIsEmpty()
    {
        return strategy.isEmpty();
    }

    public boolean hasExifData()
    {
        if (strategy instanceof ExifStrategy)
        {
            return ((ExifStrategy) strategy).hasExifData();
        }

        else if (strategy instanceof PngChunkStrategy)
        {
            return ((PngChunkStrategy) strategy).hasExifData();
        }

        return false;
    }

    public boolean hasTextualData()
    {
        if (strategy instanceof PngChunkStrategy)
        {
            return ((PngChunkStrategy) strategy).hasTextualData();
        }

        return false;
    }

    // ---------- Typed Iterators ----------

    public Iterator<DirectoryIFD> getExifIterator()
    {
        if (strategy instanceof ExifStrategy)
        {
            return ((ExifStrategy) strategy).iterator();
        }

        return Collections.<DirectoryIFD> emptyIterator();
    }

    public Iterator<PngDirectory> getPngIterator()
    {
        if (strategy instanceof PngChunkStrategy)
        {
            return ((PngChunkStrategy) strategy).iterator();
        }

        return Collections.<PngDirectory> emptyIterator();
    }

    // ---------- Safe Directory Getters ----------

    public Optional<DirectoryIFD> getDirectory(DirectoryIdentifier key)
    {
        if (strategy instanceof ExifStrategy)
        {
            return Optional.ofNullable(((ExifStrategy) strategy).getDirectory(key));
        }

        return Optional.empty();
    }

    public Optional<PngDirectory> getDirectory(ChunkType.Category category)
    {
        if (strategy instanceof PngChunkStrategy)
        {
            return Optional.ofNullable(((PngChunkStrategy) strategy).getDirectory(category));
        }

        return Optional.empty();
    }

    public Optional<PngDirectory> getDirectory(Taggable tag)
    {
        if (strategy instanceof PngChunkStrategy)
        {
            return Optional.ofNullable(((PngChunkStrategy) strategy).getDirectory(tag));
        }

        return Optional.empty();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        for (Object obj : strategy)
        {
            if (obj instanceof DirectoryIFD)
            {
                sb.append(obj).append(System.lineSeparator());
            }

            else if (obj instanceof PngDirectory)
            {
                sb.append(obj).append(System.lineSeparator());
            }
        }

        return sb.toString();
    }
}