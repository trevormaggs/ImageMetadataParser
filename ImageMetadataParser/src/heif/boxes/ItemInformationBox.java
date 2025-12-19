package heif.boxes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import common.ByteValueConverter;
import common.ByteStreamReader;
import logger.LogFactory;

/**
 * Represents the {@code iinf} (Item Information Box), which describes items within the HEIF file.
 * This is often used to locate EXIF metadata, thumbnails, or other auxiliary images.
 *
 * <p>
 * Specification Reference: ISO/IEC 14496-12:2015, Pages 81–83.
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
public class ItemInformationBox extends FullBox
{
    private static final LogFactory LOGGER = LogFactory.getLogger(ItemInformationBox.class);
    private static final String TYPE_URI = "uri ";
    private static final String TYPE_MIME = "mime";
    private static final String TYPE_EXIF = "Exif";
    private final long entryCount;
    private final List<ItemInfoEntry> entries;

    /**
     * Parses the {@code ItemInformationBox} from the specified reader.
     *
     * @param box
     *        the parent box header
     * @param reader
     *        the sequential byte reader for HEIF content
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public ItemInformationBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box, reader);

        List<ItemInfoEntry> tmpEntries = new ArrayList<>();
        long pos = reader.getCurrentPosition();

        this.entryCount = (getVersion() == 0) ? reader.readUnsignedShort() : reader.readUnsignedInteger();

        for (int i = 0; i < entryCount; i++)
        {
            tmpEntries.add(new ItemInfoEntry(new Box(reader), reader));
        }

        this.entries = Collections.unmodifiableList(tmpEntries);

        byteUsed += reader.getCurrentPosition() - pos;
    }

    /**
     * Returns the list of all {@link ItemInfoEntry} entries in this box.
     *
     * @return an unmodifiable list of {@code ItemInfoEntry}
     */
    public List<ItemInfoEntry> getEntries()
    {
        return entries;
    }

    /**
     * Checks whether this {@code ItemInformationBox} contains an EXIF metadata reference.
     *
     * @return boolean true if an EXIF reference exists, otherwise false
     */
    public boolean containsExif()
    {
        for (ItemInfoEntry infe : entries)
        {
            if (infe.isExif())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks whether this {@code ItemInformationBox} contains an XMP metadata reference.
     *
     * @return true if an XMP reference exists, otherwise false
     */
    public boolean containsXmp()
    {
        return findXmpItemID() != -1;
    }

    /**
     * Retrieves the Item ID associated with the EXIF metadata entry.
     *
     * @return the EXIF Item ID if present, otherwise -1
     */
    public int findExifItemID()
    {
        for (ItemInfoEntry infe : entries)
        {
            if (infe.getItemType() != null && infe.isExif())
            {
                return infe.getItemID();
            }
        }

        return -1;
    }

    /**
     * Retrieves the Item ID associated with the XMP metadata entry.
     */
    public int findXmpItemID()
    {
        for (ItemInfoEntry infe : entries)
        {
            if (TYPE_MIME.equals(infe.getItemType()) && "application/rdf+xml".equalsIgnoreCase(infe.getContentType()))
            {
                return infe.getItemID();
            }
        }

        return -1;
    }

    /**
     * Retrieves the {@link ItemInfoEntry} matching the given {@code itemID}.
     *
     * @param itemID
     *        the item ID to search for
     *
     * @return an Optional containing the matching entry if found, otherwise Optional.empty() is
     *         returned
     */
    public Optional<ItemInfoEntry> getEntry(int itemID)
    {
        for (ItemInfoEntry infe : entries)
        {
            if (infe.itemID == itemID)
            {
                return Optional.ofNullable(infe);
            }
        }

        return Optional.empty();
    }

    /**
     * Returns the item type for a specific item ID.
     * 
     * @param itemID
     *        the ID to look up
     * @return the 4-character type (i.e. "Exif", "mime") or an empty string if not found
     */
    public String getItemType(int itemID)
    {
        return getEntry(itemID).map(ItemInfoEntry::getItemType).orElse("");
    }

    /**
     * Returns a combined list of all boxes contained in this {@code ItemInformationBox}, including
     * the ItemInfoEntry boxes ({@code infe}).
     *
     * @return a combined list of Box objects in reading order
     */
    @Override
    public List<Box> getBoxList()
    {
        List<Box> combinedList = new ArrayList<>();

        combinedList.addAll(entries);

        return combinedList;
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
        LOGGER.debug(String.format("%s%s '%s':\tItem_count=%d", tab, this.getClass().getSimpleName(), getTypeAsString(), entryCount));
    }

    /**
     * Represents an {@code infe} (Item Info Entry) box inside an {@code iinf} box.
     */
    public static class ItemInfoEntry extends FullBox
    {
        private final int itemID;
        private final int itemProtectionIndex;
        private final String itemType;
        private final String itemName;
        private final String contentType;
        private final String contentEncoding;
        private final String itemUriType;
        private final long extensionType;

        /**
         * Parses an {@code ItemInfoEntry} from the specified reader.
         *
         * @param box
         *        the parent box header
         * @param reader
         *        the byte reader for entry content
         * 
         * @throws IOException
         *         if an I/O error occurs
         */
        public ItemInfoEntry(Box box, ByteStreamReader reader) throws IOException
        {
            super(box, reader);

            byte[] payload = reader.readBytes((int) available());
            String[] items;
            int version = getVersion();

            String type = null;
            String name = null;
            String cType = null;
            String encoding = null;
            String uri = null;
            long extType = -1L;

            if (version == 0 || version == 1)
            {
                this.itemID = ByteValueConverter.toUnsignedShort(payload, 0, box.getByteOrder());
                this.itemProtectionIndex = ByteValueConverter.toUnsignedShort(payload, 2, box.getByteOrder());

                // Extract strings for fields (Name, CType, Encoding)
                items = ByteValueConverter.splitNullDelimitedStrings(Arrays.copyOfRange(payload, 4, payload.length));

                if (items.length > 0)
                {
                    name = items[0];
                    cType = items.length > 1 ? items[1] : null;
                    encoding = items.length > 2 ? items[2] : null;
                }

                if (version == 1)
                {
                    /*
                     * According to the spec, extension_type comes after name, cType, AND encoding
                     * even if those strings are empty (0x00). There are 3 null terminators, so find
                     * the offset of the byte immediately following the third and last null.
                     */
                    int nullCount = 0;
                    int binaryOffset = -1;

                    for (int i = 4; i < payload.length; i++)
                    {
                        if (payload[i] == 0)
                        {
                            nullCount++;

                            if (nullCount == 3)
                            {
                                binaryOffset = i + 1;

                                if (payload.length >= binaryOffset + 4)
                                {
                                    extType = ByteValueConverter.toUnsignedInteger(payload, binaryOffset, box.getByteOrder());
                                }

                                break;
                            }
                        }
                    }
                }
            }

            else
            {
                int index = (version == 2) ? 2 : 4;
                this.itemID = (version == 2 ? ByteValueConverter.toUnsignedShort(payload, 0, box.getByteOrder()) : ByteValueConverter.toInteger(payload, 0, box.getByteOrder()));

                this.itemProtectionIndex = ByteValueConverter.toUnsignedShort(payload, index, box.getByteOrder());
                index += 2;

                type = new String(Arrays.copyOfRange(payload, index, index + 4), StandardCharsets.UTF_8);
                index += 4;

                items = ByteValueConverter.splitNullDelimitedStrings(Arrays.copyOfRange(payload, index, payload.length));

                if (items.length > 0)
                {
                    name = items[0];

                    if (TYPE_MIME.equals(type))
                    {
                        cType = items.length > 1 ? items[1] : null;
                        encoding = items.length > 2 ? items[2] : null;

                        // System.out.printf("DEBUG: Type='%s', Name='%s', ContentType='%s'\n",
                        // type, name, cType);
                    }

                    else if (TYPE_URI.equals(type))
                    {
                        uri = items.length > 1 ? items[1] : null;
                    }
                }
            }

            this.itemType = type;
            this.itemName = name;
            this.contentType = cType;
            this.contentEncoding = encoding;
            this.itemUriType = uri;
            this.extensionType = extType;
        }

        /**
         * Returns the Item ID.
         *
         * @return the item ID
         */
        public int getItemID()
        {
            return itemID;
        }

        /**
         * Indicates if this entry refers to EXIF data.
         *
         * @return boolean true if this is an EXIF reference, otherwise false
         */
        public boolean isExif()
        {
            // Check the explicit type field (Version 2+)
            if (TYPE_EXIF.equalsIgnoreCase(getItemType()))
            {
                return true;
            }
            // Fallback for older Version 0/1 files where "Exif" might be in the Name
            return getItemName().equalsIgnoreCase(TYPE_EXIF);
        }

        /**
         * Returns the protection index of this item.
         *
         * @return the item protection index (0 if unprotected)
         */
        public int getItemProtectionIndex()
        {
            return itemProtectionIndex;
        }

        /**
         * Returns the item type as a 4-character code.
         *
         * @return the item type if present
         */
        public String getItemType()
        {
            return (itemType == null ? "" : itemType);
        }

        /**
         * Returns the item name.
         *
         * @return the item name if present
         */
        public String getItemName()
        {
            return (itemName == null ? "" : itemName);
        }

        /**
         * Returns the content type for MIME entries.
         *
         * @return the content type if present
         */
        public String getContentType()
        {
            return (contentType == null ? "" : contentType);
        }

        /**
         * Returns the URI type for URI entries.
         *
         * @return the URI type if present
         */
        public String getItemUriType()
        {
            return (itemUriType == null ? "" : itemUriType);
        }

        /**
         * Returns the content encoding for MIME entries.
         *
         * @return the encoding if present
         */
        public String getContentEncoding()
        {
            return (contentEncoding == null ? "" : contentEncoding);
        }

        /**
         * Returns the extension type for version 1 entries.
         *
         * @return the extension type if present, otherwise -1
         */
        public long getExtensionType()
        {
            return extensionType;
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
            LOGGER.debug(String.format("%s%d)\t'%s': item_ID=%d,\titem_type='%s'", tab, getItemID(), getTypeAsString(), getItemID(), getItemType()));
        }
    }
}