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
    private int tifHeaderOffset;

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
     * Constructs an IFD handler for reading TIFF metadata using the specified byte reader.
     *
     * @param reader
     *        the byte reader providing access to the TIFF file content
     */
    public IFDHandler(SequentialByteReader reader)
    {
        this.reader = reader;
        this.tifHeaderOffset = 0;
        this.directoryList = new ArrayList<>();
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
     * Always return a zero value.
     *
     * @return always zero
     */
    @Override
    public long getSafeFileSize()
    {
        return 0L;
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

        navigateImageFileDirectory(DirectoryIdentifier.IFD_DIRECTORY_IFD0, tifHeaderOffset + firstIFDoffset);

        return (!directoryList.isEmpty());
    }

    /**
     * Returns the list of IFD directories that were successfully parsed.
     *
     * <p>
     * If no directories were found, this method returns {@link Optional#empty()}. Otherwise, it
     * returns an {@link Optional} containing a copy of the parsed IFD directory list.
     * </p>
     *
     * @return an {@link Optional} containing at least one {@link DirectoryIFD}, or
     *         {@link Optional#empty()} if no directories were parsed
     */
    public Optional<List<DirectoryIFD>> getDirectories()
    {
        return (directoryList.isEmpty() ? Optional.empty() : Optional.of(Collections.unmodifiableList(directoryList)));
    }

    /**
     * Reads the TIFF header to determine the byte order, version (Standard or BigTIFF), and the
     * offset to the first Image File Directory (IFD0). The first two bytes indicate the byte order
     * ("II" for little-endian or "MM" for big-endian). The next two bytes contain the TIFF version
     * number (42 for standard TIFF, 43 for BigTIFF). Finally, the last four bytes of the 8-byte
     * header specify the offset to the first IFD.
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
            LOGGER.debug("Byte order detected as [Intel]");
        }

        else if (firstByte == 0x4D && secondByte == 0x4D)
        {
            reader.setByteOrder(ByteOrder.BIG_ENDIAN);
            LOGGER.debug("Byte order detected as [Motorola]");
        }

        else
        {
            LOGGER.warn(String.format("Mismatched or unknown byte order bytes [First byte: 0x%04X ] and [Second byte: 0x%04X]", firstByte, secondByte));
            return 0L;
        }

        /* Identify whether this is Standard TIFF (42) or Big TIFF (43) version */
        int tiffVer = reader.readUnsignedShort();
        isTiffBig = (tiffVer == TIFF_BIG_VERSION);

        if (isTiffBig)
        {
            /* TODO - develop and expand to support Big Tiff */
            LOGGER.warn("BigTIFF detected (not fully supported yet)");
            throw new UnsupportedOperationException("BigTIFF (version 43) not supported yet");
        }

        else if (tiffVer != TIFF_STANDARD_VERSION)
        {
            LOGGER.warn("Unexpected TIFF version [" + tiffVer + "], defaulting to standard TIFF 6.0");
        }

        /* Advance by offset from base to IFD0 */
        return reader.readInteger();
    }

    /**
     * Recursively traverses the specified Image File Directory and its linked sub-directories.
     * An IFD is a sequence of 12-byte entries, each containing a tag ID, a field type, a count of
     * values, and a 4-byte value or offset. This method iterates through these entries, reads the
     * corresponding data, and if an entry points to another IFD (like EXIF, GPS, or Interop),
     * it recursively calls itself to parse that sub-directory.
     *
     * @param dirType
     *        the directory type being processed
     * @param startOffset
     *        the file offset (from header base) where the IFD begins
     */
    private void navigateImageFileDirectory(DirectoryIdentifier dirType, long startOffset)
    {
        if (startOffset < 0 || startOffset >= reader.length())
        {
            LOGGER.warn("Invalid offset [" + startOffset + "] for directory [" + dirType + "]");
            return;
        }

        reader.seek((int) startOffset);
        DirectoryIFD ifd = new DirectoryIFD(dirType, reader.getByteOrder());
        int entryCount = reader.readUnsignedShort();

        for (int i = 0; i < entryCount; i++)
        {
            int tagID = reader.readUnsignedShort();
            Taggable tagEnum = TAG_LOOKUP.get(tagID);

            /*
             * In some instances where tag IDs are found to be unknown or unspecified
             * in scope of this scanner, this will safely skip the whole segment and
             * continue to the next iteration.
             */
            if (tagEnum == null)
            {
                LOGGER.warn("Unknown tag ID: 0x" + Integer.toHexString(tagID));
                reader.skip(10); // skip rest of the entry
                continue;
            }

            TifFieldType fieldType = TifFieldType.getTiffType(reader.readUnsignedShort());
            int count = (int) reader.readUnsignedInteger();
            byte[] valueBytes = reader.readBytes(4);

            int offset = ByteValueConverter.toInteger(valueBytes, reader.getByteOrder());
            long totalBytes = (long) count * fieldType.getElementLength();

            byte[] data;

            /*
             * A length of the value that is larger than 4 bytes indicates
             * the entry is an offset outside this directory field.
             */
            if (totalBytes > ENTRY_MAX_VALUE_LENGTH)
            {
                // Using long to prevent integer wrap-around risk
                if (tifHeaderOffset + offset < 0 || tifHeaderOffset + offset + totalBytes > reader.length())
                {
                    LOGGER.warn("Offset out of bounds for tag [" + tagEnum + "]");
                    continue;
                }

                data = reader.peek(tifHeaderOffset + offset, (int) totalBytes);
            }

            else
            {
                data = valueBytes;
            }

            /* Make sure the tag ID is known and defined in TIF Specification 6.0 */
            if (TifFieldType.dataTypeinRange(fieldType.getDataType()))
            {
                ifd.addEntry(tagEnum, fieldType, count, offset, data);
            }

            else
            {
                LOGGER.warn("Unknown field type [" + fieldType + "] for tag [" + tagEnum + "]");
                continue;
            }

            if (subIfdMap.containsKey(tagEnum))
            {
                reader.mark();
                navigateImageFileDirectory(subIfdMap.get(tagEnum), tifHeaderOffset + offset);
                reader.reset();
            }
        }

        directoryList.add(ifd);

        long nextOffset = reader.readUnsignedInteger();

        if (nextOffset != 0x0000L)
        {
            navigateImageFileDirectory(DirectoryIdentifier.getNextDirectoryType(dirType), tifHeaderOffset + nextOffset);
        }
    }
}