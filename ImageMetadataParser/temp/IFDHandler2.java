package tif;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import common.ByteStreamReader;
import common.ByteValueConverter;
import common.ImageHandler;
import common.ImageRandomAccessReader;
import common.SequentialByteArrayReader;
import logger.LogFactory;
import tif.DirectoryIFD.EntryIFD;
import tif.tagspecs.TagExif_Interop;
import tif.tagspecs.TagIFD_Baseline;
import tif.tagspecs.TagIFD_Exif;
import tif.tagspecs.TagIFD_Extension;
import tif.tagspecs.TagIFD_GPS;
import tif.tagspecs.TagIFD_Private;
import tif.tagspecs.TagIFD_Unknown;
import tif.tagspecs.Taggable;

/**
 * Parses TIFF-based metadata structures (standard TIFF, EXIF in JPEGs, and DNG) by interpreting
 * Image File Directories (IFDs).
 *
 * <p>
 * This handler performs a deep recursive traversal of the IFD chain, following pointers to
 * sub-directories such as {@code EXIF}, {@code GPS}, and {@code INTEROP}. It adheres to the TIFF
 * 6.0 specification for directory structures and tag entry parsing.
 * </p>
 *
 * <p>
 * <strong>Note:</strong> While this handler detects BigTIFF (version 43), it currently only
 * supports Standard TIFF (version 42). This class focuses exclusively on IFD-based metadata. Other
 * formats like XMP or ICC profiles should be managed by a different Image Parser.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.1
 * @since 5 September 2025
 * @see <a href="https://partners.adobe.com/public/developer/en/tiff/TIFF6.pdf">TIFF 6.0
 *      Specification</a>
 */
public class IFDHandler2 implements ImageHandler, AutoCloseable
{
    private static final LogFactory LOGGER = LogFactory.getLogger(IFDHandler2.class);
    private static final int TIFF_STANDARD_VERSION = 42;
    private static final int TIFF_BIG_VERSION = 43;
    public static final int ENTRY_MAX_VALUE_LENGTH = 4;
    public static final int ENTRY_MAX_VALUE_LENGTH_BIG = 8;
    private final List<DirectoryIFD> directoryList = new ArrayList<>();
    private static final Map<Taggable, DirectoryIdentifier> subIfdMap;
    private static final Map<DirectoryCategory, Map<Integer, Taggable>> TAG_REGISTRY;
    private final ByteStreamReader reader;
    private boolean isTiffBig;

    /**
     * Categorises directories into logical groups to handle overlapping Tag IDs across different
     * IFD schemas, for example: GPS tags vs. Baseline tags, etc.
     */
    private enum DirectoryCategory
    {
        MAIN_ROOT, EXIF, GPS, INTEROP
    }

    static
    {
        /* Linkable directories */
        subIfdMap = new HashMap<>();
        subIfdMap.put(TagIFD_Extension.IFD_IFDSUB_POINTER, DirectoryIdentifier.IFD_DIRECTORY_SUBIFD);
        subIfdMap.put(TagIFD_Extension.IFD_EXIF_POINTER, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);
        subIfdMap.put(TagIFD_Extension.IFD_GPS_INFO_POINTER, DirectoryIdentifier.IFD_GPS_DIRECTORY);
        subIfdMap.put(TagIFD_Exif.EXIF_INTEROPERABILITY_POINTER, DirectoryIdentifier.EXIF_INTEROP_DIRECTORY);

        /* Register all Taggable tags by category for efficient Tag ID search and capture */
        TAG_REGISTRY = new HashMap<>();
        register(TagIFD_Baseline.class, DirectoryCategory.MAIN_ROOT);
        register(TagIFD_Extension.class, DirectoryCategory.MAIN_ROOT);
        register(TagIFD_Private.class, DirectoryCategory.MAIN_ROOT);
        register(TagIFD_Exif.class, DirectoryCategory.EXIF);
        register(TagIFD_GPS.class, DirectoryCategory.GPS);
        register(TagExif_Interop.class, DirectoryCategory.INTEROP);

        if (LOGGER.isDebugEnabled())
        {
            for (Map.Entry<DirectoryCategory, Map<Integer, Taggable>> entry : TAG_REGISTRY.entrySet())
            {
                for (Taggable tag : entry.getValue().values())
                {
                    LOGGER.debug(String.format("Registered: %-10s | 0x%04X | %s", entry.getKey(), tag.getNumberID(), tag));
                }
            }
        }
    }

    /**
     * Constructs a handler using an existing byte stream reader.
     * 
     * @param reader
     *        the stream reader providing access to TIFF content
     */
    public IFDHandler2(ByteStreamReader reader)
    {
        this.reader = reader;
    }

    /**
     * Constructs a handler that reads metadata from a file on disk.
     * 
     * <p>
     * To ensure the underlying file handle is released and to prevent file locks, this handler
     * should be managed within a {@code try-with-resources} block.
     * </p>
     * 
     * @param fpath
     *        the path to the image file
     * @throws IOException
     *         if the file is inaccessible
     */
    public IFDHandler2(Path fpath) throws IOException
    {
        this.reader = new ImageRandomAccessReader(fpath);
    }

    /**
     * Constructs a handler for in-memory byte arrays.
     * 
     * @param payload
     *        byte array containing TIFF-formatted data
     */
    public IFDHandler2(byte[] payload)
    {
        this.reader = new SequentialByteArrayReader(payload);
    }

    /**
     * Returns the list of IFD directories that were successfully parsed.
     * 
     * @return an unmodifiable {@link List} of all parsed {@link DirectoryIFD} structures
     */
    public List<DirectoryIFD> getDirectories()
    {
        return Collections.unmodifiableList(directoryList);
    }

    /**
     * Returns the byte order, indicating how metadata values should be interpreted correctly.
     * 
     * @return the {@link ByteOrder} detected in the TIFF header
     */
    public ByteOrder getTifByteOrder()
    {
        return reader.getByteOrder();
    }

    /**
     * Indicates whether the parsed file is a BigTIFF variant (version 43).
     * 
     * @return {@code true} if the file magic number identifies it as BigTIFF (version 43)
     */
    public boolean isBigTiffVersion()
    {
        return isTiffBig;
    }

    /**
     * Executes the parsing logic, performing a deep scan and populating the directory list.
     *
     * <p>
     * It validates the header and traverses the IFD chain. In specific cases, for example: JPEGs
     * where the thumbnail IFD1 appears before the primary IFD0, the handler re-orders the results
     * to ensure the primary image metadata is consistently at index 0.
     * </p>
     * 
     * <p>
     * If any part of the directory structure is found to be corrupt, the directory list is cleared
     * to maintain data integrity.
     * </p>
     *
     * @return {@code true} if at least one valid directory (IFD0) was extracted
     * @throws IOException
     *         if an I/O error occurs during streaming
     */
    @Override
    public boolean parseMetadata() throws IOException
    {
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
                LOGGER.debug("Detected IFD1 data in IFD0 slot. Re-ordering directories");
                firstIFD.setDirectoryType(DirectoryIdentifier.IFD_THUMBNAIL_DIRECTORY);

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
                return true;
            }
        }

        directoryList.clear();
        LOGGER.error("No Primary Image Directory (IFD0) was found after parsing and re-classification");

        return false;
    }

    /**
     * Closes the underlying stream reader and releases system resources.
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
     * Identifies byte order, TIFF version, and returns the initial IFD offset.
     * 
     * <p>
     * <strong>Requirement:</strong> The stream must be positioned at the TIFF magic bytes. Any
     * preambles (HEIF/JPEG markers) must be skipped prior to calling this method.
     * </p>
     *
     * @return the absolute offset to IFD0, or {@code 0L} if the header is malformed or unsupported,
     *         for example: BigTIFF
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
        }
        else if (firstByte == 0x4D && secondByte == 0x4D)
        {
            reader.setByteOrder(ByteOrder.BIG_ENDIAN);
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
            LOGGER.warn("BigTIFF (version 43) not supported yet");
            return 0L;
        }

        else if (tiffVer != TIFF_STANDARD_VERSION)
        {
            LOGGER.error(String.format("Undefined TIFF magic number [%d].", tiffVer));
            return 0L;
        }

        /* Advance by offset from base to IFD0 */
        long firstOffset = reader.readUnsignedInteger();

        if (firstOffset < 8L || firstOffset >= reader.length())
        {
            LOGGER.error("Malformed TIFF header: IFD0 offset points to invalid location");
            return 0L;
        }

        return firstOffset;
    }

    /**
     * Recursively traverses a physical IFD and its linked sub-directories.
     *
     * <p>
     * Each IFD is parsed as a 2-byte entry count, followed by a sequence of 12-byte
     * entries, and a 4-byte pointer to the next IFD in the main chain.
     * </p>
     *
     * @param dirType
     *        the physical directory identity being processed
     * @param startOffset
     *        the file offset where the IFD block begins
     * @return {@code true} if the directory and all linked IFDs were successfully parsed
     * 
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
            DirectoryCategory cat = getLogicalSchema(dirType);
            Taggable tagEnum = TAG_REGISTRY.get(cat).get(tagID);

            if (tagEnum == null)
            {
                tagEnum = new TagIFD_Unknown(tagID, dirType);
                LOGGER.warn(String.format("Unknown tag ID [0x%04X] detected in directory [%s]", tagID, dirType));
            }

            TifFieldType fieldType = TifFieldType.getTiffType(reader.readUnsignedShort());
            long count = reader.readUnsignedInteger();
            byte[] valueBytes = reader.readBytes(4);
            long offset = ByteValueConverter.toUnsignedInteger(valueBytes, getTifByteOrder());
            long totalBytes = count * fieldType.getFieldSize();

            if (totalBytes == 0L || fieldType == TifFieldType.TYPE_ERROR)
            {
                continue;
            }

            /*
             * A length of the value that is larger than 4 bytes indicates
             * the entry is an offset outside this directory field.
             */
            if (totalBytes > ENTRY_MAX_VALUE_LENGTH)
            {
                if (offset < 0 || offset + totalBytes > reader.length() || totalBytes > Integer.MAX_VALUE)
                {
                    LOGGER.error(String.format("Offset [0x%04X] out of bounds for [%s]", offset, tagEnum));
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
                reader.mark();

                if (!navigateImageFileDirectory(subIfdMap.get(tag), entry.getOffset()))
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

    /**
     * Registers all constants of each Taggable Enum class into the global registry.
     * 
     * <p>
     * Uses a "First-Defined-Wins" policy to ensure standard tags take precedence.
     * </p>
     *
     * @param <E>
     *        an Enum type that implements Taggable
     * @param enumClass
     *        the Enum class literal, for example: TagIFD_Exif.class
     * @param category
     *        the logical directory category to which these tags belong
     */
    private static <E extends Enum<E> & Taggable> void register(Class<E> enumClass, DirectoryCategory category)
    {
        TAG_REGISTRY.putIfAbsent(category, new HashMap<>());
        Map<Integer, Taggable> map = TAG_REGISTRY.get(category);

        E[] constants = enumClass.getEnumConstants();

        if (constants != null)
        {
            for (E tag : constants)
            {
                Taggable existing = map.putIfAbsent(tag.getNumberID(), tag);
                
                if (existing != null)
                {
                    LOGGER.error(String.format("Tag ID 0x%04X in [%s] conflicts with [%s]", tag.getNumberID(), existing, tag));
                }
            }
        }
    }

    /**
     * Maps physical directories to its corresponding logical schemas to resolve Tag ID collisions.
     * 
     * <p>
     * While TIFF files may contain multiple physical directories (IFD0, IFD1, etc.), they often
     * share the same tag definitions (Baseline/Extension). Conversely, specialised sub-directories
     * like EXIF or GPS require localised schemas where Tag IDs may overlap with those in the Root
     * directory but carry different meanings.
     * </p>
     * 
     * @param dirType
     *        the physical directory type identified during the file crawl
     * @return the logical {@link DirectoryCategory} used to fetch the correct tag registry map
     */
    private DirectoryCategory getLogicalSchema(DirectoryIdentifier dirType)
    {
        DirectoryCategory retVal;

        switch (dirType)
        {
            case IFD_EXIF_SUBIFD_DIRECTORY:
                retVal = DirectoryCategory.EXIF;
            break;

            case IFD_GPS_DIRECTORY:
                retVal = DirectoryCategory.GPS;
            break;

            case EXIF_INTEROP_DIRECTORY:
                retVal = DirectoryCategory.INTEROP;
            break;

            // Default: IFD0, IFD1, IFD2, and ROOT all share the Baseline/Main schema
            default:
                retVal = DirectoryCategory.MAIN_ROOT;
        }

        return retVal;
    }
}