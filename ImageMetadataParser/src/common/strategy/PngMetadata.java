package common.strategy;

import png.ChunkDirectory;
import png.ChunkType;
import png.TextKeyword;
import png.ChunkType.Category;
import tif.TagEntries.Taggable;

public class PngMetadata implements PngChunkStrategy<ChunkDirectory>
{
    private ChunkDirectory chunkDir;

    public PngMetadata()
    {
    }

    @Override
    public void addDirectory(ChunkDirectory directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
        }

        this.chunkDir = directory;
    }

    @Override
    public boolean removeDirectory(ChunkDirectory directory)
    {
        throw new UnsupportedOperationException("Cannot remove PNG chunk directory");
    }

    
    // THIS IS NOT SMART. OVERHAUL IT!
    @Override
    public <U> ChunkDirectory getDirectory(U component)
    {
        if (component instanceof ChunkType.Category)
        {
            ChunkType.Category category = (ChunkType.Category) component;

            if (chunkDir.getDirectoryCategory() == category)
            {
                return chunkDir;
            }
        }

        else if (component instanceof Taggable)
        {
            /* Using TagPngChunk enum constants */
            Taggable tag = (Taggable) component;

            if (chunkDir.contains(tag))
            {
                return chunkDir;
            }
        }

        else if (component instanceof TextKeyword)
        {
            /* Using TextKeyword class */
            TextKeyword keyword = (TextKeyword) component;

            if (chunkDir.existsTextualKeyword(keyword))
            {
                return chunkDir;
            }
        }

        else if (component instanceof Class<?>)
        {
            /* Using class resources, i.e. MetadataTIF.class */
            Class<?> clazz = (Class<?>) component;

            if (clazz.isInstance(chunkDir))
            {
                return chunkDir;
            }
        }

        return null;
    }

    @Override
    public boolean isEmpty()
    {
        return (chunkDir.length() == 0);
    }

    @Override
    public boolean hasMetadata()
    {
        return !isEmpty();
    }

    @Override
    public boolean hasTextualData()
    {
        return (chunkDir.getDirectoryCategory() == Category.TEXTUAL);
    }

    @Override
    public boolean hasExifData()
    {
        return chunkDir.hasChunk(ChunkType.eXIf);
    }
}