package xmp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import common.Directory;
import png.ChunkType.Category;
import png.PngChunk;

/**
 * Encapsulates a collection of {@link PngChunk} objects of a specific {@link ChunkType.Category}
 * group.
 *
 * <p>
 * This class implements the {@link Directory} interface, providing simple methods for adding,
 * retrieving, and iterating over the {@link PngChunk} objects. It enforces that all chunks added to
 * the directory must match the directory's predefined {@link ChunkType.Category}.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 6 November 2025
 */
public class XmpDirectory implements Directory<PngChunk>
{
    private final List<PngChunk> chunks;

    /**
     * Constructs a new {@code PngDirectory} for the specified chunk {@link Category}.
     *
     * @param category
     *        the Category of PngChunk objects this directory stores
     */
    public XmpDirectory()
    {
        this.chunks = new ArrayList<>();
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

    @Override
    public void add(PngChunk entry)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean remove(PngChunk entry)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean contains(PngChunk entry)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public int size()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean isEmpty()
    {
        // TODO Auto-generated method stub
        return false;
    }
}