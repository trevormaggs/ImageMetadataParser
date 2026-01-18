package heif.boxes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import common.ByteStreamReader;
import common.Utils;
import heif.BoxFactory;
import heif.HeifBoxType;
import logger.LogFactory;

/**
 * Represents the {@code iprp} (Item Properties Box) in a HEIF/HEIC file structure.
 *
 * <p>
 * The {@code ItemPropertiesBox} allows the definition of properties that describe specific
 * characteristics of media items, such as images or auxiliary data. Each property is stored
 * in the {@code ipco} (ItemPropertyContainerBox), while associations between items and their
 * properties are managed through one or more {@code ipma} (ItemPropertyAssociationBox) entries.
 * </p>
 *
 * <p>
 * <b>Box Structure:</b>
 * </p>
 *
 * <ul>
 * <li>{@code ipco} – Contains an implicitly indexed list of property boxes</li>
 * <li>{@code ipma} – Maps items to property indices defined in {@code ipco}</li>
 * </ul>
 *
 * <p>
 * <b>Specification Reference:</b>
 * </p>
 *
 * <ul>
 * <li>ISO/IEC 23008-12:2017 (Page 28)</li>
 * </ul>
 *
 * <p>
 * <strong>API Note:</strong>This implementation assumes a flat box hierarchy. Additional testing is
 * recommended for nested or complex structures.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class ItemPropertiesBox extends Box
{
    private static final LogFactory LOGGER = LogFactory.getLogger(ItemPropertiesBox.class);
    private final ItemPropertyContainerBox ipco;
    private final List<ItemPropertyAssociationBox> associations = new ArrayList<>();

    /**
     * Constructs an {@code ItemPropertiesBox} by reading the {@code ipco} (property container) and
     * one or more {@code ipma} (item-property association) boxes.
     *
     * The {@code ItemPropertiesBox} consists of two parts: {@code ItemPropertyContainerBox} that
     * contains an implicitly indexed list of item properties, and one or more
     * {@code ItemPropertyAssociation} boxes that associate items with item properties.
     *
     * @param box
     *        the parent Box header containing size and type
     * @param reader
     *        a {@code ByteStreamReader} to read the box content
     * 
     * @throws IOException
     *         if an I/O error occurs
     * @throws IllegalStateException
     *         if malformed data is encountered, such as a negative box size and corrupted data
     */
    public ItemPropertiesBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box);

        ItemPropertyContainerBox ipcoBox = null;

        while (reader.getCurrentPosition() + 8 <= getEndPosition())
        {
            Box child = new Box(reader);

            if (child.getHeifType() == HeifBoxType.ITEM_PROPERTY_CONTAINER)
            {
                ipcoBox = new ItemPropertyContainerBox(child, reader);
            }

            else if (child.getHeifType() == HeifBoxType.ITEM_PROPERTY_ASSOCIATION)
            {
                this.associations.add(new ItemPropertyAssociationBox(child, reader));
            }

            else
            {
                LOGGER.debug("Skipping unknown box [" + child.getFourCC() + "] inside iprp");
                reader.skip(child.getBoxSize() - 8); // 8 bytes already consumed in the parent Box
            }
        }

        if (ipcoBox == null)
        {
            throw new IllegalStateException("Mandatory [ipco] box missing from [iprp]");
        }

        /* Makes sure any paddings or trailing alignment bytes are fully consumed */
        long remaining = getEndPosition() - reader.getCurrentPosition();

        if (remaining > 0)
        {
            reader.skip(remaining);
            LOGGER.debug(String.format("Skipping %d bytes of padding in [%s]", remaining, getFourCC()));
        }

        this.ipco = ipcoBox;
    }

    /**
     * Returns the reference to the {@code ItemPropertyContainerBox}.
     */
    public ItemPropertyContainerBox getItemPropertyContainerBox()
    {
        return ipco;
    }

    /**
     * Returns the reference to the {@code ItemPropertyAssociationBox}.
     */
    public ItemPropertyAssociationBox getItemPropertyAssociationBox()
    {
        return associations.get(0);
    }

    /**
     * Retrieves a property by its 1-based index from the container.
     */
    public Box getPropertyByIndex(int index)
    {
        return ipco.getProperty(index);
    }

    /**
     * Resolves all property boxes associated with a specific item ID.
     * 
     * @param itemID
     *        the ID of the item, for example: from pitm or infe, etc
     * 
     * @return a list of properties, for example: ispe, irot, colr, etc associated with the
     *         specified item ID
     */
    public List<Box> getPropertyListByItem(int itemID)
    {
        List<Box> results = new ArrayList<>();

        for (ItemPropertyAssociationBox ipma : associations)
        {
            int[] indices = ipma.getPropertyIndicesArray(itemID);

            for (int index : indices)
            {
                // Index 0 means "no property" according to the HEIF specification
                if (index > 0)
                {
                    results.add(getPropertyByIndex(index));
                }
            }
        }

        return results;
    }

    /**
     * Returns a combined list of all boxes contained in this {@code ItemPropertiesBox}, including
     * both property container and associations.
     *
     * @return a list of Box objects in reading order
     */
    @Override
    public List<Box> getBoxList()
    {
        List<Box> list = new ArrayList<>();

        list.add(ipco);
        list.addAll(associations);

        return list;
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
     * Represents the {@code ipco} (ItemPropertyContainerBox), a nested container holding an
     * implicitly indexed list of item property boxes.
     *
     * <p>
     * Each property describes an aspect of an image or media item, such as color information, pixel
     * layout, or transformation metadata.
     * </p>
     *
     * <p>
     * Refer to the specification document - {@code ISO/IEC 23008-12:2017} on Page 28 for more
     * information.
     * </p>
     */
    public static final class ItemPropertyContainerBox extends Box
    {
        private List<Box> properties = new ArrayList<>();

        /**
         * Constructs an {@code ItemPropertyContainerBox} resource by reading sequential boxes.
         *
         * <p>
         * Each property box is read, added to the property list, and skipped over to handle cases
         * where specific handlers for sub-boxes may not yet be implemented.
         * </p>
         *
         * @param box
         *        the parent Box containing size and header information
         * @param reader
         *        the stream byte reader for parsing box data
         * 
         * @throws IOException
         *         if an I/O error occurs
         * @throws IllegalStateException
         *         if any form of data corruption is detected
         */
        private ItemPropertyContainerBox(Box box, ByteStreamReader reader) throws IOException
        {
            super(box);

            try
            {

                while (reader.getCurrentPosition() + 8 <= getEndPosition())
                {
                    Box childBox = BoxFactory.createBox(reader);

                    validateBounds(childBox);

                    if (childBox.getHeifType() == HeifBoxType.UNKNOWN)
                    {
                        LOGGER.debug("Unknown property box encountered: " + childBox.getFourCC());
                    }

                    properties.add(childBox);
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
            }
        }

        /**
         * Retrieves the property box based on the specified index.
         * 
         * <p>
         * According to the HEIF specification in ISO/IEC 23008-12:2017, page 28, property index is
         * either 0 indicating that no property is associated, or is the 1-based index of the
         * associated property box in the {@code ItemPropertyContainerBox} contained in the same
         * {@code ItemPropertiesBox}.
         * </p>
         *
         * @return a property box, such as ispe, imir, irot etc
         */
        private Box getProperty(int propertyIndex)
        {
            if (propertyIndex < 1 || propertyIndex > properties.size())
            {
                throw new IllegalArgumentException("Property Index is 1-based. Must be between [1 and " + properties.size() + "]. Found [" + propertyIndex + "]");
            }

            return properties.get(propertyIndex - 1);
        }

        /**
         * Returns a list of all boxes contained in this {@code ItemPropertyContainerBox},
         * specifically all properties.
         *
         * @return a list of Box objects in reading order
         */
        @Override
        public List<Box> getBoxList()
        {
            return Collections.unmodifiableList(properties);
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
            String tab = Utils.repeatPrint("\t", getHierarchyDepth());
            LOGGER.debug(String.format("%s%s '%s':", tab, getClass().getSimpleName(), getFourCC()));
        }
    }
}