package png;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import logger.LogFactory;

/**
 * Enumeration of PNG chunk types as defined by the W3C specification.
 * <p>
 * Each type captures the 4-byte identifier, functional category, and
 * usage constraints (such as whether multiple instances are permitted).
 * </p>
 * * @author Trevor Maggs
 * 
 * @version 1.2
 * @since 30 January 2026
 */
public enum ChunkType2
{
    IHDR("IHDR", "Image header", Category.HEADER),
    PLTE("PLTE", "Palette", Category.PALETTE),
    IDAT("IDAT", "Image data", Category.DATA, true),
    IEND("IEND", "Image trailer", Category.END),
    acTL("acTL", "Animation Control Chunk", Category.ANIMATION),
    cHRM("cHRM", "Primary chromaticities", Category.COLOUR),
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
     * Categories for grouping chunks by their general purpose.
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

    private ChunkType2(String name, String desc, Category category)
    {
        this(name, desc, category, false);
    }

    private ChunkType2(String name, String desc, Category category, boolean multipleAllowed)
    {
        this.name = name;
        this.description = desc;
        this.category = category;
        this.multipleAllowed = multipleAllowed;
        this.identifierBytes = name.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * @return the 4-character ASCII identifier (e.g., "IHDR")
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return a human-readable description of the chunk
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * @return the {@link Category} of this chunk
     */
    public Category getCategory()
    {
        return category;
    }

    /**
     * @return true if the chunk belongs to the TEXTUAL category
     */
    public boolean isTextual()
    {
        return category == Category.TEXTUAL;
    }

    /**
     * @return true if multiple instances are permitted in a single file
     */
    public boolean isMultipleAllowed()
    {
        return multipleAllowed;
    }

    /**
     * Determines if the chunk is critical for rendering.
     * <p>
     * Critically is defined by bit 5 of the first byte being 0 (uppercase).
     * </p>
     * * @return true if critical, false if ancillary
     */
    public boolean isCritical()
    {
        if (this == UNKNOWN) return false;
        // PNG Spec: bit 5 of first byte: 0=critical, 1=ancillary
        return (identifierBytes[0] & 0x20) == 0;
    }

    /**
     * @return a defensive copy of the 4-byte identifier
     */
    public byte[] getIdentifier()
    {
        return Arrays.copyOf(identifierBytes, 4);
    }

    /**
     * Validates that the identifier contains exactly 4 ASCII alphabetic characters.
     * * @param bytes the 4-byte array to validate
     * 
     * @return true if valid
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
            if (!((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z')))
            {
                LOGGER.error("Invalid character in identifier: " + (char) b);
                return false;
            }
        }
        
        return true;
    }

    /**
     * Maps a 4-byte array to a known ChunkType.
     * * @param bytes the 4-byte identifier from the stream
     * 
     * @return the matching {@link ChunkType}, or {@link #UNKNOWN}
     */
    public static ChunkType2 fromBytes(byte[] bytes)
    {
        if (!isValidIdentifier(bytes)) return UNKNOWN;

        for (ChunkType2 type : values())
        {
            if (Arrays.equals(type.identifierBytes, bytes))
            {
                return type;
            }
        }
        
        return UNKNOWN;
    }
}