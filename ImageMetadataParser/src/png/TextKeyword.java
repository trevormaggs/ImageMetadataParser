package png;

import tif.TagHint;

/**
 * Enumerates standard and common PNG textual metadata keywords found in {@code tEXt}, {@code iTXt},
 * and {@code zTXt} chunks.
 *
 * <p>
 * Each constant maps a registered PNG keyword, i.e. {@code Creation Time} to a {@link TagHint},
 * facilitating the conversion of raw strings into structured data types like
 * {@link java.util.Date}.
 * </p>
 * 
 * <p>
 * Examples include: {@code Title}, {@code Author}, {@code Creation Time}, etc.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/png/#11keywords">PNG Specification: Textual Keywords</a>
 * 
 * @author Trevor Maggs
 * @version 1.1
 * @since 30 January 2026
 */
public enum TextKeyword
{
    TITLE("Title"),
    AUTHOR("Author"),
    DESC("Description"),
    COPYRIGHT("Copyright"),
    CREATE("Creation Time", TagHint.HINT_DATE),
    SOFTWARE("Software"),
    DISCLAIMER("Disclaimer"),
    WARNING("Warning"),
    SOURCE("Source"),
    COMMENT("Comment"),
    XMP("XML:com.adobe.xmp"),
    COLLECTION("Collection"),
    OTHER("Unknown");

    private final String keyword;
    private final TagHint hint;

    private TextKeyword(String name)
    {
        this(name, TagHint.HINT_STRING);
    }

    private TextKeyword(String name, TagHint hint)
    {
        this.keyword = name;
        this.hint = hint;
    }

    /**
     * Returns the string keyword associated with this enumeration constant.
     *
     * @return the textual keyword
     */
    public String getKeyword()
    {
        return keyword;
    }

    /**
     * Returns the metadata interpretation hint associated with this keyword.
     *
     * @return the {@link TagHint} for this keyword
     */
    public TagHint getHint()
    {
        return hint;
    }

    /**
     * Resolves a {@code TextKeyword} from a raw string.
     *
     * @param text
     *        the string to look up
     * @return the matching {@code TextKeyword}, or {@link #OTHER} if the string is null or
     *         unrecognised
     */
    public static TextKeyword fromIdentifierString(String text)
    {
        if (text != null)
        {
            for (TextKeyword tk : values())
            {
                if (tk.keyword.equalsIgnoreCase(text))
                {
                    return tk;
                }
            }
        }

        return OTHER;
    }
}