package png;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import common.Directory;
import png.ChunkType.Category;

public class PngDirectory implements Directory<PngChunk>
{
    private final Category category;
    private final List<PngChunk> chunks;

    public PngDirectory(Category category)
    {
        this.category = category;
        this.chunks = new ArrayList<>();
    }

    public Category getCategory()
    {
        return category;
    }

    public PngChunk getFirstChunk(ChunkType chunk)
    {
        return findChunkByID(chunk.getIndexID());
    }

    public int addChunkList(List<PngChunk> chunkList)
    {
        int count = 0;

        if (chunkList == null || chunkList.isEmpty())
        {
            return 0;
        }

        for (PngChunk chunk : chunkList)
        {
            add(chunk);
            count++;
        }

        return count;
    }

    public List<PngChunk> getChunks()
    {
        return Collections.unmodifiableList(chunks);
    }

    @Override
    public Iterator<PngChunk> iterator()
    {
        return chunks.iterator();
    }

    @Override
    public void add(PngChunk chunk)
    {
        if (chunk.getType().getCategory() != category)
        {
            throw new IllegalArgumentException("Inconsistent chunk type detected. The category for this directory must be [" + category.getDescription() + "]");
        }

        chunks.add(chunk);
    }

    @Override
    public int size()
    {
        return chunks.size();
    }

    @Override
    public boolean isEmpty()
    {
        return chunks.isEmpty();
    }

    @Override
    public boolean contains(PngChunk entry)
    {
        return chunks.contains(entry);
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

    private PngChunk findChunkByID(int id)
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