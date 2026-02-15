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
import tif.tagspecs.TagIFD_Unknown;
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
    private static final Map<Integer, Taggable> TAG_LOOKUP;
    private final List<DirectoryIFD> directoryList = new ArrayList<>();
    private static final Map<Taggable, DirectoryIdentifier> subIfdMap = new HashMap<>();
    private final ByteStreamReader reader;
    private boolean isTiffBig;

    static
    {
        subIfdMap.put(TagIFD_Baseline.IFD_IFDSUB_POINTER, DirectoryIdentifier.IFD_DIRECTORY_SUBIFD);
        subIfdMap.put(TagIFD_Baseline.IFD_EXIF_POINTER, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);
        subIfdMap.put(TagIFD_Baseline.IFD_GPS_INFO_POINTER, DirectoryIdentifier.IFD_GPS_DIRECTORY);
        subIfdMap.put(TagIFD_Exif.EXIF_INTEROPERABILITY_POINTER, DirectoryIdentifier.EXIF_INTEROP_DIRECTORY);

        tagClassList = Arrays.asList(
                TagIFD_Baseline.class,
                TagIFD_Exif.class,
                TagIFD_GPS.class,
                TagExif_Interop.class,
                TagIFD_Private.class);

        Map<Integer, Taggable> map = new HashMap<>();

        for (Class<? extends Enum<?>> enumClass : tagClassList)
        {
            for (Enum<?> val : enumClass.getEnumConstants())
            {
                Taggable tag = (Taggable) val;

                // TODO: Fix potential collision ID issues across directories
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
     * Parses the image stream and populates the directory list, performing a deep scan of the IFD
     * chain to collect all underlying data entries.
     *
     * <p>
     * In some cases (notably in JPEGs with EXIF data), where the initial directory identifier is
     * IFD1, for example, storing a thumbnail image, a check is provided to swap with IFD0, which is
     * the primary directory.
     * </p>
     * 
     * <p>
     * If any part of the directory structure is found to be corrupt, the directory list is cleared
     * to maintain data integrity.
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
        boolean hasIFD0 = false;
        long firstIFDoffset = readTifHeader();

        if (firstIFDoffset <= 0L)
        {
            LOGGER.error("Invalid TIFF header detected. Metadata parsing cancelled");
            return false;
        }

        if (!navigateImageFileDirectory(DirectoryIdentifier.IFD_ROOT_DIRECTORY, firstIFDoffset))
        {
            directoryList.clear();
            return false;
        }

        // Do identity check
        if (!directoryList.isEmpty())
        {
            DirectoryIFD firstIFD = directoryList.get(0);

            boolean hasThumbnailTag = firstIFD.hasTag(TagIFD_Baseline.IFD_JPEG_INTERCHANGE_FORMAT)
                    || firstIFD.hasTag(TagIFD_Baseline.IFD_NEW_SUBFILE_TYPE);

            if (hasThumbnailTag && firstIFD.getDirectoryType() == DirectoryIdentifier.IFD_ROOT_DIRECTORY)
            {
                LOGGER.debug("Detected IFD1 data in IFD0 slot. Swapping identities");

                firstIFD.setDirectoryType(DirectoryIdentifier.IFD_JPEG_INTERCHANGE_FORMAT);

                if (directoryList.size() > 1)
                {
                    directoryList.get(1).setDirectoryType(DirectoryIdentifier.IFD_DIRECTORY_IFD0);
                    Collections.swap(directoryList, 0, 1);
                }
            }
        }

        // IFD0 must exist
        for (DirectoryIFD dir : directoryList)
        {
            if (dir.getDirectoryType() == DirectoryIdentifier.IFD_DIRECTORY_IFD0)
            {
                hasIFD0 = true;
                break;
            }
        }

        if (!hasIFD0)
        {
            LOGGER.error("No Primary Image Directory (IFD0) was found after parsing and re-classification");
            directoryList.clear();
            return false;
        }

        return true;
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
     * and unsupported. This process validates the magic bytes ({@code II} - {@code 0x49 0x49} or
     * {@code MM} - {@code 0x4D 0x4D}) and determines the offset to IFD0 based on the version
     * detected. Anything that is not in compliance with the TIFF specification 6.0 or the file is
     * malformed will fail-fast.
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

        if (tiffVer == TIFF_BIG_VERSION)
        {
            LOGGER.warn("BigTIFF (version 43) not supported yet");
            return 0L;
        }

        else if (tiffVer != TIFF_STANDARD_VERSION)
        {
            LOGGER.error(String.format("Undefined TIFF magic number [%d]. Parsing cancelled", tiffVer));
            return 0L;
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
     * Each IFD begins with a 2-byte entry count, followed by a sequence of 12-byte directory
     * entries, and concludes with a 4-byte pointer to the next IFD.
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

        DirectoryIFD ifd = new DirectoryIFD(dirType);
        int entryCount = reader.readUnsignedShort();

        /* Process all 12-byte entries in this IFD first */
        for (int i = 0; i < entryCount; i++)
        {
            byte[] data;
            int tagID = reader.readUnsignedShort();
            Taggable tagEnum = TAG_LOOKUP.get(tagID);

            if (tagEnum == null)
            {
                tagEnum = new TagIFD_Unknown(tagID, dirType);
                LOGGER.warn(String.format("Unknown tag ID [0x%04X] detected", tagID));
            }

            TifFieldType fieldType = TifFieldType.getTiffType(reader.readUnsignedShort());
            long count = reader.readUnsignedInteger();
            byte[] valueBytes = reader.readBytes(4);
            long offset = ByteValueConverter.toUnsignedInteger(valueBytes, getTifByteOrder());
            long totalBytes = count * fieldType.getFieldSize();

            if (totalBytes == 0L || fieldType == TifFieldType.TYPE_ERROR)
            {
                LOGGER.error(String.format("Invalid type [%s] detected in tag [%s]. Skipped", fieldType, tagEnum));
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
                    LOGGER.error(String.format("Offset [0x%04X] out of bounds for [%s]", offset, tagEnum));
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
        }

        directoryList.add(ifd);
        LOGGER.debug("New directory [" + dirType + "] added");

        /*
         * Read pointer to the next IFD in the primary chain,
         * such as the transition from IFD0 to IFD1. This pointer
         * is always located immediately after the last entry
         */
        long nextOffset = reader.readUnsignedInteger();

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

        if (nextOffset == 0x0000L)
        {
            return true;
        }

        if (nextOffset <= startOffset || nextOffset >= reader.length())
        {
            LOGGER.error(String.format("Next IFD offset [0x%04X] invalid. Malformed file likely", nextOffset));
            return false;
        }

        /* Follow the main chain, for example: IFD0 -> IFD1 */
        return navigateImageFileDirectory(DirectoryIdentifier.getNextDirectoryType(dirType), nextOffset);
    }
}