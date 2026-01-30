package png;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import logger.LogFactory;

/**
 * Represents PNG chunk types as defined by the W3C specification. Each constant captures the 4-byte
 * identifier, a description, functional category, and whether multiple occurrences are permitted.
 * 
 * <p>
 * For detailed understanding of the PNG format, including chunk structure and meanings of various
 * chunks, refer to the official W3C PNG Specification:
 * <a href="https://www.w3.org/TR/png/#4Concepts">https://www.w3.org/TR/png/#4Concepts</a>.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.1
 * @since 30 January 2026
 */
public enum ChunkType
{
    IHDR("IHDR", "Image header", Category.HEADER),
    PLTE("PLTE", "Palette", Category.PALETTE),
    IDAT("IDAT", "Image data", Category.DATA, true),
    IEND("IEND", "Image trailer", Category.END),
    acTL("acTL", "Animation Control Chunk", Category.ANIMATION),
    cHRM("cHRM", "Primary chromaticities and white point", Category.COLOUR),
    cICP("cICP", "Coding-independent code points", Category.COLOUR),
    gAMA("gAMA", "Image Gamma", Category.COLOUR),
    iCCP("iCCP", "Embedded ICC profile", Category.COLOUR),
    mDCV("mDCV", "Mastering Display Color Volume", Category.COLOUR),
    cLLI("cLLI", "Content Light Level Information", Category.COLOUR),
    sBIT("sBIT", "Significant bits", Category.COLOUR),
    sRGB("sRGB", "Standard RGB color space", Category.COLOUR),
    bKGD("bKGD", "Background color", Category.MISC),
    hIST("hIST", "Image Histogram", Category.MISC),
    tRNS("tRNS", "Transparency", Category.TRANSP),
    eXIf("eXIf", "Exchangeable Image File Profile", Category.MISC),
    fcTL("fcTL", "Frame Control Chunk", Category.ANIMATION, true),
    pHYs("pHYs", "Physical pixel dimensions", Category.MISC),
    sPLT("sPLT", "Suggested palette", Category.MISC, true),
    fdAT("fdAT", "Frame Data Chunk", Category.ANIMATION, true),
    tIME("tIME", "Image last-modification time", Category.TIME),
    iTXt("iTXt", "International textual data", Category.TEXTUAL, true),
    tEXt("tEXt", "Textual data", Category.TEXTUAL, true),
    zTXt("zTXt", "Compressed textual data", Category.TEXTUAL, true),
    UNKNOWN("Unknown", "Undefined chunk", Category.UNDEFINED);

    /**
     * Defines categories for PNG chunk types, grouping them by their general purpose. This helps in
     * organising and filtering chunks during processing.
     */
    public enum Category
    {
        HEADER("Image Header"),
        PALETTE("Palette table"),
        DATA("Image Data"),
        END("Image Trailer"),
        COLOUR("Colour Space"),
        MISC("Miscellaneous"),
        TRANSP("Transparency"),
        TEXTUAL("Textual"),
        ANIMATION("Animation"),
        TIME("Modified Time"),
        UNDEFINED("Undefined");

        private final String desc;

        Category(String name)
        {
            this.desc = name;
        }
        public String getDescription()
        {
            return desc;
        }
    }

    private static final LogFactory LOGGER = LogFactory.getLogger(ChunkType.class);
    private final String name;
    private final String description;
    private final Category category;
    private final boolean multipleAllowed;
    private final byte[] identifierBytes;

    private ChunkType(String name, String desc, Category category)
    {
        this(name, desc, category, false);
    }

    private ChunkType(String name, String desc, Category category, boolean multipleAllowed)
    {
        this.name = name;
        this.description = desc;
        this.category = category;
        this.multipleAllowed = multipleAllowed;
        this.identifierBytes = name.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Returns the 4-character ASCII name of this chunk type. This is the identifier used in the PNG
     * file format.
     * 
     * @return the chunk name, for example: {@code IHDR}
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns a human-readable description of this chunk type's purpose.
     * 
     * @return the description of the chunk
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Returns the category to which this chunk type belongs.
     * 
     * @return the {@link Category} of the chunk
     */
    public Category getCategory()
    {
        return category;
    }

    /**
     * Validates the chunk is a textual type.
     *
     * @return true to indicate the chunk is textual, otherwise false
     */
    public boolean isTextual()
    {
        return category == Category.TEXTUAL;
    }

    /**
     * Checks if multiple instances of this chunk type are allowed within a single PNG file. Some
     * chunks, for example: like IHDR and IEND, must appear only once, while others, such as IDAT,
     * tEXt, etc can appear multiple times.
     * 
     * @return true if multiple occurrences are allowed, otherwise false
     */
    public boolean isMultipleAllowed()
    {
        return multipleAllowed;
    }

    /**
     * Identifies if a chunk is {@code Critical} or {@code Ancillary}.
     * 
     * <p>
     * According to PNG specifications, the case of the first letter determines this. Uppercase
     * (e.g., 'I') is critical, while lowercase (e.g., 'a') is ancillary.
     * </p>
     * 
     * @return true if the chunk is critical, false if it is ancillary or unknown
     */
    public boolean isCritical()
    {
        if (this == UNKNOWN)
        {
            return false;
        }

        return Character.isUpperCase(name.charAt(0));
    }

    /**
     * Retrieves a copy of the byte array for this chunk type.
     *
     * @return an array of bytes, containing the data
     */
    public byte[] getIdentifier()
    {
        return Arrays.copyOf(identifierBytes, identifierBytes.length);
    }

    /**
     * Validates that the 4-byte array contains only A-Z or a-z ASCII characters.
     * 
     * @param bytes
     *        the 4-byte array representing a potential chunk type
     * @return false if the byte array is not 4 bytes long or contains non-alphabetic characters,
     *         otherwise true
     */
    public static boolean isValidIdentifier(byte[] bytes)
    {
        if (bytes == null || bytes.length != 4)
        {
            LOGGER.error("PNG chunk identifier must be 4 bytes.");
            return false;
        }

        for (byte b : bytes)
        {
            int val = b & 0xFF;

            if (!((val >= 65 && val <= 90) || (val >= 97 && val <= 122)))
            {
                LOGGER.error("Invalid character in PNG chunk identifier: " + (char) val);
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the ChunkType matching the 4-byte array, or UNKNOWN if no match.
     *
     * @param bytes
     *        the 4-byte array representing the chunk type
     * 
     * @return the {@link ChunkType} enum constant if a match is found, otherwise {@link #UNKNOWN}
     */
    public static ChunkType fromBytes(byte[] bytes)
    {
        if (isValidIdentifier(bytes))
        {
            for (ChunkType type : values())
            {
                if (Arrays.equals(type.identifierBytes, bytes))
                {
                    return type;
                }
            }
        }

        return UNKNOWN;
    }

    /**
     * Checks if a given 4-byte array corresponds to a known {@code ChunkType}.
     *
     * @param bytes
     *        the 4-byte array representing the chunk type identifier
     * @return true if the chunk type is recognised, otherwise false
     */
    public static boolean isDefined(byte[] bytes)
    {
        return fromBytes(bytes) != UNKNOWN;
    }
}