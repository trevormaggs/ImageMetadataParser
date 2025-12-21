package heif.boxes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import common.ByteStreamReader;
import common.ByteValueConverter;
import logger.LogFactory;

/**
 * Represents an {@code infe} (Item Info Entry) box inside an {@code iinf} box.
 */
public class ItemInfoEntry extends FullBox
{
    private static final LogFactory LOGGER = LogFactory.getLogger(ItemInfoEntry.class);
    public static final String TYPE_URI = "uri ";
    public static final String TYPE_MIME = "mime";
    public static final String TYPE_EXIF = "Exif";
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

        String[] items;
        int version = getVersion();
        byte[] payload = reader.readBytes((int) available());

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

                        // 3 null terminators expected
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
        
        setExitBytePosition(reader.getCurrentPosition());
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
        LOGGER.debug(String.format("%s%d)\t'%s': item_ID=%d,\titem_type='%s'", tab, getItemID(), getTypeAsString(), getItemID(), getItemType()));
    }
}
