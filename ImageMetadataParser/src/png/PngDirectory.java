package png;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import common.Directory;
import png.ChunkType.Category;

/**
 * Manages a collection of {@link PngChunk} objects that all belong to a specific
 * {@link png.ChunkType.Category}.
 * 
 * <p>
 * This class implements the {@link Directory} interface, providing methods for adding, retrieving,
 * and iterating over the {@link PngChunk} objects. It enforces that all chunks added to the
 * directory must match the directory's predefined {@link ChunkType.Category}.
 * </p>
 * 
 * @author Trevor Maggs
 * @version 1.0
 * @since 3 October 2025
 */
public class PngDirectory implements Directory<PngChunk>
{
    private final Category category;
    private final List<PngChunk> chunks;

    /**
     * Constructs a new {@code PngDirectory} for the specified chunk {@link Category}.
     *
     * @param category
     *        the Category of PngChunk objects this directory stores
     */
    public PngDirectory(Category category)
    {
        this.category = category;
        this.chunks = new ArrayList<>();
    }

    /**
     * Gets the {@link Category} of {@link PngChunk} objects stored in this directory.
     *
     * @return the Category of this directory
     */
    public Category getCategory()
    {
        return category;
    }

    /**
     * Retrieves the first {@link PngChunk} in this directory whose {@link ChunkType} matches the
     * given {@code chunk} type.
     *
     * @param chunk
     *        the ChunkType to search for
     * @return the first PngChunk found with the matching ChunkType, or null if none is found
     */
    public PngChunk getFirstChunk(ChunkType chunk)
    {
        return findChunkByID(chunk.getIndexID());
    }

    /**
     * Adds all {@link PngChunk} objects from the specified list to this directory.
     * 
     * <p>
     * Each chunk is added using the {@link #add(PngChunk)} method, which enforces the
     * {@link Category} consistency.
     * </p>
     *
     * @param chunkList
     *        the list of PngChunk objects to add. Can be null or empty
     * @return The number of chunks successfully added.
     */
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

    /**
     * Gets a safe unmodifiable {@link List} of all {@link PngChunk} objects in this directory.
     *
     * @return An unmodifiable view of the internal list of chunks
     */
    public List<PngChunk> getChunks()
    {
        return Collections.unmodifiableList(chunks);
    }

    /**
     * Returns an {@link Iterator} over the {@link PngChunk}s in this directory.
     *
     * @return an Iterator for the chunks
     */
    @Override
    public Iterator<PngChunk> iterator()
    {
        return chunks.iterator();
    }

    /**
     * Adds a single {@link PngChunk} to this directory.
     *
     * @param chunk
     *        the PngChunk to be added
     * 
     * @throws IllegalArgumentException
     *         if the Category does not match the directory's required Category
     */
    @Override
    public void add(PngChunk chunk)
    {
        if (chunk.getType().getCategory() != category)
        {
            throw new IllegalArgumentException("Inconsistent chunk type detected. The category for this directory must be [" + category.getDescription() + "]");
        }

        chunks.add(chunk);
    }

    /**
     * Returns the number of {@link PngChunk} objects in this directory.
     *
     * @return the size of the directory
     */
    @Override
    public int size()
    {
        return chunks.size();
    }

    /**
     * Checks if this directory contains any {@link PngChunk} objects.
     *
     * @return true if the directory is empty, otherwise false
     */
    @Override
    public boolean isEmpty()
    {
        return chunks.isEmpty();
    }

    /**
     * Checks if a specific {@link PngChunk} is present in this directory.
     *
     * @param entry
     *        the PngChunk to check for
     * @return true if the chunk is found, otherwise false
     */
    @Override
    public boolean contains(PngChunk entry)
    {
        return chunks.contains(entry);
    }

    /**
     * Returns a string representation of this directory, which is the concatenation of the string
     * representations of all contained {@link PngChunk} objects, each on a new line.
     *
     * @return a multi-line string representing the chunks in the directory
     */
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

    /**
     * Searches for the first {@link PngChunk} in the directory whose {@link ChunkType}'s index ID
     * matches the given ID.
     *
     * @param id
     *        the index ID of the ChunkType to search for
     * @return the first matching PngChunk, or null if no match is found
     */
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