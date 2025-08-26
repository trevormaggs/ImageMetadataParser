package heif.boxes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import common.SequentialByteReader;
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
    private final List<Box> references;

    /**
     * Constructs an {@code ItemReferenceBox}, reading its references from the specified
     * {@link SequentialByteReader} resource.
     *
     * @param box
     *        the parent {@link Box} containing size and type information
     * @param reader
     *        the reader for sequential byte parsing
     *
     * @throws IllegalStateException
     *         if malformed data is encountered, such as a negative box size and corrupted data
     */
    public ItemReferenceBox(Box box, SequentialByteReader reader)
    {
        super(box, reader);

        long startpos = reader.getCurrentPosition();
        long endpos = startpos + available();

        references = new ArrayList<>();

        do
        {
            references.add(new SingleItemTypeReferenceBox(new Box(reader), reader, (getVersion() != 0)));

        } while (reader.getCurrentPosition() < endpos);

        if (reader.getCurrentPosition() != endpos)
        {
            throw new IllegalStateException("Mismatch in expected box size for [" + getTypeAsString() + "]");
        }

        byteUsed += reader.getCurrentPosition() - startpos;
    }

    /**
     * Returns the list of {@code SingleItemTypeReferenceBox} entries contained in this
     * {@code ItemReferenceBox} resource.
     *
     * @return an unmodifiable list of references
     */
    public List<Box> getReferences()
    {
        return Collections.unmodifiableList(references);
    }

    /**
     * Returns the list of child boxes.
     *
     * @return the list of reference boxes, or an empty list if none exist
     */
    @Override
    public List<Box> getBoxList()
    {
        return references != null ? references : Collections.emptyList();
    }

    /**
     * Logs a single diagnostic line for this box at the debug level.
     *
     * <p>
     * This is useful when traversing the box tree of a HEIF/ISO-BMFF structure for debugging or
     * inspection purposes.
     * </p>
     */
    @Override
    public void logBoxInfo()
    {
        String tab = Box.repeatPrint("\t", getHierarchyDepth());
        LOGGER.debug(String.format("%s%s '%s':", tab, this.getClass().getSimpleName(), getTypeAsString()));
    }

    /**
     * Represents a {@code SingleItemTypeReferenceBox} resource, which stores a group of item
     * references of a specific type.
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
         * Constructs a {@code SingleItemTypeReferenceBox} by reading its fields from the given
         * {@link SequentialByteReader}.
         *
         * @param box
         *        the parent {@link Box} containing size and type information
         * @param reader
         *        the reader for sequential byte parsing
         * @param large
         *        indicates whether 32-bit item IDs are used ({@code version != 0})
         */
        public SingleItemTypeReferenceBox(Box box, SequentialByteReader reader, boolean large)
        {
            super(box);

            fromItemID = large ? reader.readUnsignedInteger() : reader.readUnsignedShort();
            referenceCount = reader.readUnsignedShort();
            toItemID = new long[referenceCount];

            for (int j = 0; j < referenceCount; j++)
            {
                toItemID[j] = large ? reader.readUnsignedInteger() : reader.readUnsignedShort();
            }
        }

        /**
         * Logs a single diagnostic line for this box at the debug level.
         *
         * <p>
         * This is useful when traversing the box tree of a HEIF/ISO-BMFF structure for debugging or
         * inspection purposes.
         * </p>
         */
        @Override
        public void logBoxInfo()
        {
            StringBuilder sb = new StringBuilder();
            String tab = Box.repeatPrint("\t", getHierarchyDepth());

            sb.append(String.format("%sreferenceType='%s': from_item_ID=%d, ref_count=%d, to_item_ID=", tab, getTypeAsString(), fromItemID, referenceCount));

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