package common.strategy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import png.ChunkType;
import png.ChunkType.Category;
import png.PngChunk;
import png.PngChunkDirectory;
import png.TextKeyword;
import tif.TagEntries.Taggable;

public class PngMetadata implements PngChunkStrategy
{
    private final Map<Category, PngChunkDirectory> pngMap;

    public PngMetadata()
    {
        this.pngMap = new HashMap<>();
    }

    @Override
    public void addDirectory(PngChunkDirectory directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
        }

        pngMap.putIfAbsent(directory.getDirectoryCategory(), directory);
    }

    @Override
    public boolean removeDirectory(PngChunkDirectory directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
        }

        return (pngMap.remove(directory.getDirectoryCategory()) != null);
    }

    @Override
    public PngChunkDirectory getDirectory(ChunkType.Category category)
    {
        for (PngChunkDirectory dir : pngMap.values())
        {
            if (dir.getDirectoryCategory() == category)
            {
                return dir;
            }
        }

        return null;
    }

    @Override
    public PngChunkDirectory getDirectory(Taggable tag)
    {
        for (PngChunkDirectory dir : pngMap.values())
        {
            if (dir.containsTag(tag))
            {
                return dir;
            }
        }

        return null;
    }

    @Override
    public PngChunkDirectory getDirectory(TextKeyword keyword)
    {
        for (PngChunkDirectory dir : pngMap.values())
        {
            if (dir.getDirectoryCategory() == (Category.TEXTUAL))
            {
                for (PngChunk chunk : dir)
                {
                    if (chunk.hasKeywordPair(keyword))
                    {
                        return dir;
                    }
                }
            }
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
        if (pngMap.containsKey(Category.MISC))
        {
            return pngMap.get(Category.MISC).containsChunk(ChunkType.eXIf);
        }

        return false;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Category, PngChunkDirectory> entry : pngMap.entrySet())
        {
            sb.append(entry.getKey()).append(System.lineSeparator());
            sb.append(entry.getValue()).append(System.lineSeparator());
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    @Override
    public Iterator<PngChunkDirectory> iterator()
    {
        return pngMap.values().iterator();
    }
}