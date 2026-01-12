package heif.boxes;

import java.io.IOException;
import common.ByteStreamReader;
import common.Utils;
import logger.LogFactory;

/**
 * Represents the {@code ipma} (Item Property Association Box) in HEIF/ISOBMFF files.
 *
 * <p>
 * The {@code ipma} box defines associations between items and their properties. Each item can
 * reference multiple properties, and each property can be marked as essential or non-essential for
 * decoding the item.
 * </p>
 *
 * <p>
 * This class supports both version 0 and version 1 of the {@code ipma} box format. The structure is
 * specified in the ISO/IEC 23008-12:2017 (HEIF) on Page 28 document.
 * </p>
 *
 * <p>
 * <strong>API Note:</strong> Further testing may be required to validate the reliability and
 * robustness of this implementation.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class ItemPropertyAssociationBox extends FullBox
{
    private static final LogFactory LOGGER = LogFactory.getLogger(ItemPropertyAssociationBox.class);
    private final ItemPropertyEntry[] entries;

    /**
     * Constructs an {@code ItemPropertyAssociationBox} object, parsing its structure from the
     * specified {@link ByteStreamReader}.
     *
     * @param box
     *        the base {@link Box} object containing size and type information
     * @param reader
     *        the {@link ByteStreamReader} for sequential byte access
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public ItemPropertyAssociationBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box, reader);

        ItemPropertyEntry[] localEntries = null;

        try
        {
            int entryCount = (int) reader.readUnsignedInteger();
            
            localEntries = new ItemPropertyEntry[entryCount];

            for (int i = 0; i < entryCount; i++)
            {
                int itemID = (getVersion() < 1 ? reader.readUnsignedShort() : (int) reader.readUnsignedInteger());
                int associationCount = reader.readUnsignedByte();
                ItemPropertyEntry entry = new ItemPropertyEntry(itemID, associationCount);

                for (int j = 0; j < associationCount; j++)
                {
                    int value = isFlagSet(0x01) ? reader.readUnsignedShort() : reader.readUnsignedByte();
                    boolean essential = (isFlagSet(0x01)) ? (value & 0x8000) != 0 : (value & 0x80) != 0;
                    int propertyIndex = (isFlagSet(0x01)) ? (value & 0x7FFF) : (value & 0x7F);

                    entry.setAssociation(j, essential, propertyIndex);
                }

                localEntries[i] = entry;
            }
        }

        finally
        {
            /* Makes sure any paddings or trailing alignment bytes are fully consumed */
            long remaining = getEndPosition() - reader.getCurrentPosition();

            if (remaining > 0)
            {
                reader.skip(remaining);
                LOGGER.debug(String.format("Skipping %d bytes of padding in [%s]", remaining, getFourCC()));
            }

            this.entries = (localEntries == null ? new ItemPropertyEntry[0] : localEntries);
        }
    }

    /**
     * Returns the total number of items that have property associations in this box.
     *
     * @return the number of entries in the {@code ipma} box
     */
    public int getEntryCount()
    {
        return entries.length;
    }

    /**
     * Retrieves the item ID for the entry at the specified array index.
     * 
     * <p>
     * Note: This refers to the index within the {@code entries} array, not the {@code item_ID}
     * itself.
     * </p>
     *
     * @param index
     *        the 0-based index of the entry to retrieve
     * @return the unique identifier of the item (image or metadata)
     * 
     * @throws ArrayIndexOutOfBoundsException
     *         if the index is out of range
     */
    public int getItemIDAt(int index)
    {
        return entries[index].getItemID();
    }

    /**
     * Retrieves the number of property associations defined for the entry at the specified array
     * index.
     *
     * @param index
     *        the 0-based index of the entry to retrieve
     * @return the count of properties associated with the item at this index
     * 
     * @throws ArrayIndexOutOfBoundsException
     *         if the index is out of range
     */
    public int getAssociationCountAt(int index)
    {
        return entries[index].getAssociationCount();
    }

    /**
     * Retrieves the 1-based property indices associated with the specified item ID.
     * 
     * @param itemID
     *        the unique identifier of the image or metadata item
     * @return an array of 1-based indices into the property container, or an empty array if no
     *         associations exist for the given ID
     */
    public int[] getPropertyIndicesArray(int itemID)
    {
        for (ItemPropertyEntry entry : entries)
        {
            if (entry.getItemID() == itemID)
            {
                int[] indices = new int[entry.getAssociationCount()];

                for (int i = 0; i < entry.getAssociationCount(); i++)
                {
                    indices[i] = entry.getAssociations()[i].getPropertyIndex();
                }

                return indices;
            }
        }

        return new int[0];
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
        LOGGER.debug(String.format("%s%s '%s':\t\tentry_count=%d", tab, this.getClass().getSimpleName(), getFourCC(), entries.length));

        for (int i = 0; i < entries.length; i++)
        {
            ItemPropertyEntry entry = entries[i];

            LOGGER.debug(String.format("\t%s%d)\titem_ID=%d,\tassociation_count=%d", tab, i + 1, entry.getItemID(), entry.getAssociationCount()));

            for (ItemPropertyEntryAssociation assoc : entry.getAssociations())
            {
                LOGGER.debug(String.format("\t\t\t\t\t%sessential=%s, property_index=%d", tab, assoc.isEssential(), assoc.getPropertyIndex()));
            }
        }
    }

    /**
     * Represents a single item's property associations in the {@code ipma} box.
     */
    private static class ItemPropertyEntry
    {
        private final int itemID;
        private final int associationCount;
        private final ItemPropertyEntryAssociation[] associations;

        /**
         * Constructs an {@code ItemPropertyEntry} with the specified item ID and number of
         * associations.
         *
         * @param itemID
         *        the identifier of the item
         * @param count
         *        the number of property associations for this item
         */
        private ItemPropertyEntry(int itemID, int count)
        {
            this.itemID = itemID;
            this.associationCount = count;
            this.associations = new ItemPropertyEntryAssociation[count];
        }

        /**
         * Sets the association at the specified index.
         *
         * @param index
         *        the index to set
         * @param essential
         *        true if the property is essential, otherwise false
         * @param propertyIndex
         *        the 1-based index of the property in the ipco box
         */
        private void setAssociation(int index, boolean essential, int propertyIndex)
        {
            associations[index] = new ItemPropertyEntryAssociation(essential, propertyIndex);
        }

        /** @return the ID of the associated item */
        private int getItemID()
        {
            return itemID;
        }

        /** @return the number of associations for this item */
        private int getAssociationCount()
        {
            return associationCount;
        }

        /** @return the list of associations for this item */
        private ItemPropertyEntryAssociation[] getAssociations()
        {
            return associations;
        }
    }

    /**
     * Represents a single association between an item and a property.
     */
    private static class ItemPropertyEntryAssociation
    {
        private final boolean essential;
        private final int propertyIndex;

        /**
         * Constructs an association between an item and a property.
         *
         * @param essential
         *        whether the property is essential
         * @param propertyIndex
         *        the 1-based index of the property in the ipco box
         */
        private ItemPropertyEntryAssociation(boolean essential, int propertyIndex)
        {
            this.essential = essential;
            this.propertyIndex = propertyIndex;
        }

        /**
         * Returns the Essential value as a boolean value.
         *
         * @return true if the property is essential, otherwise false
         */
        private boolean isEssential()
        {
            return essential;
        }

        /**
         * Returns the Property Index.
         *
         * @return the 1-based property index in the ipco box
         */
        private int getPropertyIndex()
        {
            return propertyIndex;
        }
    }
}