package heif.boxes;

import java.io.IOException;
import common.ByteStreamReader;
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
 * <strong>API Note:</strong> Additional testing is required to validate the reliability and
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

        int entryCount = (int) reader.readUnsignedInteger();
        
        entries = new ItemPropertyEntry[entryCount];

        for (int i = 0; i < entryCount; i++)
        {
            int itemID = (getVersion() < 1 ? reader.readUnsignedShort() : (int) reader.readUnsignedInteger());
            int associationCount = reader.readUnsignedByte();
            ItemPropertyEntry entry = new ItemPropertyEntry(itemID, associationCount);

            for (int j = 0; j < associationCount; j++)
            {
                int value;
                boolean essential;
                int propertyIndex;

                if (isFlagSet(0x01))
                {
                    value = reader.readUnsignedShort();
                    essential = ((value & 0x8000) != 0);
                    propertyIndex = (value & 0x7FFF);
                }

                else
                {
                    value = reader.readUnsignedByte();
                    essential = ((value & 0x80) != 0);
                    propertyIndex = (value & 0x7F);
                }

                entry.setAssociation(j, essential, propertyIndex);
            }

            entries[i] = entry;
        }
    }

    /**
     * @return the number of item property association entries.
     */
    public int getEntryCount()
    {
        return entries.length;
    }

    /**
     * @return the list of {@link ItemPropertyEntry} objects.
     */
    public ItemPropertyEntry[] getEntries()
    {
        return entries;
    }

    /**
     * TESTING...
     * 
     * Returns all property indices associated with a specific item.
     * 
     * @param itemID the ID of the image or metadata item
     * 
     * @return an array of 1-based property indices
     */
    public int[] getPropertyIndicesForItem(int itemID)
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
        String tab = Box.repeatPrint("\t", getHierarchyDepth());
        LOGGER.debug(String.format("%s%s '%s':\t\tentry_count=%d", tab, this.getClass().getSimpleName(), getFourCC(), getEntryCount()));

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
        public ItemPropertyEntry(int itemID, int count)
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
        public void setAssociation(int index, boolean essential, int propertyIndex)
        {
            associations[index] = new ItemPropertyEntryAssociation(essential, propertyIndex);
        }

        /** @return the ID of the associated item */
        public int getItemID()
        {
            return itemID;
        }

        /** @return the number of associations for this item */
        public int getAssociationCount()
        {
            return associationCount;
        }

        /** @return the list of associations for this item */
        public ItemPropertyEntryAssociation[] getAssociations()
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
        public ItemPropertyEntryAssociation(boolean essential, int propertyIndex)
        {
            this.essential = essential;
            this.propertyIndex = propertyIndex;
        }

        /**
         * Returns the Essential value as a boolean value.
         *
         * @return true if the property is essential, otherwise false
         */
        public boolean isEssential()
        {
            return essential;
        }

        /**
         * Returns the Property Index.
         *
         * @return the 1-based property index in the ipco box
         */
        public int getPropertyIndex()
        {
            return propertyIndex;
        }
    }
}