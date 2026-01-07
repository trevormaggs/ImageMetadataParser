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
public class BoxOld
{
    private static final LogFactory LOGGER = LogFactory.getLogger(BoxOld.class);
    private static final long BOX_SIZE_TO_EOF = Long.MAX_VALUE;
    private final ByteOrder order;
    private final long boxSize;
    private final byte[] boxTypeBytes;
    private final String userType;
    private final HeifBoxType type;
    private BoxOld parent;
    private int hierarchyDepth;
    protected long startPos = 0;
    protected long byteUsed;

    /**
     * Constructs a {@code Box} by reading its header from the specified
     * {@code ByteStreamReader}.
     *
     * @param reader
     *        the byte reader for parsing
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public BoxOld(ByteStreamReader reader) throws IOException
    {
        markSegment(reader.getCurrentPosition());

        this.order = reader.getByteOrder();
        long sizeField = reader.readUnsignedInteger();
        this.boxTypeBytes = reader.readBytes(4);
        this.type = HeifBoxType.fromTypeBytes(boxTypeBytes);
        this.boxSize = (sizeField == 1 ? reader.readLong() : (sizeField == 0 ? BOX_SIZE_TO_EOF : sizeField));

        if (type == HeifBoxType.UUID)
        {
            byte[] uuidBytes = reader.readBytes(16);
            this.userType = ByteValueConverter.toHex(uuidBytes);
        }

        else
        {
            this.userType = null;
        }

        commitSegment(reader.getCurrentPosition());
    }

    /**
     * A copy constructor to replicate field values one by one, useful for sub-classing, retaining
     * the original field values.
     *
     * @param box
     *        the box to copy
     */
    public BoxOld(BoxOld box)
    {
        this.order = box.order;
        this.boxSize = box.boxSize;
        this.boxTypeBytes = box.boxTypeBytes.clone();
        this.userType = box.userType;
        this.type = box.type;
        this.byteUsed = box.byteUsed;
        this.parent = box.parent;
        this.startPos = box.startPos;
    }

    /**
     * Sets the anchor point for the current parsing segment.
     */
    protected void markSegment(long currentPos)
    {
        startPos = currentPos;
    }

    /**
     * Calculates the bytes consumed since the last mark and adds them to the total used.
     */
    protected void commitSegment(long currentPos)
    {
        long delta = currentPos - startPos;

        if (delta < 0)
        {
            LOGGER.error("Negative segment length detected in " + getFourCC());
            return;
        }

        byteUsed += delta;
    }

    /**
     * Sets the parent of this child box.
     *
     * @param parent
     *        the Box referencing to the parent box
     */
    public void setParent(BoxOld outerbox)
    {
        parent = outerbox;
    }

    /**
     * Returns the parent box of this child box for referencing purposes
     *
     * @return the Box reference
     */
    public BoxOld getParent()
    {
        return parent;
    }

    /**
     * Sets this box's hierarchical depth of this box. The root box has a depth of 0. Each
     * level below increases the depth by 1.
     *
     * @return the depth of this box in the hierarchy
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
     * Returns the byte order, assuring the correct interpretation of data values. For HEIC files,
     * the big-endian-ness is the standard configuration.
     *
     * @return either {@link java.nio.ByteOrder#BIG_ENDIAN} or
     *         {@link java.nio.ByteOrder#LITTLE_ENDIAN}
     */
    public ByteOrder getByteOrder()
    {
        return order;
    }

    /**
     * Returns the 4-character box type as a string.
     *
     * @return the box type in textual form
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
     * Returns the number of remaining bytes in the box.
     *
     * @return remaining bytes
     * 
     * @throws IllegalStateException
     *         if malformed data is encountered. The box size is unknown (extends to EOF)
     */
    public long available()
    {
        if (boxSize == BOX_SIZE_TO_EOF)
        {
            throw new IllegalStateException("Box size is unknown (extends to EOF). Remaining size cannot be calculated");
        }

        return (boxSize - byteUsed);
    }

    /**
     * Returns the number of bytes already read from this box.
     *
     * @return bytes read so far
     */
    public long byteUsed()
    {
        return byteUsed;
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
    public List<BoxOld> getBoxList()
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
        LOGGER.debug(String.format("%s'%s':\t\t\t%s", tab, getFourCC(), type.getTypeName()));
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
}