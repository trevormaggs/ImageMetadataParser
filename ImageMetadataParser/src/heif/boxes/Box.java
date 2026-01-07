package heif.boxes;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import common.ByteValueConverter;
import common.ByteStreamReader;
import heif.HeifBoxType;
import logger.LogFactory;

/**
 * Represents a generic HEIF Box, according to ISO/IEC 14496-12:2015. Handles both standard boxes
 * and {@code uuid} extended boxes.
 */
public class Box
{
    private static final LogFactory LOGGER = LogFactory.getLogger(Box.class);
    private static final long BOX_SIZE_TO_EOF = Long.MAX_VALUE;
    private final long boxSize;
    private final byte[] boxTypeBytes;
    private final HeifBoxType type;
    private final String userType;

    private Box parent;
    private int hierarchyDepth;
    private long startPosition;

    /**
     * Constructs a {@code Box} by reading its header from the specified
     * {@code ByteStreamReader}.
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
        startPosition = reader.getCurrentPosition();

        long size = reader.readUnsignedInteger();

        if (size > 1 && size < 8)
        {
            throw new IllegalStateException("Inconsistent box size detected [" + size + "]. It should strictly be 8 bytes or greater");
        }

        this.boxTypeBytes = reader.readBytes(4);
        this.type = HeifBoxType.fromTypeBytes(boxTypeBytes);
        this.boxSize = (size == 1 ? reader.readLong() : (size == 0 ? BOX_SIZE_TO_EOF : size));

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
        this.userType = box.userType;
        this.parent = box.parent;
        this.hierarchyDepth = box.hierarchyDepth;
        this.startPosition = box.startPosition;
    }

    /**
     * Returns the number of remaining bytes in the box.
     *
     * @param reader
     *        the stream reader for checking the current position
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

        return (startPosition + boxSize - reader.getCurrentPosition());
    }

    /**
     * Calculates the absolute file offset where this box ends.
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
        return startPosition + getBoxSize();
    }

    /**
     * Returns the absolute file offset where this box begins.
     * 
     * <p>
     * This is the position of the first byte of the box header (the start of the 4-byte size
     * field).
     * </p>
     * 
     * @return the absolute byte position of the box in the stream
     */
    public long getOffset()
    {
        return startPosition;
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
     * @return the Box reference
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
     * Returns the total size of this box, or {@link Long#MAX_VALUE} if size is unknown.
     *
     * @return the box size
     */
    public long getBoxSize()
    {
        return boxSize;
    }

    /**
     * Returns the user type for a {@code uuid} box, or an empty Optional if not applicable.
     *
     * @return optional user type
     */
    public Optional<String> getUserType()
    {
        return Optional.ofNullable(userType);
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
        String tab = repeatPrint("\t", getHierarchyDepth());
        LOGGER.debug(String.format("%sUn-handled Box '%s':\t\t%s", tab, getFourCC(), type.getTypeName()));
    }

    /**
     * Generates a line of padded characters to n of times.
     * 
     * @param ch
     *        string to be padded
     * @param n
     *        number of times to pad in integer form
     * 
     * @return formatted string
     */
    protected static String repeatPrint(String ch, int n)
    {
        if (n <= 0)
        {
            return "";
        }

        StringBuilder sb = new StringBuilder(ch.length() * n);

        for (int i = 0; i < n; i++)
        {
            sb.append(ch);
        }

        return sb.toString();
    }

    /**
     * Returns the byte order, assuring the correct interpretation of data values. For HEIC files,
     * the big-endian-ness is the standard configuration.
     *
     * @return always {@link java.nio.ByteOrder#BIG_ENDIAN}
     */
    @Deprecated
    public ByteOrder getByteOrder()
    {
        return ByteOrder.BIG_ENDIAN;
    }
}