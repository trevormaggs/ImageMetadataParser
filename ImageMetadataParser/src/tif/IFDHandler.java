package tif;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import common.ByteValueConverter;
import common.ImageHandler;
import common.SequentialByteReader;
import logger.LogFactory;
import tif.DirectoryIFD.EntryIFD;
import tif.tagspecs.TagExif_Interop;
import tif.tagspecs.TagIFD_Baseline;
import tif.tagspecs.TagIFD_Exif;
import tif.tagspecs.TagIFD_GPS;
import tif.tagspecs.TagIFD_Private;
import tif.tagspecs.Taggable;

/**
 * This {@code IFDHandler} parses TIFF-based files by reading and interpreting Image File
 * Directories (IFDs) within the file's binary structure.
 *
 * <p>
 * It supports standard TIFF 6.0 parsing, including IFD0, EXIF, GPS, and INTEROP directories,
 * traversed recursively via tag-defined pointers.
 * </p>
 *
 * <p>
 * <strong>Note:</strong> BigTIFF (version 43) is detected but not supported.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 5 September 2025
 * @see <a href="https://partners.adobe.com/public/developer/en/tiff/TIFF6.pdf">TIFF 6.0
 *      Specification (Adobe) for in-depth technical information</a>
 */
public class IFDHandler implements ImageHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(IFDHandler.class);
    private static final int TIFF_STANDARD_VERSION = 42;
    private static final int TIFF_BIG_VERSION = 43;
    public static final int ENTRY_MAX_VALUE_LENGTH = 4;
    public static final int ENTRY_MAX_VALUE_LENGTH_BIG = 8; // reserved for BigTIFF
    private static final List<Class<? extends Enum<?>>> tagClassList;
    private static final Map<Taggable, DirectoryIdentifier> subIfdMap;
    private static final Map<Integer, Taggable> TAG_LOOKUP;
    private final List<DirectoryIFD> directoryList;
    private final SequentialByteReader reader;
    private boolean isTiffBig;

    static
    {
        // Maps a tag (like EXIF_POINTER) to the identifier of the directory it points to
        subIfdMap = Collections.unmodifiableMap(new HashMap<Taggable, DirectoryIdentifier>()
        {
            {
                put(TagIFD_Baseline.IFD_IFDSUB_POINTER, DirectoryIdentifier.IFD_DIRECTORY_SUBIFD);
                put(TagIFD_Baseline.IFD_EXIF_POINTER, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);
                put(TagIFD_Baseline.IFD_GPS_INFO_POINTER, DirectoryIdentifier.IFD_GPS_DIRECTORY);
                put(TagIFD_Exif.EXIF_INTEROPERABILITY_POINTER, DirectoryIdentifier.EXIF_INTEROP_DIRECTORY);
            }
        });

        tagClassList = Collections.unmodifiableList(Arrays.asList(
                TagIFD_Exif.class,
                TagIFD_GPS.class,
                TagIFD_Baseline.class,
                TagExif_Interop.class,
                TagIFD_Private.class));

        Map<Integer, Taggable> map = new HashMap<>();

        for (Class<? extends Enum<?>> enumClass : tagClassList)
        {
            for (Enum<?> val : enumClass.getEnumConstants())
            {
                Taggable tag = (Taggable) val;
                map.put(tag.getNumberID(), tag);
            }
        }

        TAG_LOOKUP = Collections.unmodifiableMap(map);
    }

    /**
     * Constructs an IFD handler for reading TIFF metadata using the specified byte reader.
     *
     * @param reader
     *        the byte reader providing access to the TIFF file content
     */
    public IFDHandler(SequentialByteReader reader)
    {
        this.reader = reader;
        this.directoryList = new ArrayList<>();
    }

    /**
     * Returns the list of IFD directories that were successfully parsed.
     *
     * @return a copy of {@link List} that can be empty or contains at least one
     *         {@link DirectoryIFD} instances
     */
    public List<DirectoryIFD> getDirectories()
    {
        return Collections.unmodifiableList(directoryList);
    }

    /**
     * Indicates whether the parsed file is a BigTIFF variant (version 43).
     *
     * @return boolean true if the TIFF version is BigTIFF, otherwise false
     */
    public boolean isBigTiffVersion()
    {
        return isTiffBig;
    }

    /**
     * Returns the byte order, indicating how TIF metadata values will be interpreted correctly.
     *
     * @return either {@link ByteOrder.BIG_ENDIAN} or {@link ByteOrder.LITTLE_ENDIAN}
     */
    public ByteOrder getTifByteOrder()
    {
        return reader.getByteOrder();
    }

    /**
     * Retrieves the XMP payload embedded within the IFD_XML_PACKET (0x02BC) tag of the IFD
     * directory if present. Note, it iterates in reverse direction, applying the last-one-wins
     * strategy, which is common for metadata.
     *
     * @return an {@link Optional} containing the XMP payload as an array of raw bytes if found, or
     *         {@link Optional#empty()} if the tag cannot be found
     */
    @Override
    public Optional<byte[]> getXmpPayload()
    {
        for (int i = directoryList.size() - 1; i >= 0; i--)
        {
            DirectoryIFD dir = directoryList.get(i);

            if (dir.contains(TagIFD_Baseline.IFD_XML_PACKET))
            {
                return Optional.of(dir.getRawByteArray(TagIFD_Baseline.IFD_XML_PACKET));
            }
        }

        return Optional.empty();
    }

    /**
     * Parses the image data stream and attempts to extract metadata directories.
     *
     * <p>
     * After invoking this method, use {@link #getDirectories()} to retrieve the list of IFD
     * (Image File Directory) structures that were successfully parsed.
     * </p>
     *
     * @return true if at least one metadata directory was successfully extracted, otherwise false
     */
    @Override
    public boolean parseMetadata()
    {
        long firstIFDoffset = readTifHeader();

        if (firstIFDoffset == 0L)
        {
            LOGGER.error("Invalid TIFF header detected. Metadata parsing cancelled");
            return false;
        }

        if (!navigateImageFileDirectory(DirectoryIdentifier.IFD_DIRECTORY_IFD0, firstIFDoffset))
        {
            directoryList.clear();
            LOGGER.warn("Corrupted IFD chain detected while navigating. Directory list cleared");
        }

        return (!directoryList.isEmpty());
    }

    /**
     * Handles the first 8 bytes of the TIFF header to determine the byte order, version (Standard
     * or BigTIFF), and the offset to the first Image File Directory (IFD0).
     *
     * <p>
     * The first two bytes indicate the byte order ({@code II} for little-endian or {@code MM} for
     * big-endian). The next two bytes contain the TIFF version number (42 for standard TIFF, 43 for
     * BigTIFF). Finally, the last four bytes of the 8-byte header specify the offset to the first
     * IFD.
     * </p>
     *
     * @return the offset to the first IFD0 directory, otherwise zero if the header is invalid
     */
    private long readTifHeader()
    {
        byte firstByte = reader.readByte();
        byte secondByte = reader.readByte();

        if (firstByte == 0x49 && secondByte == 0x49)
        {
            reader.setByteOrder(ByteOrder.LITTLE_ENDIAN);
            LOGGER.debug("Byte order detected as [Intel (Little-Endian)]");
        }

        else if (firstByte == 0x4D && secondByte == 0x4D)
        {
            reader.setByteOrder(ByteOrder.BIG_ENDIAN);
            LOGGER.debug("Byte order detected as [Motorola (Big-Endian)]");
        }

        else
        {
            LOGGER.warn("Mismatched or unknown byte order bytes [First byte: 0x" + Integer.toHexString(firstByte) + " ] and [Second byte: 0x" + Integer.toHexString(secondByte) + "]");
            return 0L;
        }

        /* Identify whether this is Standard TIFF (42) or Big TIFF (43) version */
        int tiffVer = reader.readUnsignedShort();
        isTiffBig = (tiffVer == TIFF_BIG_VERSION);

        if (isTiffBig)
        {
            /* TODO - develop and expand to support Big Tiff */
            LOGGER.warn("BigTIFF (version 43) not supported yet");
            return 0L;
        }

        else if (tiffVer != TIFF_STANDARD_VERSION)
        {
            LOGGER.warn("Unexpected TIFF version [" + tiffVer + "], defaulting to standard TIFF 6.0");
        }

        /* Advance by offset from base to IFD0 */
        return reader.readUnsignedInteger();
    }

    /**
     * Recursively traverses the specified Image File Directory and its linked sub-directories. An
     * IFD is a sequence of 12-byte entries, each containing a tag ID, a field type, a count of
     * values, and a 4-byte value or offset. This method iterates through these entries, reads the
     * corresponding data, and if an entry points to another IFD (like EXIF, GPS, or Interop), it
     * recursively calls itself to parse that sub-directory.
     *
     * <p>
     * <b>Important note</b>, if any recursive call failed due to malformed entries, it will return
     * false to indicate the potentially corrupt partial list must be cleared to prevent a
     * downstream logic issue.
     * </p>
     *
     * @param dirType
     *        the directory type being processed
     * @param startOffset
     *        the file offset (from header base) where the IFD begins
     * @return true if the directory and all subsequent linked IFDs were successfully parsed,
     *         otherwise false
     */
    private boolean navigateImageFileDirectory(DirectoryIdentifier dirType, long startOffset)
    {
        if (startOffset < 0 || startOffset >= reader.length())
        {
            LOGGER.warn(String.format("Invalid offset [0x%04X] for directory [%s]", startOffset, dirType));
            return false;
        }

        reader.seek(startOffset);

        DirectoryIFD ifd = new DirectoryIFD(dirType);
        int entryCount = reader.readUnsignedShort();

        for (int i = 0; i < entryCount; i++)
        {
            int tagID = reader.readUnsignedShort();
            Taggable tagEnum = TAG_LOOKUP.get(tagID);

            /*
             * In some instances where tag IDs are found to be unknown or unspecified
             * in scope of this scanner, this will safely skip the whole segment and
             * continue to the next iteration. 12-byte entry in total (4 bytes for ID/Type, 4
             * for Count, 4 for Value/Offset)
             */
            if (tagEnum == null)
            {
                LOGGER.warn("Unknown tag ID: 0x" + Integer.toHexString(tagID));
                reader.skip(10);
                continue;
            }

            byte[] data;
            TifFieldType fieldType = TifFieldType.getTiffType(reader.readUnsignedShort());
            long count = reader.readUnsignedInteger();
            byte[] valueBytes = reader.readBytes(4);
            long offset = ByteValueConverter.toUnsignedInteger(valueBytes, getTifByteOrder());
            long totalBytes = count * fieldType.getElementLength();

            if (totalBytes == 0L || fieldType == TifFieldType.TYPE_ERROR)
            {
                LOGGER.warn(String.format("Skipping tag [%s] due to zero byte count or unknown field type [%s]", tagEnum, fieldType));
                continue;
            }

            /*
             * A length of the value that is larger than 4 bytes indicates
             * the entry is an offset outside this directory field.
             */
            if (totalBytes > ENTRY_MAX_VALUE_LENGTH)
            {
                if (offset < 0 || offset + totalBytes > reader.length())
                {
                    LOGGER.error(String.format("Offset out of bounds for tag [%s]. Offset [0x%04X]. Size [%d]. File Length [%d]", tagEnum, offset, totalBytes, reader.length()));
                    continue;
                }

                // Check for potential array allocation overflow
                if (totalBytes > Integer.MAX_VALUE)
                {
                    LOGGER.error(String.format("Value size exceeds Java array limit for tag [%s]. Size [%d]", tagEnum, totalBytes));
                    continue;
                }

                data = reader.peek(offset, (int) totalBytes);
            }

            else
            {
                data = valueBytes;
            }

            /* Make sure the tag ID is known and defined in TIF Specification 6.0 */
            if (TifFieldType.dataTypeinRange(fieldType.getDataType()))
            {
                ifd.add(new EntryIFD(tagEnum, fieldType, count, offset, data, getTifByteOrder()));
            }

            else
            {
                LOGGER.warn("Unknown field type [" + fieldType + "] for tag [" + tagEnum + "]");
                continue;
            }

            // Sub-IFD check (EXIF, GPS, etc)
            if (subIfdMap.containsKey(tagEnum))
            {
                reader.mark();

                if (!navigateImageFileDirectory(subIfdMap.get(tagEnum), offset))
                {
                    reader.reset();
                    return false;
                }

                reader.reset();
            }
        }

        directoryList.add(ifd);
        LOGGER.debug("New directory [" + dirType + "] added");

        // Read pointer to the next IFD in the primary chain (i.e. IFD0 -> IFD1)
        long nextOffset = reader.readUnsignedInteger();

        if (nextOffset == 0x0000L)
        {
            return true;
        }

        if (nextOffset <= startOffset || nextOffset >= reader.length())
        {
            LOGGER.error(String.format("Next IFD offset [0x%04X] points to an invalid location [Start: 0x%04X]. Malformed file", nextOffset, startOffset));
            return false;
        }

        return navigateImageFileDirectory(DirectoryIdentifier.getNextDirectoryType(dirType), nextOffset);
    }
}