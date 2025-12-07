package tif;

import java.nio.ByteOrder;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import common.DateParser;
import common.ImageReadErrorException;
import logger.LogFactory;
import tif.tagspecs.TagIFD_Baseline;
import tif.tagspecs.TagIFD_Exif;
import xmp.XmpDirectory;
import xmp.XmpHandler;
import xmp.XmpProperty;

/**
 * A concrete metadata strategy for managing a collection of metadata, including EXIF and
 * potentially XMP metadata. These metadata segments are typically found in TIFF and JPEG files.
 * This class stores and provides access to various Image File Directories (IFDs), such as the
 * primary IFD, and EXIF sub-IFD.
 *
 * <p>
 * This class implements the {@link TifMetadataStrategy} interface, defining the specific behaviours
 * required for managing EXIF data, including checking for the presence of the EXIF sub-directory.
 * </p>
 */
public class TifMetadata implements TifMetadataStrategy
{
    private static final LogFactory LOGGER = LogFactory.getLogger(TifMetadata.class);
    private final Map<DirectoryIdentifier, DirectoryIFD> ifdMap;
    private ByteOrder byteOrder;
    private XmpDirectory xmpDir;

    /**
     * Constructs a new {@code TifMetadata} instance.
     */
    public TifMetadata()
    {
        this.ifdMap = new HashMap<>();
    }

    /**
     * Constructs a new {@code TifMetadata} object, respecting the byte order for correctly
     * interpreting multi-byte raw data.
     * 
     * @param byteOrder
     *        the byte order either {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * 
     * @throws NullPointerException
     *         if the specified byte order is null
     */
    public TifMetadata(ByteOrder byteOrder)
    {
        this();

        if (byteOrder == null)
        {
            throw new NullPointerException("ByteOrder is null");
        }

        this.byteOrder = byteOrder;
    }

    /**
     * Returns the byte order, indicating how data values will be interpreted correctly.
     *
     * @return either {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     */
    @Override
    public ByteOrder getByteOrder()
    {
        return byteOrder;
    }
    /**
     * Checks if a specific directory type is present in the metadata collection.
     *
     * @param dir
     *        the {@link DirectoryIdentifier} to check for
     * @return true if the directory is present, otherwise false
     */
    public boolean isDirectoryPresent(DirectoryIdentifier dir)
    {
        return ifdMap.containsKey(dir);
    }

    /**
     * Adds a new {@link DirectoryIFD} to the collection. The directory is stored and indexed by its
     * type.
     *
     * @param directory
     *        the {@link DirectoryIFD} to add
     *
     * @throws NullPointerException
     *         if the provided directory is null
     * @throws IllegalStateException
     *         if the byte order is not in an deterministic state. Be sure to invoke the
     *         parameterised constructor first
     */
    @Override
    public void addDirectory(DirectoryIFD directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
        }

        if (byteOrder == null)
        {
            throw new IllegalStateException("ByteOrder is unknown. Cannot add DirectoryIFD. Please ensure the TIFF header is handled first");
        }

        ifdMap.put(directory.getDirectoryType(), directory);

        /*
         * Add an XMP directory if there are XMP properties
         * within the IFD directory.
         */
        if (directory.contains(TagIFD_Baseline.IFD_XML_PACKET))
        {
            addXmpDirectory(directory.getRawByteArray(TagIFD_Baseline.IFD_XML_PACKET));
        }
    }

    /**
     * Removes a {@link DirectoryIFD} from the collection.
     *
     * @param directory
     *        the {@link DirectoryIFD} to remove
     * @return true if the directory was successfully removed, otherwise false
     *
     * @throws NullPointerException
     *         if the provided directory is null
     */
    @Override
    public boolean removeDirectory(DirectoryIFD directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
        }

        return (ifdMap.remove(directory.getDirectoryType()) != null);
    }

    /**
     * Adds a new {@link XmpDirectory} directory to manage XMP metadata.
     *
     * @param payload
     *        raw XMP data as a single byte array
     *
     * @throws ImageReadErrorException
     *         if the provided directory is null
     */
    @Override
    public void addXmpDirectory(byte[] payload)
    {
        try
        {
            XmpHandler xmp = new XmpHandler(payload);

            if (xmp.parseMetadata())
            {
                this.xmpDir = xmp.getXmpDirectory();
            }
        }

        catch (ImageReadErrorException exc)
        {
            LOGGER.warn("Failed to parse XMP directory payload [" + exc.getMessage() + "]");
        }
    }

    /**
     * Retrieves a {@link DirectoryIFD} from the collection based on its identifier.
     *
     * @param key
     *        the {@link DirectoryIdentifier} of the directory to retrieve
     * @return the {@link DirectoryIFD} associated with the key, or null if not found
     */
    @Override
    public DirectoryIFD getDirectory(DirectoryIdentifier key)
    {
        return ifdMap.get(key);
    }

    /**
     * Retrieves the parsed {@link XmpDirectory} XMP metadata directory.
     *
     * @return the XmpDirectory containing parsed properties, or null if XMP data was not found or
     *         failed to parse. To avoid processing null, checking with the {@link hasXmpData()}
     *         method first is recommended
     */
    @Override
    public XmpDirectory getXmpDirectory()
    {
        return xmpDir;
    }

    /**
     * Checks if this metadata collection is empty.
     *
     * @return true if no directories are stored, otherwise false
     */
    @Override
    public boolean isEmpty()
    {
        return ifdMap.isEmpty();
    }

    /**
     * Checks if this metadata collection contains any metadata.
     *
     * @return true if the collection contains some metadata, otherwise false
     */
    @Override
    public boolean hasMetadata()
    {
        return !ifdMap.isEmpty() || hasXmpData();
    }

    /**
     * Returns an iterator over all stored directories.
     *
     * @return an iterator of {@link DirectoryIFD} instances
     */
    @Override
    public Iterator<DirectoryIFD> iterator()
    {
        return ifdMap.values().iterator();
    }

    /**
     * Checks if the collection contains an EXIF directory, specifically, the EXIF sub-IFD.
     *
     * Note: This method re-declares the default method defined in the parent interface to
     * poly-morphically enable specialised behaviour.
     *
     * @return true if an EXIF sub-IFD is present, otherwise false
     */
    @Override
    public boolean hasExifData()
    {
        return isDirectoryPresent(DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);
    }

    /**
     * Checks if the collection contains an XMP directory.
     *
     * Note: This method re-declares the default method defined in the parent interface to
     * poly-morphically enable specialised behaviour.
     *
     * @return true if XMP metadata is present, otherwise false
     */
    @Override
    public boolean hasXmpData()
    {
        return (xmpDir != null && xmpDir.size() > 0);
    }

    /**
     * Extracts the {@code DateTimeOriginal} tag from an EXIF directory if available. If it is not
     * found, attempts will be made to find the creation time-stamp in the XMP segment if present.
     *
     * @return a {@link Date} object extracted from the EXIF or XMP segment, otherwise null if not
     *         found
     */
    @Override
    public Date extractDate()
    {
        if (hasExifData())
        {
            DirectoryIFD dir = getDirectory(DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);

            if (dir != null && dir.contains(TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL))
            {
                return dir.getDate(TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL);
            }
        }

        if (hasXmpData())
        {
            Optional<String> opt = xmpDir.getValueByPath(XmpProperty.EXIF_DATE_TIME_ORIGINAL);

            if (opt.isPresent())
            {
                Date date = DateParser.convertToDate(opt.get());

                if (date != null)
                {
                    return date;
                }
            }

            opt = xmpDir.getValueByPath(XmpProperty.XMP_CREATEDATE);

            if (opt.isPresent())
            {
                Date date = DateParser.convertToDate(opt.get());

                if (date != null)
                {
                    return date;
                }
            }
        }

        return null;
    }
}