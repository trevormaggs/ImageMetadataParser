package png;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import png.ChunkType.Category;
import tif.tagspecs.Taggable;

public class PngDirectory implements Iterable<PngChunk>
{
    private final Category category;
    private final List<PngChunk> chunks;

    public PngDirectory(Category category)
    {
        this.category = category;
        this.chunks = new ArrayList<>();
    }

    public Category getDirectoryCategory()
    {
        return category;
    }

    public boolean addChunk(PngChunk chunk)
    {
        if (chunk.getType().getCategory() != category)
        {
            throw new IllegalArgumentException("Inconsistent chunk type detected. The category for this directory must be [" + category.getDescription() + "]");

        }

        return chunks.add(chunk);
    }

    public void addChunkList(List<PngChunk> chunkList)
    {
        if (chunkList != null && !chunkList.isEmpty())
        {
            for (PngChunk chunk : chunkList)
            {
                addChunk(chunk);
            }
        }
    }

    public boolean remove(PngChunk chunk)
    {
        return chunks.remove(chunk);
    }

    public int size()
    {
        return chunks.size();
    }

    public boolean containsChunk(ChunkType chunk)
    {
        return findEntryByID(chunk.getIndexID()) != null;
    }

    public boolean containsTag(Taggable tag)
    {
        return findEntryByID(tag.getNumberID()) != null;
    }

    public PngChunk getFirstChunk(ChunkType chunk)
    {
        PngChunk type = findEntryByID(chunk.getIndexID());

        if (type != null)
        {
            return type;
        }

        return null;
    }

    @Override
    public Iterator<PngChunk> iterator()
    {
        return chunks.iterator();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        for (PngChunk chunk : chunks)
        {
            sb.append(chunk).append(System.lineSeparator());
        }

        return sb.toString();
    }

    private PngChunk findEntryByID(int id)
    {
        for (PngChunk chunk : chunks)
        {
            if (chunk.getType().getIndexID() == id)
            {
                return chunk;
            }
        }

        return null;
    }
}