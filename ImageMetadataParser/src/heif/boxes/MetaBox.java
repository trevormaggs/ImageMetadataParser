package heif.boxes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import common.ByteStreamReader;
import heif.BoxFactory;
import logger.LogFactory;

/**
 * Represents a MetaBox {@code meta} structure in HEIF/ISOBMFF files.
 *
 * The MetaBox contains metadata and subordinate boxes such as ItemInfoBox, ItemLocationBox and
 * more. It acts as a container for descriptive and structural metadata relevant to HEIF-based
 * formats.
 *
 * For technical details, refer to ISO/IEC 14496-12:2015, Page 76 (Meta Box).
 *
 * <p>
 * <strong>API Note:</strong> Additional testing is required to validate the reliability and
 * robustness of this implementation.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class MetaBox extends FullBox
{
    private static final LogFactory LOGGER = LogFactory.getLogger(MetaBox.class);
    private final List<Box> containedBoxList;

    /**
     * Constructs a {@code MetaBox}, parsing its fields from the specified
     * {@link ByteStreamReader}.
     *
     * @param box
     *        the parent {@link Box} object containing size and type information
     * @param reader
     *        the byte reader for parsing box data
     *
     * @throws IOException
     *         if an I/O error occurs
     * @throws IllegalStateException
     *         if malformed data is encountered, such as a negative box size and corrupted data
     */
    public MetaBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box, reader);

        markSegment(reader.getCurrentPosition());

        List<Box> children = new ArrayList<>();

        while (reader.getCurrentPosition() < getEndPosition())
        {
            Box child = BoxFactory.createBox(reader);

            child.setParent(this);
            child.setHierarchyDepth(this.getHierarchyDepth() + 1);
            children.add(child);
        }

        this.containedBoxList = children;

        if (reader.getCurrentPosition() != getEndPosition())
        {
            throw new IllegalStateException("Mismatch in expected box size for [" + getFourCC() + "]");
        }

        commitSegment(reader.getCurrentPosition());
    }

    /**
     * Returns a combined list of all boxes contained in this {@code MetaBox}.
     *
     * @return a list of Box objects in reading order
     */
    @Override
    public List<Box> getBoxList()
    {
        return Collections.unmodifiableList(containedBoxList);
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
        String tab = Box.repeatPrint("\t", getHierarchyDepth());
        LOGGER.debug(String.format("%s%s '%s':\t\t(%s)", tab, this.getClass().getSimpleName(), getFourCC(), getHeifType().getBoxCategory()));
    }
}