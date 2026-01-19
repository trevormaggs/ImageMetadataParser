package heif.boxes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import common.ByteStreamReader;
import common.ByteValueConverter;
import common.Utils;
import heif.BoxHandler;
import heif.HeifBoxType;
import logger.LogFactory;

/**
 * Represents a generic HEIF Box, according to ISO/IEC 14496-12:2015. Handles both standard boxes
 * and {@code uuid} extended boxes.
 */
public class Box
{
    private static final LogFactory LOGGER = LogFactory.getLogger(Box.class);
    public static final long BOX_SIZE_TO_EOF = Long.MAX_VALUE;
    public static final int MIN_BOX_LENGTH = 8;
    private final long startPosition;
    private final long boxSize;
    private final byte[] boxTypeBytes;
    private final int typeCode;
    private final HeifBoxType type;
    private final String userType;

    private Box parent;
    private int hierarchyDepth;

    /**
     * Constructs a {@code Box} by reading its header from the specified {@code ByteStreamReader}.
     *
     * @param reader
     *        the byte reader for parsing
     *
     * @throws IOException
     *         if an I/O error occurs
     * @throws IllegalStateException
     *         if the standard box size is illegal
     */
    public Box(ByteStreamReader reader) throws IOException
    {
        this.startPosition = reader.getCurrentPosition();

        long size = reader.readUnsignedInteger();
        long minRequired = (size == 1) ? 16 : 8;
        this.boxTypeBytes = reader.readBytes(4);
        this.typeCode = ByteValueConverter.toInteger(boxTypeBytes, BoxHandler.HEIF_BYTE_ORDER);
        this.type = HeifBoxType.fromTypeInt(typeCode);
        this.boxSize = (size == 1 ? reader.readLong() : (size == 0 ? BOX_SIZE_TO_EOF : size));

        if (type == HeifBoxType.UUID)
        {
            minRequired += 16;
        }

        if (boxSize != BOX_SIZE_TO_EOF && boxSize < minRequired)
        {
            throw new IllegalStateException(String.format("Inconsistent box size [%d] for type [%s]. Minimum required: %d", boxSize, getFourCC(), minRequired));
        }

        if (type == HeifBoxType.UUID)
        {
            byte[] uuidBytes = reader.readBytes(16);
            this.userType = ByteValueConverter.toHex(uuidBytes);
        }

        else
        {
            this.userType = null;
        }
    }

    /**
     * A copy constructor to replicate field values one by one, useful for sub-classing, retaining
     * the original field values.
     *
     * @param box
     *        the box to copy
     */
    public Box(Box box)
    {
        this.boxSize = box.boxSize;
        this.boxTypeBytes = box.boxTypeBytes.clone();
        this.type = box.type;
        this.typeCode = box.typeCode;
        this.userType = box.userType;
        this.parent = box.parent;
        this.hierarchyDepth = box.hierarchyDepth;
        this.startPosition = box.startPosition;
    }

    /**
     * Returns the number of remaining bytes in the box.
     *
     * @param reader
     *        the stream reader for calculating the remaining bytes allowed to be used based on the
     *        current position
     * @return remaining bytes
     *
     * @throws IOException
     *         if there is an I/O error
     * @throws IllegalStateException
     *         the box size is unknown, possibly due to a malformed structure
     */
    public long available(ByteStreamReader reader) throws IOException
    {
        if (boxSize == BOX_SIZE_TO_EOF)
        {
            throw new IllegalStateException("Box size is unknown (extends to EOF). Remaining size cannot be calculated");
        }

        return Math.max(0, startPosition + boxSize - reader.getCurrentPosition());
    }

    /**
     * Returns the absolute stream offset where this box begins.
     *
     * <p>
     * This offset is the position of the first byte of the box header (the start of the 4-byte size
     * field).
     * </p>
     *
     * @return the absolute byte position in the stream
     */
    public long getStartOffset()
    {
        return startPosition;
    }

    /**
     * Calculates the absolute stream offset where this box ends.
     *
     * <p>
     * This value serves as a boundary to ensure the start of the next box can be accurately
     * located, even if any box constructor fails to consume all its allocated bytes.
     * </p>
     *
     * @return the absolute byte position of the next box in the stream
     */
    public long getEndPosition()
    {
        return (boxSize != BOX_SIZE_TO_EOF ? (startPosition + boxSize) : BOX_SIZE_TO_EOF);
    }

    /**
     * Sets the parent of this child box.
     *
     * @param outerbox
     *        the Box referencing to the parent box
     */
    public void setParent(Box outerbox)
    {
        parent = outerbox;
    }

    /**
     * Returns the parent box of this child box for referencing purposes
     *
     * @return the parent Box
     */
    public Box getParent()
    {
        return parent;
    }

    /**
     * Sets this box's hierarchical depth of this box. The root box has a depth of 0. Each
     * level below increases the depth by 1.
     *
     * @param depth
     *        the depth of this box
     */
    public void setHierarchyDepth(int depth)
    {
        hierarchyDepth = depth;
    }

    /**
     * Returns the depth of this box within the box hierarchy.
     *
     * @return the depth of this box in the hierarchy
     */
    public int getHierarchyDepth()
    {
        return hierarchyDepth;
    }

    /**
     * Returns the Four-Character Code (FourCC) identifying the box type.
     *
     * @return the 4-character box type string, for example: "meta", "iinf", etc
     */
    public String getFourCC()
    {
        return new String(boxTypeBytes, StandardCharsets.US_ASCII);
    }

    /**
     * Returns the 32-bit integer representation (FourCC) of the box type.
     *
     * <p>
     * This numeric value allows for high-efficiency comparisons and is used as the key for
     * {@link HeifBoxType} resolution.
     * </p>
     *
     * @return the integer type code
     */
    public int getTypeCode()
    {
        return typeCode;
    }

    /**
     * Returns the total size of this box, or {@link Long#MAX_VALUE} if size is unknown.
     *
     * @return the box size
     */
    public long getBoxSize()
    {
        return boxSize;
    }

    /**
     * Returns the user type for a {@code uuid} box, or an empty string if not applicable.
     *
     * @return the user type
     */
    public String getUserType()
    {
        return (userType == null ? "" : userType);
    }

    /**
     * Returns the {@link HeifBoxType} of this box.
     *
     * @return the type
     */
    public HeifBoxType getHeifType()
    {
        return type;
    }

    /**
     * Returns a list of child boxes, if applicable. Default is empty.
     *
     * @return list of contained boxes
     */
    public List<Box> getBoxList()
    {
        return Collections.emptyList();
    }

    /**
     * Logs the box hierarchy and internal entry data at the debug level.
     *
     * <p>
     * It provides a visual representation of the box's HEIF/ISO-BMFF structure. It is intended for
     * tree traversal and file inspection during development and degugging if required.
     * </p>
     */
    public void logBoxInfo()
    {
        String tab = Utils.repeatPrint("\t", getHierarchyDepth());
        LOGGER.debug(String.format("%sUn-handled Box '%s':\t\t%s", tab, getFourCC(), type.getTypeName()));
    }

    /**
     * Ensures the child box resides within the parent's byte boundaries.
     *
     * @param child
     *        the box to validate against this parent's limits
     * 
     * @throws IllegalStateException
     *         if the child boundary exceeds the parent boundary
     */
    protected void validateBounds(Box child) throws IllegalStateException
    {
        long parentEnd = this.getEndPosition();
        long childEnd = child.getEndPosition();

        if (this.boxSize != BOX_SIZE_TO_EOF && childEnd > parentEnd)
        {
            throw new IllegalStateException(String.format("Child [%s] ends at %d, exceeding parent [%s] boundary of %d", child.getFourCC(), childEnd, this.getFourCC(), parentEnd));
        }
    }
}