package heif.boxes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import common.ByteStreamReader;
import logger.LogFactory;

/**
 * The {@code ItemLocationBox} class handles the HEIF Box identified as {@code iloc} (Item Location
 * Box).
 *
 * <p>
 * This box provides a directory of item resources, either in the same file or in external files.
 * Each entry describes the item's container, offset within that container, and length.
 * </p>
 *
 * <p>
 * For technical details, refer to the specification document: {@code ISO/IEC 14496-12:2015}, pages
 * 77–80.
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
public class ItemLocationBox extends FullBox
{
    private static final LogFactory LOGGER = LogFactory.getLogger(ItemLocationBox.class);
    private final int offsetSize;
    private final int lengthSize;
    private final int baseOffsetSize;
    private final int indexSize;
    private final int itemCount;
    private final List<ItemLocationEntry> items;

    /**
     * Constructs an {@code ItemLocationBox} by parsing the provided {@code iloc} box data.
     *
     * @param box
     *        the parent {@code Box} containing common box values
     * @param reader
     *        a {@code ByteStreamReader} for sequential access to the box content
     *
     * @throws IOException
     *         if an I/O error occurs
     * @throws UnsupportedOperationException
     *         if external data references (dataReferenceIndex != 0) are found
     */
    public ItemLocationBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box, reader);

        items = new ArrayList<>();
        long pos = reader.getCurrentPosition();

        int tmp = reader.readUnsignedByte();
        offsetSize = (tmp & 0xF0) >> 4;
        lengthSize = (tmp & 0x0F);

        tmp = reader.readUnsignedByte();
        baseOffsetSize = (tmp & 0xF0) >> 4;
        indexSize = (getVersion() > 0 ? (tmp & 0x0F) : 0);

        itemCount = (getVersion() < 2 ? reader.readUnsignedShort() : (int) reader.readUnsignedInteger());

        for (int i = 0; i < itemCount; i++)
        {
            final int itemID = (getVersion() < 2) ? reader.readUnsignedShort() : (int) reader.readUnsignedInteger();

            int constructionMethod = 0;

            if (getVersion() > 0)
            {
                constructionMethod = reader.readUnsignedShort() & 0x000F;
            }

            int dataReferenceIndex = reader.readUnsignedShort();
            long baseOffset = readSizedValue(baseOffsetSize, reader);
            int extentCount = reader.readUnsignedShort();

            if (dataReferenceIndex != 0)
            {
                reader.skip(extentCount * (indexSize + offsetSize + lengthSize));
                LOGGER.warn("Item [" + itemID + "] uses external data reference (dref idx [" + dataReferenceIndex + "]. Skipping item");

                continue;
            }

            List<ExtentData> extents = new ArrayList<>(extentCount);

            for (int j = 0; j < extentCount; j++)
            {
                int extentIndex = 0;

                if (getVersion() > 0 && indexSize > 0)
                {
                    extentIndex = (int) readSizedValue(indexSize, reader);
                }

                long extentOffset = readSizedValue(offsetSize, reader);
                int extentLength = (int) readSizedValue(lengthSize, reader);

                extents.add(new ExtentData(itemID, extentIndex, extentOffset, extentLength));
            }

            items.add(new ItemLocationEntry(itemID, constructionMethod, dataReferenceIndex, baseOffset, extents));
        }

        byteUsed += reader.getCurrentPosition() - pos;
    }

    /**
     * Finds the item with the specified {@code itemID}.
     *
     * @param itemID
     *        the item identifier to search for
     *
     * @return the matching {@code ItemLocationEntry}, or {@code null} if not found
     */
    public ItemLocationEntry findItem(int itemID)
    {
        for (ItemLocationEntry item : items)
        {
            if (item.getItemID() == itemID)
            {
                return item;
            }
        }

        return null;
    }

    /**
     * Finds all extents associated with the specified {@code itemID}.
     *
     * @param itemID
     *        the item identifier to search for
     *
     * @return an unmodifiable list of extents for the item, or empty list if none found
     */
    public List<ExtentData> findExtentsForItem(int itemID)
    {
        ItemLocationEntry item = findItem(itemID);

        return item == null ? Collections.emptyList() : Collections.unmodifiableList(item.getExtents());
    }

    /**
     * Returns the list of all items.
     *
     * @return an unmodifiable list of items
     */
    public List<ItemLocationEntry> getItems()
    {
        return Collections.unmodifiableList(items);
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
        LOGGER.debug(String.format("%s%s '%s':\titemCount=%d", tab, this.getClass().getSimpleName(), getTypeAsString(), itemCount));

        for (ItemLocationEntry item : items)
        {
            LOGGER.debug(String.format("\t\tItemID=%-10d constructionMethod=%-5d dataRefIdx=%-8d baseOffset=0x%X", item.getItemID(), item.getConstructionMethod(), item.getDataReferenceIndex(), item.getBaseOffset()));

            for (ExtentData extent : item.getExtents())
            {
                LOGGER.debug(String.format("\t\textentIndex=%-5d extentOffset=0x%08X  extentLength=%d", extent.getExtentIndex(), extent.getExtentOffset(), extent.getExtentLength()));
            }
        }
    }

    /**
     * Reads a value from the stream based on the specified size indicator.
     *
     * <ul>
     * <li>{@code 0} – value is always zero (no bytes read)</li>
     * <li>{@code 4} – reads a 4-byte unsigned integer</li>
     * <li>{@code 8} – reads an 8-byte unsigned integer</li>
     * </ul>
     * 
     * <p>
     * Important note: the data read follows the Big-Endian format
     * </p>
     *
     * @param input
     *        the number of bytes to read: {0, 4, 8}
     * @param reader
     *        a {@code ByteStreamReader} for reading the value
     *
     * @return the parsed value as an unsigned {@code long}
     * 
     * @throws IOException
     *         if an I/O error occurs
     * @throws IllegalArgumentException
     *         if {@code input} is not one of {0, 4, 8}
     */
    private long readSizedValue(int input, ByteStreamReader reader) throws IOException
    {
        switch (input)
        {
            case 0:
                return 0L;
            case 1: // Not standard
                return reader.readUnsignedByte();
            case 2:// Not standard
                return reader.readUnsignedShort();
            case 4:
                return reader.readUnsignedInteger();
            case 8:
                return reader.readLong();
            default:
                throw new IllegalArgumentException("Invalid input size: " + input);
        }
    }

    /**
     * Represents one item entry, holding multiple extents.
     */
    public static class ItemLocationEntry
    {
        private final int itemID;
        private final int constructionMethod;
        private final int dataReferenceIndex;
        private final long baseOffset;
        private final List<ExtentData> extents;

        public ItemLocationEntry(int itemID, int constructionMethod, int dataReferenceIndex, long baseOffset, List<ExtentData> extents)
        {
            this.itemID = itemID;
            this.constructionMethod = constructionMethod;
            this.dataReferenceIndex = dataReferenceIndex;
            this.baseOffset = baseOffset;
            this.extents = extents;
        }

        public int getItemID()
        {
            return itemID;
        }

        public int getConstructionMethod()
        {
            return constructionMethod;
        }

        public int getDataReferenceIndex()
        {
            return dataReferenceIndex;
        }

        public long getBaseOffset()
        {
            return baseOffset;
        }

        public List<ExtentData> getExtents()
        {
            return extents;
        }
    }

    /**
     * Represents a single extent entry in the {@code ItemLocationBox}.
     */
    public static class ExtentData
    {
        private final int itemID;
        private final int extentIndex;
        private final long extentOffset;
        private final int extentLength;

        public ExtentData(int itemID, int extentIndex, long extentOffset, int extentLength)
        {
            this.itemID = itemID;
            this.extentIndex = extentIndex;
            this.extentOffset = extentOffset;
            this.extentLength = extentLength;
        }

        public int getItemID()
        {
            return itemID;
        }

        public int getExtentIndex()
        {
            return extentIndex;
        }

        public long getExtentOffset()
        {
            return extentOffset;
        }

        public int getExtentLength()
        {
            return extentLength;
        }
    }
}