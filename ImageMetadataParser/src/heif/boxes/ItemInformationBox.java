package heif.boxes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import common.ByteStreamReader;
import heif.BoxFactory;
import heif.HeifBoxType;
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
    private final List<ItemInfoEntry> entries = new ArrayList<>();

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

        markSegment(reader.getCurrentPosition());

        long entryCount = (getVersion() == 0) ? reader.readUnsignedShort() : reader.readUnsignedInteger();

        for (int i = 0; i < entryCount; i++)
        {
            Box childBox = BoxFactory.createBox(reader);

            if (childBox == null)
            {
                continue;
            }

            else if (childBox.getHeifType() == HeifBoxType.ITEM_INFO_ENTRY)
            {
                entries.add((ItemInfoEntry) childBox);
            }

            else
            {
                LOGGER.warn("Expected [infe] box but found [" + childBox.getTypeAsString() + "]");
            }
        }

        if (entryCount > 0 && entries.isEmpty())
        {
            LOGGER.error("Parsed [" + entryCount + "] entries, but none were found as ItemInfoEntry. Check BoxFactory mapping for [infe]");
        }

        commitSegment(reader.getCurrentPosition());
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
            if (infe.getItemID() == itemID)
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
     * Finds the first entry matching a specific item type string.
     *
     * @param type
     *        the type of item entry to find
     * @return an ItemInfoEntry entry if found, otherwise null
     */
    public ItemInfoEntry findEntryByType(String type)
    {
        if (type != null)
        {
            for (ItemInfoEntry entry : entries)
            {
                if (type.equals(entry.getItemType()))
                {
                    return entry;
                }
            }
        }

        return null;
    }

    /**
     * Returns the list of {@link ItemInfoEntry} entries defined as ({@code infe}) contained in this
     * box.
     *
     * @return an unmodifiable list of {@code ItemInfoEntry} objects in reading order
     */
    @Override
    public List<Box> getBoxList()
    {
        return new ArrayList<>(entries);
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
        LOGGER.debug(String.format("%s%s '%s':\t\tItem_count=%d", tab, this.getClass().getSimpleName(), getTypeAsString(), entries.size()));
    }
}