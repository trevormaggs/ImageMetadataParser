package heif.boxes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import common.ByteStreamReader;
import heif.BoxFactory;
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

        markSegment(reader.getCurrentPosition());

        List<ItemInfoEntry> tmpEntries = new ArrayList<>();
        this.entryCount = (getVersion() == 0) ? reader.readUnsignedShort() : reader.readUnsignedInteger();

        for (int i = 0; i < entryCount; i++)
        {
            // tmpEntries.add(new ItemInfoEntry(new Box(reader), reader));
        }

        for (int i = 0; i < entryCount; i++)
        {
            Box child = BoxFactory.createBox(reader);

            if (child instanceof ItemInfoEntry)
            {
                tmpEntries.add((ItemInfoEntry) child);
            }

            else
            {
                LOGGER.warn("Expected [infe] box but found [" + child.getTypeAsString() + "]");
            }
        }

        this.entries = Collections.unmodifiableList(tmpEntries);

        commitSegment(reader.getCurrentPosition());
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
            if (ItemInfoEntry.TYPE_MIME.equals(infe.getItemType()) && "application/rdf+xml".equalsIgnoreCase(infe.getContentType()))
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
        LOGGER.debug(String.format("%s%s '%s':\tItem_count=%d", tab, this.getClass().getSimpleName(), getTypeAsString(), entryCount));
    }}