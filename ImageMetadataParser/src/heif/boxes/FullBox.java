package heif.boxes;

import java.io.IOException;
import common.ByteStreamReader;
import common.Utils;
import logger.LogFactory;

/**
 * This code creates a complete class that stores ubiquitous high-level data applicable for the
 * majority of the derived Box objects, serving as the complete primary header box. It maintains
 * details such as the entire size and type header, fields, and potentially contained boxes. This
 * feature supports the parsing process of the HEIC file.
 *
 * For further technical details, refer to the Specification document - ISO/IEC 14496-12:2015 on
 * Pages 6 and 7 under {@code Object Structure}.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class FullBox extends Box
{
    private static final LogFactory LOGGER = LogFactory.getLogger(FullBox.class);
    public static final int MIN_FULLBOX_LENGTH = 12;
    private final int version;
    private final int flags;

    /**
     * Constructs a {@code FullBox} by extending a standard {@code Box} header with version and
     * flags data.
     * 
     * <p>
     * According to ISO/IEC 14496-12, a FullBox contains a 1-byte version field and a 3-byte flags
     * field immediately following the standard box header.
     * </p>
     *
     * @param box
     *        the base Box header containing size and type (FourCC)
     * @param reader
     *        the reader positioned at the start of the version field
     *
     * @throws IOException
     *         if there is an error reading the 4-byte version/flags payload
     */

    public FullBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box);

        /*
         * Reads 4 additional bytes (1 byte version + 3 bytes flags),
         * on top of the Box header
         */
        this.version = reader.readUnsignedByte();
        this.flags = reader.readUnsignedInt24();
    }

    /**
     * This copy constructor creates a new FullBox object by copying all field variables from
     * another FullBox object.
     *
     * @param box
     *        the other FullBox object to copy from
     */
    public FullBox(FullBox box)
    {
        super(box);

        this.version = box.version;
        this.flags = box.flags;
    }

    /**
     * Returns the integer identifying the version of this particular box's format details.
     *
     * @return a value representing the version
     */
    public int getVersion()
    {
        return version;
    }

    /**
     * Returns the value of the flag as an integer.
     *
     * @return an integer value representing the flag
     */
    public int getFlags()
    {
        return flags;
    }

    /**
     * Checks if a specific bit or set of bits is enabled within the box flags.
     *
     * <p>
     * This is commonly used in HEIF parsing to check for functional signals,
     * such as the "self-contained" flag (0x000001) in Data Entry boxes.
     * </p>
     *
     * @param mask
     *        the bitmask to test against the 24-bit flags field
     * @return {@code true} if any of the bits specified in the mask are set;
     *         {@code false} otherwise
     */
    public boolean isFlagSet(int mask)
    {
        return (this.flags & mask) != 0;
    }

    /**
     * Formats the flags representing the 24-bit padded binary string of the flag map.
     *
     * @return a string displaying the binary representation
     */
    public String getFlagsAsBinaryString()
    {
        return String.format("%24s", Integer.toBinaryString(flags)).replace(' ', '0');
    }

    /**
     * Logs the box hierarchy and internal entry data at the debug level.
     *
     * <p>
     * It provides a visual representation of the box's HEIF/ISO-BMFF structure. It is intended for
     * tree traversal and file inspection during development and degugging if required.
     * </p>
     */
    @Override
    public void logBoxInfo()
    {
        String tab = Utils.repeatPrint("\t", getHierarchyDepth());
        LOGGER.debug(String.format("%s%s '%s':v%d flags:0x%06X (%s)", tab, this.getClass().getSimpleName(), getFourCC(), version, flags, getHeifType().getBoxCategory()));
    }
}