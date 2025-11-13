package png;

/**
 * Represents a textual metadata entry within a PNG textual chunk, consisting of a chunk type,
 * keyword, and associated text value.
 *
 * Typically used to store entries from textual chunks, specifically {@code tEXt}, {@code iTXt}, or
 * {@code zTXt}.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 November 2025
 */
public class TextEntry
{
    private final ChunkType type;
    private final String keyword;
    private final String text;

    /**
     * Constructs a {@code TextEntry} with a PNG chunk type, keyword, and associated text value.
     *
     * @param type
     *        indicates the PNG chunk type
     * @param keyword
     *        the keyword name identifying the entry
     * @param text
     *        the text value associated with the keyword
     */
    public TextEntry(ChunkType type, String keyword, String text)
    {
        if (type == null || keyword == null || keyword.isEmpty() || text == null)
        {
            throw new IllegalArgumentException("TextEntry fields (ChunkType, keyword, or text value) cannot be null or empty");
        }

        this.type = type;
        this.keyword = keyword;
        this.text = text;
    }

    /**
     * Returns the chunk type associated with this entry.
     *
     * @return the {@link ChunkType} type
     */
    public ChunkType getChunkType()
    {
        return type;
    }

    /**
     * Returns the keyword for this entry.
     *
     * @return the keyword string
     */
    public String getKeyword()
    {
        return keyword;
    }

    /**
     * Returns the text value associated with this entry.
     *
     * @return the associated text string
     */
    public String getText()
    {
        return text;
    }

    /**
     * Returns a formatted string representation of this text entry.
     *
     * @return a detailed multi-line string
     */
    @Override
    public String toString()
    {
        StringBuilder line = new StringBuilder();

        line.append(String.format(" %-20s %s%n", "[Chunk Type]", type));
        line.append(String.format(" %-20s %s%n", "[Keyword]", keyword));
        line.append(String.format(" %-20s %s%n", "[Text Value]", text));

        return line.toString();
    }
}