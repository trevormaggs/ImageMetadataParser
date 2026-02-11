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
 * Parses TIFF-based files (such as standard TIFF, EXIF in JPEGs, and DNG) by reading and
 * interpreting Image File Directories (IFDs).
 *
 * <p>
 * Supports standard TIFF 6.0 parsing, including the primary {@code IFD0} and linked
 * sub-directories, such as {@code EXIF}, {@code GPS}, and {@code INTEROP}, which are traversed
 * recursively via tag-defined pointers.
 * </p>
 *
 * <p>
 * <strong>Note:</strong> While this handler detects BigTIFF (version 43), it currently only
 * supports Standard TIFF (version 42) parsing. This handler focuses on IFD structures, other
 * metadata formats, such as XMP or ICCP, should be handled by the Image Parser.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 5 September 2025
 * @see <a href="https://partners.adobe.com/public/developer/en/tiff/TIFF6.pdf">TIFF 6.0
 *      Specification</a>
 */
public class IFDHandler implements ImageHandler, AutoCloseable
{
    private static final LogFactory LOGGER = LogFactory.getLogger(IFDHandler.class);
    private static final int TIFF_STANDARD_VERSION = 42;
    private static final int TIFF_BIG_VERSION = 43;
    public static final int ENTRY_MAX_VALUE_LENGTH = 4;
    public static final int ENTRY_MAX_VALUE_LENGTH_BIG = 8;
    private static final List<Class<? extends Enum<?>>> tagClassList;
    private static final Map<Taggable, DirectoryIdentifier> subIfdMap;
    private static final Map<Integer, Taggable> TAG_LOOKUP;
    private final List<DirectoryIFD> directoryList = new ArrayList<>();
    private final ByteStreamReader reader;
    private boolean isTiffBig;

    static
    {
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
     * Constructs a handler using the specified byte stream reader.
     *
     * @param reader
     *        the stream reader providing access to TIFF content
     */
    public IFDHandler(ByteStreamReader reader)
    {
        this.reader = reader;
    }

    /**
     * Constructs a handler that reads metadata directly from a file.
     *
     * <p>
     * <strong>Note:</strong> This constructor opens the file. To prevent file locks or memory
     * leaks, use this handler within a {@code try-with-resources} block so the file resource is
     * automatically closed.
     * </p>
     *
     * @param fpath
     *        the path to the image file
     * @throws IOException
     *         if the file cannot be opened or read
     */
    public IFDHandler(Path fpath) throws IOException
    {
        this.reader = new ImageRandomAccessReader(fpath);
    }

    /**
     * Returns the list of IFD directories that were successfully parsed.
     *
     * @return an unmodifiable {@link List} of parsed {@link DirectoryIFD} structures
     */
    public List<DirectoryIFD> getDirectories()
    {
        return Collections.unmodifiableList(directoryList);
    }

    /**
     * Returns the byte order, indicating how metadata values will be interpreted correctly.
     *
     * @return either {@link java.nio.ByteOrder#BIG_ENDIAN} or
     *         {@link java.nio.ByteOrder#LITTLE_ENDIAN}
     */
    public ByteOrder getTifByteOrder()
    {
        return reader.getByteOrder();
    }

    /**
     * Indicates whether the parsed file is a BigTIFF variant (version 43).
     *
     * @return {@code true} if the file is a BigTIFF variant
     */
    public boolean isBigTiffVersion()
    {
        return isTiffBig;
    }

    /**
     * Parses the image stream and populates the directory list.
     *
     * <p>
     * This method performs a deep scan of the IFD chain. If any part of the directory structure is
     * found to be corrupt, the directory list is cleared to ensure data integrity.
     * </p>
     *
     * @return {@code true} if the IFD chain was successfully traversed and at least one directory
     *         was extracted
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
    public boolean parseMetadata() throws IOException
    {
        long firstIFDoffset = readTifHeader();

        if (firstIFDoffset > 0L)
        {
            if (!navigateImageFileDirectory(DirectoryIdentifier.IFD_DIRECTORY_IFD0, firstIFDoffset))
            {
                directoryList.clear();
                LOGGER.warn("Corrupted IFD chain detected while navigating. Directory list cleared");
            }
        }

        else
        {
            LOGGER.error("Invalid TIFF header detected. Metadata parsing cancelled");
        }

        return (!directoryList.isEmpty());
    }

    /**
     * Releases the file handle and closes the underlying stream reader.
     *
     * <p>
     * This is called automatically when using a {@code try-with-resources} block. Closing this
     * handler ensures that any system locks on the file are released and memory resources are
     * freed.
     * </p>
     *
     * @throws IOException
     *         if an I/O error occurs while closing the reader
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
     * <strong>Requirement:</strong> The stream must be positioned at the TIFF magic bytes. Any
     * preambles, such as HEIF or JPEG markers, must be skipped prior to calling this method.
     * </p>
     *
     * <p>
     * Currently supports <b>Standard TIFF</b> (16-bit), but <b>BigTIFF</b> (64-bit) is detectable
     * and un-supported. This process validates the magic bytes ({@code II} - {@code 0x49 0x49} or
     * {@code MM} - {@code 0x4D 0x4D}) and determines the offset to IFD0 based on the version
     * detected.
     * </p>
     *
     * @return the absolute offset to IFD0, or {@code 0} if the header is malformed
     *
     * @throws IOException
     *         if an I/O error occurs
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
            LOGGER.warn(String.format("Unknown byte order: [0x%02X, 0x%02X]", firstByte, secondByte));
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
     * Recursively traverses an IFD and its linked sub-directories.
     *
     * <p>
     * Iterates through entries and follows pointers to sub-IFDs, such as EXIF, GPS, or Interop. If
     * a recursive call fails due to malformed data, it returns {@code false} to prevent processing
     * a corrupt directory chain.
     * </p>
     *
     * <p>
     * Essentially, each IFD is a sequence of 12-byte entries, each containing a tag ID, a field
     * type, a count of values, and a 4-byte value or offset.
     * </p>
     *
     * @param dirType
     *        the directory type being processed
     * @param startOffset
     *        the file offset where the IFD begins
     * @return {@code true} if the directory and all linked IFDs were successfully parsed.
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    private boolean navigateImageFileDirectory2(DirectoryIdentifier dirType, long startOffset) throws IOException
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

            if (tagEnum == null)
            {
                /*
                 * In some instances where tag IDs are found to be unknown or unspecified in scope
                 * of this scanner, this will safely skip the remaining 10 bytes of 12 bytes and
                 * continue to the next iteration.
                 */
                LOGGER.warn(String.format("Unknown tag ID: 0x%04X", tagID));
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
                LOGGER.error(String.format("Skipping tag [%s]: zero count or invalid type [%s]", tagEnum, fieldType));
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
                    LOGGER.error(String.format("Offset out of bounds for [%s]. Offset [0x%04X]", tagEnum, offset));
                    continue;
                }

                if (totalBytes > Integer.MAX_VALUE)
                {
                    LOGGER.error("Value size exceeds array limit for [" + tagEnum + "]. Size [" + totalBytes + "]");
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

        /*
         * Read pointer to the next IFD in the primary chain,
         * such as the transition from IFD0 to IFD1.
         */
        long nextOffset = reader.readUnsignedInteger();

        if (nextOffset == 0x0000L)
        {
            return true;
        }

        if (nextOffset <= startOffset || nextOffset >= reader.length())
        {
            LOGGER.error(String.format("Next IFD offset [0x%04X] invalid. Malformed file", nextOffset));
            return false;
        }

        return navigateImageFileDirectory(DirectoryIdentifier.getNextDirectoryType(dirType), nextOffset);
    }

    /**
     * Recursively traverses an IFD and its linked sub-directories.
     * *
     * <p>
     * This version implements a "Parent-First" strategy: the current IFD is added to
     * the directory list before any sub-directories (like EXIF or GPS) are explored.
     * </p>
     *
     * @param dirType
     *        the directory type being processed
     * @param startOffset
     *        the file offset where the IFD begins
     * @return {@code true} if the directory and all linked IFDs were successfully parsed
     * @throws IOException
     *         if an I/O error occurs
     */
    private boolean navigateImageFileDirectory(DirectoryIdentifier dirType, long startOffset) throws IOException
    {
        if (startOffset < 0 || startOffset >= reader.length())
        {
            LOGGER.warn(String.format("Invalid offset [0x%04X] for directory [%s]", startOffset, dirType));
            return false;
        }

        reader.seek(startOffset);

        byte[] data;
        DirectoryIFD ifd = new DirectoryIFD(dirType);
        int entryCount = reader.readUnsignedShort();

        // 1. Process all 12-byte entries in this IFD first
        for (int i = 0; i < entryCount; i++)
        {
            int tagID = reader.readUnsignedShort();
            Taggable tagEnum = TAG_LOOKUP.get(tagID);

            if (tagEnum == null)
            {
                LOGGER.warn(String.format("Unknown tag ID: 0x%04X", tagID));
                reader.skip(10);
                continue;
            }

            TifFieldType fieldType = TifFieldType.getTiffType(reader.readUnsignedShort());
            long count = reader.readUnsignedInteger();
            byte[] valueBytes = reader.readBytes(4);
            long offset = ByteValueConverter.toUnsignedInteger(valueBytes, getTifByteOrder());
            long totalBytes = count * fieldType.getElementLength();

            if (totalBytes == 0L || fieldType == TifFieldType.TYPE_ERROR)
            {
                LOGGER.error(String.format("Skipping tag [%s]: zero count or invalid type [%s]", tagEnum, fieldType));
                continue;
            }

            if (totalBytes > ENTRY_MAX_VALUE_LENGTH)
            {
                if (offset < 0 || offset + totalBytes > reader.length())
                {
                    LOGGER.error(String.format("Offset out of bounds for [%s]. Offset [0x%04X]", tagEnum, offset));
                    continue;
                }
                
                data = reader.peek(offset, (int) totalBytes);
            }
            
            else
            {
                data = valueBytes;
            }

            if (TifFieldType.dataTypeinRange(fieldType.getDataType()))
            {
                ifd.add(new EntryIFD(tagEnum, fieldType, count, offset, data, getTifByteOrder()));
            }
        }

        // 2. Add current IFD to the list BEFORE recursion
        directoryList.add(ifd);
        LOGGER.debug("New directory [" + dirType + "] added");

        // 3. Capture the 'next IFD' pointer before jumping to sub-IFDs
        // This pointer is always located immediately after the last entry
        long nextOffset = reader.readUnsignedInteger();

        // 4. Traverse Sub-IFDs (EXIF, GPS, etc.)
        for (EntryIFD entry : ifd)
        {
            Taggable tag = entry.getTag();
            if (subIfdMap.containsKey(tag))
            {
                long subIfdOffset = entry.getOffset();
                
                reader.mark();
                
                if (!navigateImageFileDirectory(subIfdMap.get(tag), subIfdOffset))
                {
                    reader.reset();
                    return false;
                }
                
                reader.reset();
            }
        }

        // 5. Finally, follow the main chain (e.g. IFD0 -> IFD1)
        if (nextOffset == 0x0000L)
        {
            return true;
        }

        if (nextOffset <= startOffset || nextOffset >= reader.length())
        {
            LOGGER.error(String.format("Next IFD offset [0x%04X] invalid. Malformed file", nextOffset));
            return false;
        }

        return navigateImageFileDirectory(DirectoryIdentifier.getNextDirectoryType(dirType), nextOffset);
    }
}