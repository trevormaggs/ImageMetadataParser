package heif.boxes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import common.ByteStreamReader;
import common.Utils;
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
    private final List<ItemLocationEntry> items = new ArrayList<>();

    // This is needed to support HeifPropertyInjector for testing
    private final int offsetSize;
    private final int lengthSize;
    private final int baseOffsetSize;

    /**
     * Constructs an {@code ItemLocationBox} by parsing the provided {@code iloc} box data.
     *
     * @param box
     *        the parent {@code Box} containing common box values
     * @param reader
     *        the stream resource using {@code ByteStreamReader} to enable byte parsing
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    public ItemLocationBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box, reader);

        int tmp;
        int indexSize;
        int itemCount;
        int constructionMethod;

        tmp = reader.readUnsignedByte();
        this.offsetSize = (tmp & 0xF0) >> 4;
        this.lengthSize = (tmp & 0x0F);

        tmp = reader.readUnsignedByte();
        this.baseOffsetSize = (tmp & 0xF0) >> 4;
        indexSize = (getVersion() > 0 ? (tmp & 0x0F) : 0);

        itemCount = (getVersion() < 2 ? reader.readUnsignedShort() : (int) reader.readUnsignedInteger());

        for (int i = 0; i < itemCount; i++)
        {
            final int itemID = (getVersion() < 2) ? reader.readUnsignedShort() : (int) reader.readUnsignedInteger();

            constructionMethod = 0;

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

                long fieldPos = reader.getCurrentPosition();
                long extentOffset = readSizedValue(offsetSize, reader);
                long extentLength = readSizedValue(lengthSize, reader);

                extents.add(new ExtentData(extentIndex, extentOffset, extentLength, baseOffset, fieldPos));
            }

            items.add(new ItemLocationEntry(itemID, constructionMethod, dataReferenceIndex, extents));
        }
    }

    /**
     * Returns the size in bytes of the base offset field. Usually from the set {4, 8}.
     * 
     * @return the size in bytes of the base offset field
     */
    public int getBaseOffsetSize()
    {
        return baseOffsetSize;
    }

    /**
     * Returns the size in bytes of the offset field. Usually from the set {4, 8}. Note, this is
     * needed to support HeifPropertyInjector for testing.
     * 
     * @return the size in bytes of the offset field
     */
    public int getOffsetSize()
    {
        return offsetSize;
    }

    /**
     * Returns the size in bytes of the length field. Usually from the set {0, 4, 8}.
     * 
     * @return the size of the length field
     */
    public int getLengthSize()
    {
        return lengthSize;
    }

    /**
     * Finds the location entry corresponding to the specified {@code itemID}.
     *
     * @param itemID
     *        the item identifier to search for
     * @return the matching ItemLocationEntry object, or null if not found
     */
    public ItemLocationEntry findItem(int itemID)
    {
        for (ItemLocationEntry entry : items)
        {
            if (entry.getItemID() == itemID)
            {
                return entry;
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
     * @return a of extents for the item, or empty list if none found
     */
    public List<ExtentData> getExtents(int itemID)
    {
        ItemLocationEntry item = findItem(itemID);

        return (item == null ? Collections.emptyList() : Collections.unmodifiableList(item.getExtents()));
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
        LOGGER.debug(String.format("%s%s '%s':\t\titemCount=%d", tab, this.getClass().getSimpleName(), getFourCC(), items.size()));

        for (ItemLocationEntry item : items)
        {
            LOGGER.debug(String.format("\t\tItemID=%-4d constructionMethod=%-5d dataRefIdx=%-8d", item.getItemID(), item.getConstructionMethod(), item.getDataReferenceIndex()));

            for (ExtentData extent : item.getExtents())
            {
                LOGGER.debug(String.format("\t\t\t\t\tbaseOffset=0x%X extentIndex=%-3d extentOffset=0x%08X  extentLength=%d", extent.getBaseOffset(), extent.getExtentIndex(), extent.getExtentOffset(), extent.getExtentLength()));
            }
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
        private final List<ExtentData> extents;

        public ItemLocationEntry(int itemID, int constructionMethod, int dataReferenceIndex, List<ExtentData> extents)
        {
            this.itemID = itemID;
            this.constructionMethod = constructionMethod;
            this.dataReferenceIndex = dataReferenceIndex;
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
        private final int extentIndex;
        private final long extentOffset;
        private final long extentLength;
        private final long baseOffset;
        private final long offsetFieldFilePosition;

        public ExtentData(int extentIndex, long extentOffset, long extentLength, long baseOffset, long fieldPos)
        {
            this.extentIndex = extentIndex;
            this.extentOffset = extentOffset;
            this.extentLength = extentLength;
            this.baseOffset = baseOffset;
            this.offsetFieldFilePosition = fieldPos;
        }

        /**
         * Returns the base offset for this fragmented extent data block.
         */
        public long getBaseOffset()
        {
            return baseOffset;
        }

        /**
         * Returns the extent index to identify this fragmented extent data block.
         */
        public int getExtentIndex()
        {
            return extentIndex;
        }

        /**
         * Returns the extent offset for this fragmented extent data block.
         */
        public long getExtentOffset()
        {
            return extentOffset;
        }

        /**
         * Returns the length of this fragmented extent data block.
         */
        public long getExtentLength()
        {
            return extentLength;
        }

        /**
         * Calculates the absolute offset of this extent within the file. This absolute Offset is
         * the sum of Base Offset and Extent Offset.
         */
        public long getAbsoluteOffset()
        {
            return baseOffset + extentOffset;
        }

        /**
         * Returns the actual offset representing the file stream position. This is necessary to
         * support HeifPropertyInjector for testing purposes.
         */
        public long getOffsetFieldFilePosition()
        {
            return offsetFieldFilePosition;
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
     * @param bytesize
     *        the number of bytes to read: {0, 4, 8}
     * @param reader
     *        the {@code ByteStreamReader} object needed for reading the value
     * @return the parsed value as an unsigned {@code long}
     * 
     * @throws IOException
     *         if an I/O error occurs
     * @throws IllegalArgumentException
     *         if input is not one of {0, 4, 8}
     */
    private long readSizedValue(int bytesize, ByteStreamReader reader) throws IOException
    {
        switch (bytesize)
        {
            case 0:
                return 0L;
            case 1:
                return reader.readUnsignedByte();// Backward compatibility
            case 2:
                return reader.readUnsignedShort(); // Backward compatibility
            case 4:
                return reader.readUnsignedInteger();
            case 8:
                return reader.readLong();
            default:
                throw new IllegalArgumentException("Invalid input size [" + bytesize + "]");
        }
    }
}