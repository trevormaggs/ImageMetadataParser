package png;

import java.util.Optional;

public interface TextualChunk
{
    /**
     * Checks whether this chunk contains a specific textual keyword. This method should be
     * implemented by one of the textual chunks to create useful functionality.
     *
     * @param keyword
     *        the {@link TextKeyword} to search for
     *
     * @return true if found, false otherwise
     */
    public boolean hasKeyword(TextKeyword keyword);

    /**
     * This method should be sub-classed by any textual chunks to create useful functionality.
     *
     * @return
     */

    /**
     * Extracts a keyword-text pair from the textual chunk.
     *
     * @return an {@link Optional} containing the extracted keyword and text as a {@link TextEntry}
     *         instance if the keyword is present, otherwise, {@link Optional#empty()}
     */
    public Optional<TextEntry> toTextEntry();

    /**
     * Gets the keyword extracted from the textual chunk.
     *
     * @return the keyword
     */
    public String getKeyword();

    /**
     * Returns the decoded text from the textual chunk.
     *
     * @return the text or null if not yet decoded
     */
    public String getText();
}