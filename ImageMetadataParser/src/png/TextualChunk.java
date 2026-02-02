package png;

import java.util.Optional;

/**
 * Common interface for PNG textual metadata chunks (tEXt, zTXt, iTXt).
 */
public interface TextualChunk
{
    /**
     * Checks if this chunk matches a specific predefined keyword.
     *
     * @param keyword
     *        the keyword to search for
     * @return true if found, false otherwise
     */
    public boolean hasKeyword(TextKeyword keyword);

    /**
     * Converts this chunk into a keyword-text pair.
     *
     * @return an {@link Optional} containing the {@link TextEntry}, otherwise
     *         {@link Optional#empty()}
     */
    public Optional<TextEntry> toTextEntry();

    /**
     * Gets the raw keyword string.
     *
     * @return the keyword
     */
    public String getKeyword();

    /**
     * Gets the decoded text content.
     *
     * @return the decoded text, or an empty string if missing or could not be decoded
     */
    public String getText();
}