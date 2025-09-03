package png;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import png.ChunkType.Category;
import tif.TagEntries.TagPngChunk;
import tif.TagEntries.Taggable;

public class PngMetadata implements PngStrategy
{
    private final Map<Category, PngDirectory> pngMap;

    public PngMetadata()
    {
        this.pngMap = new HashMap<>();
    }

    @Override
    public void addDirectory(PngDirectory directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
        }

        pngMap.putIfAbsent(directory.getDirectoryCategory(), directory);
    }

    @Override
    public boolean removeDirectory(PngDirectory directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
        }

        return pngMap.remove(directory.getDirectoryCategory(), directory);
    }

    @Override
    public PngDirectory getDirectory(ChunkType.Category category)
    {
        return pngMap.get(category);
    }

    @Override
    public PngDirectory getDirectory(Taggable tag)
    {
        if (tag instanceof TagPngChunk)
        {
            return getDirectory(((TagPngChunk) tag).getChunkType().getCategory());
        }

        return null;
    }

    @Override
    public boolean isEmpty()
    {
        return (pngMap.size() == 0);
    }

    @Override
    public boolean hasMetadata()
    {
        return !isEmpty();
    }

    @Override
    public boolean hasTextualData()
    {
        return pngMap.containsKey(Category.TEXTUAL);
    }

    @Override
    public boolean hasExifData()
    {
        for (PngDirectory dir : pngMap.values())
        {
            for (PngChunk chunk : dir)
            {
                // Note, ChunkType.eXIf is expected to be from Category.MISC
                if (chunk.getType().equals(ChunkType.eXIf))
                {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Category, PngDirectory> entry : pngMap.entrySet())
        {
            sb.append(entry.getKey()).append(System.lineSeparator());
            sb.append(entry.getValue()).append(System.lineSeparator());
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    @Override
    public Iterator<PngDirectory> iterator()
    {
        return pngMap.values().iterator();
    }
}