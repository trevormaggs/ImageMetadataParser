package heif.boxes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import common.ByteStreamReader;
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
    private final List<ItemPropertyAssociationBox> associations;

    /**
     * Constructs an {@code ItemPropertiesBox} by reading the {@code ipco} (property container) and
     * one or more {@code ipma} (item-property association) boxes.
     *
     * The ItemPropertiesBox consists of two parts: {@code ItemPropertyContainerBox} that contains
     * an implicitly indexed list of item properties, and one or more ItemPropertyAssociation boxes
     * that associate items with item properties.
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

        markSegment(reader.getCurrentPosition());

        ipco = new ItemPropertyContainerBox(new Box(reader), reader);

        if (!ipco.getFourCC().equals("ipco"))
        {
            throw new IllegalStateException("Expected [ipco] box, but found [" + ipco.getFourCC() + "]");
        }

        associations = new ArrayList<>();

        while (reader.getCurrentPosition() < getEndPosition())
        {
            associations.add(new ItemPropertyAssociationBox(new Box(reader), reader));

        }

        if (reader.getCurrentPosition() != getEndPosition())
        {
            throw new IllegalStateException("Mismatch in expected box size for [" + getFourCC() + "]");
        }

        commitSegment(reader.getCurrentPosition());
    }

    /**
     * Retrieves the list of property boxes contained within the {@code ipco} section.
     *
     * @return a list of property Box objects
     */
    public List<Box> getProperties()
    {
        return Collections.unmodifiableList(ipco.properties);
    }

    /**
     * Retrieves the list of item-property associations from the {@code ipma} section.
     *
     * @return a list of ItemPropertyAssociationBox objects
     */
    public List<ItemPropertyAssociationBox> getAssociations()
    {
        return Collections.unmodifiableList(associations);
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
        List<Box> combinedList = new ArrayList<>();

        combinedList.add(ipco);
        combinedList.addAll(associations);

        return combinedList;
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
    private static final class ItemPropertyContainerBox extends Box
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
         *        the sequential byte reader for parsing box data
         * 
         * @throws IOException
         *         if an I/O error occurs
         * @throws IllegalStateException
         *         if any form of data corruption is detected
         */
        private ItemPropertyContainerBox(Box box, ByteStreamReader reader) throws IOException
        {
            super(box);

            markSegment(reader.getCurrentPosition());

            do
            {
                String boxType = BoxFactory.peekBoxType(reader);

                /*
                 * Handle unknown boxes such as hvcC, app1, etc to
                 * avoid unnecessary object creation.
                 */
                if (HeifBoxType.fromTypeName(boxType) == HeifBoxType.UNKNOWN)
                {
                    Box unknownBox = new Box(reader);

                    reader.skip(unknownBox.available(reader));
                    properties.add(unknownBox);
                }

                else
                {
                    Box propertyBox = BoxFactory.createBox(reader);
                    properties.add(propertyBox);
                }

            } while (reader.getCurrentPosition() < getEndPosition());

            if (reader.getCurrentPosition() != getEndPosition())
            {
                throw new IllegalStateException("Mismatch in expected box size for [" + getFourCC() + "]");
            }

            commitSegment(reader.getCurrentPosition());
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
            List<Box> list = new ArrayList<>();

            list.addAll(properties);

            return list;
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
            String tab = Box.repeatPrint("\t", getHierarchyDepth());
            LOGGER.debug(String.format("%s%s '%s':", tab, getClass().getSimpleName(), getFourCC()));
        }
    }
}