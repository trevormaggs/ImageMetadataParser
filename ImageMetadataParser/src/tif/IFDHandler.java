package tif;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import common.ByteStreamReader;
import common.ByteValueConverter;
import common.ImageHandler;
import common.ImageRandomAccessReader;
import logger.LogFactory;
import tif.DirectoryIFD.EntryIFD;
import tif.tagspecs.TagExif_Interop;
import tif.tagspecs.TagIFD_Baseline;
import tif.tagspecs.TagIFD_Exif;
import tif.tagspecs.TagIFD_GPS;
import tif.tagspecs.TagIFD_Private;
import tif.tagspecs.Taggable;

/**
 * This {@code IFDHandler} parses TIFF-based files (such as standard TIFF, EXIF in JPEGs, and DNG)
 * by reading and interpreting Image File Directories (IFDs) within the file's binary structure.
 *
 * <p>
 * It supports standard TIFF 6.0 parsing, including the primary {@code IFD0} and linked
 * sub-directories like {@code EXIF}, {@code GPS}, and {@code INTEROP}, which are traversed
 * recursively via tag-defined pointers. Each parsed IFD structure is stored as a Directory object
 * within a List.
 * </p>
 *
 * <p>
 * <strong>Note:</strong> BigTIFF (version 43) is detected but not supported. In addition, the chief
 * focus of this handler is to extract and parse information in the context of Image File
 * Directories only. The Image Parser class is responsible for extracting other metadata formats
 * such as XMP and ICCP.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 5 September 2025
 * @see <a href="https://partners.adobe.com/public/developer/en/tiff/TIFF6.pdf">TIFF 6.0
 *      Specification (Adobe) for in-depth technical information</a>
 */
public class IFDHandler implements ImageHandler, AutoCloseable
{
    private static final LogFactory LOGGER = LogFactory.getLogger(IFDHandler.class);
    private static final int TIFF_STANDARD_VERSION = 42;
    private static final int TIFF_BIG_VERSION = 43;
    public static final int ENTRY_MAX_VALUE_LENGTH = 4;
    public static final int ENTRY_MAX_VALUE_LENGTH_BIG = 8; // reserved for BigTIFF
    private static final List<Class<? extends Enum<?>>> tagClassList;
    private static final Map<Taggable, DirectoryIdentifier> subIfdMap;
    private static final Map<Integer, Taggable> TAG_LOOKUP;
    private final List<DirectoryIFD> directoryList = new ArrayList<>();;
    private final ByteStreamReader reader;
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
    public IFDHandler(ByteStreamReader reader)
    {
        this.reader = reader;
    }

    /**
     * Constructs an IFD handler that reads metadata directly from the specified file.
     *
     * <p>
     * Note: This constructor opens a file-based resource. The handler should be used within a
     * try-with-resources block to ensure the file lock is released.
     * </p>
     * 
     * @param fpath
     *        the path to the image file to scan
     * 
     * @throws IOException
     *         if the file cannot be accessed or an I/O error occurs
     */
    public IFDHandler(Path fpath) throws IOException
    {
        this.reader = new ImageRandomAccessReader(fpath);
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
     * Returns the byte order, indicating how TIF metadata values will be interpreted correctly.
     *
     * @return either {@link java.nio.ByteOrder#BIG_ENDIAN} or
     *         {@link java.nio.ByteOrder#LITTLE_ENDIAN}
     */
    public ByteOrder getTifByteOrder()
    {
        return reader.getByteOrder();
    }

    /**
     * If a packet of XMP properties embedded within the IFD_XML_PACKET (0x02BC) tag is present, it
     * is read into an array of raw bytes.
     * 
     * Note, it iterates in reverse direction, applying the <b>last-one-wins</b> strategy, which is
     * common for metadata.
     * 
     * @return an {@link Optional} containing the XMP payload as an array of raw bytes, or
     *         {@link Optional#empty()} otherwise
     */
    public Optional<byte[]> getRawXmpPayload()
    {
        for (int i = directoryList.size() - 1; i >= 0; i--)
        {
            DirectoryIFD dir = directoryList.get(i);

            if (dir.hasTag(TagIFD_Baseline.IFD_XML_PACKET))
            {
                return Optional.of(dir.getRawByteArray(TagIFD_Baseline.IFD_XML_PACKET));
            }
        }

        return Optional.empty();
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
     * Parses the image data stream and attempts to extract metadata directories.
     *
     * <p>
     * After invoking this method, use {@link #getDirectories()} to retrieve the list of IFD
     * (Image File Directory) structures that were successfully parsed.
     * </p>
     *
     * @return true if at least one metadata directory was successfully extracted, otherwise false
     * @throws IOException
     */
    @Override
    public boolean parseMetadata() throws IOException
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
     * Closes the underlying ImageHandler object.
     */
    @Override
    public void close() throws IOException
    {
        if (reader != null)
        {
            reader.close();
        }
    }

    /**
     * Parses the TIFF header to identify byte order, version, and the initial IFD offset.
     * 
     * <p>
     * Supports both <b>Standard TIFF</b> (16-bit) and <b>BigTIFF</b> (64-bit). The method validates
     * the magic bytes ({@code II} - {@code 0x49 0x49} or {@code MM} - {@code 0x4D 0x4D}) and
     * determines the offset to IFD0 based on the version detected.
     * </p>
     * 
     * <p>
     * <b>Requirement:</b> The stream must be positioned at the TIFF magic bytes. Any preambles,
     * such as HEIF and JPEG markers, must be skipped prior to calling this method.
     * </p>
     *
     * @return the absolute offset to IFD0, or {@code 0} if the header is malformed
     * 
     * @throws IOException
     *         if an I/O error occurs during reading
     */
    private long readTifHeader() throws IOException
    {
        byte firstByte = reader.readByte();
        byte secondByte = reader.readByte();

        if (firstByte == 0x49 && secondByte == 0x49)
        {
            reader.setByteOrder(ByteOrder.LITTLE_ENDIAN);
            LOGGER.debug("Little-Endian Byte order (Intel) detected");
        }

        else if (firstByte == 0x4D && secondByte == 0x4D)
        {
            reader.setByteOrder(ByteOrder.BIG_ENDIAN);
            LOGGER.debug("Big-Endian Byte order (Motorola) detected");
        }

        else
        {
            LOGGER.warn("Mismatched or unknown byte order bytes [First byte: 0x" + Integer.toHexString(firstByte) + "] and [Second byte: 0x" + Integer.toHexString(secondByte) + "]");
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
     * @throws IOException
     */
    private boolean navigateImageFileDirectory(DirectoryIdentifier dirType, long startOffset) throws IOException
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
                LOGGER.error(String.format("Skipping tag [%s] due to zero byte count or unknown field type [%s]", tagEnum, fieldType));
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