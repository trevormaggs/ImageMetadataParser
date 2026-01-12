package heif.boxes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import common.ByteStreamReader;
import common.Utils;
import logger.LogFactory;

/**
 * Represents the {@code iref} (Item Reference Box) in HEIF/ISOBMFF files.
 *
 * <p>
 * The Item Reference Box allows one item to reference other items using typed references. Each set
 * of references for a specific type is stored in a {@code SingleItemTypeReferenceBox}. This
 * structure enables relationships between media items, such as thumbnails, auxiliary images, or
 * alternate representations.
 * </p>
 *
 * <p>
 * Specification Reference: ISO/IEC 14496-12:2015, Section 8.11.12 (Page 87).
 * </p>
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
public class ItemReferenceBox extends FullBox
{
    private static final LogFactory LOGGER = LogFactory.getLogger(ItemReferenceBox.class);
    private final List<Box> references = new ArrayList<>();

    /**
     * Constructs an {@code ItemReferenceBox}, reading its references from the specified
     * {@link ByteStreamReader} stream.
     *
     * @param box
     *        the parent {@link Box} containing size and type information
     * @param reader
     *        the stream resource to enable byte parsing
     * 
     * @throws IOException
     *         if an I/O error occurs
     * @throws IllegalStateException
     *         if malformed data is detected
     */
    public ItemReferenceBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box, reader);

        while (reader.getCurrentPosition() + 8 <= getEndPosition())
        {
            Box child = new Box(reader);
            
            child.setParent(this);
            child.setHierarchyDepth(this.getHierarchyDepth() + 1);
            
            references.add(new SingleItemTypeReferenceBox(child, reader, getVersion()));
        }

        /* Makes sure any paddings or trailing alignment bytes are fully consumed */
        long remaining = getEndPosition() - reader.getCurrentPosition();

        if (remaining > 0)
        {
            reader.skip(remaining);
            LOGGER.debug(String.format("Skipping %d bytes of padding in [%s]", remaining, getFourCC()));
        }
    }

    /**
     * Finds all items (from_item_ID) that reference a specific target item (to_item_ID) using the
     * specified reference type.
     *
     * @param refType
     *        the reference type, i.e. "cdsc", "thmb"
     * @param targetId
     *        the ID of the item being referenced
     * @return a list of referencing item IDs
     */
    public List<Integer> findLinksTo(String refType, int targetId)
    {
        List<Integer> fromIds = new ArrayList<>();

        for (Box box : getBoxList())
        {
            if (box instanceof SingleItemTypeReferenceBox)
            {
                SingleItemTypeReferenceBox ref = (SingleItemTypeReferenceBox) box;

                if (refType.equals(ref.getFourCC()))
                {
                    for (long toId : ref.getToItemIDs())
                    {
                        if (toId == targetId)
                        {
                            fromIds.add((int) ref.getFromItemID());
                        }
                    }
                }
            }
        }

        return fromIds;
    }

    /**
     * Returns the list of child boxes.
     *
     * @return the list of reference boxes, or an empty list if none exist
     */
    @Override
    public List<Box> getBoxList()
    {
        return (references == null ? Collections.emptyList() : Collections.unmodifiableList(references));
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
        LOGGER.debug(String.format("%s%s '%s':", tab, this.getClass().getSimpleName(), getFourCC()));
    }

    /**
     * Represents a {@code SingleItemTypeReferenceBox} box, which stores a group of item references
     * of a specific type.
     *
     * <p>
     * Each reference links a {@code fromItemID} to one or more {@code toItemID} targets. The
     * reference type is identified by the box's name, for example, {@code thmb} for thumbnail.
     * </p>
     */
    public static class SingleItemTypeReferenceBox extends Box
    {
        private long fromItemID;
        private int referenceCount;
        private long[] toItemID;

        /**
         * Constructs a {@code SingleItemTypeReferenceBox} by reading its fields from the specified
         * stream.
         *
         * @param box
         *        the parent {@link Box} containing size and type information
         * @param reader
         *        the stream resource to enable byte parsing
         * @param version
         *        indicates the version associated with this box. Basically, it checks if 32-bit
         *        item IDs are used where {@code version} is not zero
         * 
         * @throws IOException
         *         if an I/O error occurs
         */
        public SingleItemTypeReferenceBox(Box box, ByteStreamReader reader, int version) throws IOException
        {
            super(box);

            boolean id32bit = (version != 0);
            int bytesPerId = id32bit ? 4 : 2;

            fromItemID = id32bit ? reader.readUnsignedInteger() : reader.readUnsignedShort();
            referenceCount = reader.readUnsignedShort();

            if (referenceCount > available(reader) / bytesPerId)
            {
                throw new IllegalStateException("Reference Count too large for remaining [" + available(reader) + "] bytes");
            }

            toItemID = new long[referenceCount];

            for (int j = 0; j < referenceCount; j++)
            {
                toItemID[j] = id32bit ? reader.readUnsignedInteger() : reader.readUnsignedShort();
            }
        }

        /**
         * Gets the ID of the item that is the source of the reference. For {@code cdsc}, this is
         * typically the Metadata Item ID.
         * 
         * @return the source item ID
         */
        public long getFromItemID()
        {
            return fromItemID;
        }

        /**
         * Gets the IDs of the items being referenced as targets. For {@code cdsc}, this is
         * typically the Image Item ID(s). In simplicity, each box contains one from_item_ID and
         * multiple to_item_IDs.
         * 
         * @return a clone of the target item ID array
         */
        public long[] getToItemIDs()
        {
            return toItemID != null ? toItemID.clone() : new long[0];
        }

        /**
         * Logs the box hierarchy and internal entry data at the debug level.
         *
         * <p>
         * It provides a visual representation of the box's HEIF/ISO-BMFF structure. It is intended
         * for tree traversal and file inspection during development and degugging if required.
         * </p>
         */
        @Override
        public void logBoxInfo()
        {
            StringBuilder sb = new StringBuilder();
            String tab = Utils.repeatPrint("\t", getHierarchyDepth());

            sb.append(String.format("%sreferenceType '%s':\t\tfrom_item_ID=%d, ref_count=%d, to_item_ID=", tab, getFourCC(), fromItemID, referenceCount));

            for (int j = 0; j < referenceCount; j++)
            {
                sb.append(toItemID[j]);

                if (j < referenceCount - 1)
                {
                    sb.append(", ");
                }
            }

            LOGGER.debug(sb.toString());
        }
    }
}