package tif;

import java.nio.ByteOrder;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import common.SmartDateParser;
import tif.tagspecs.TagIFD_Exif;
import xmp.XmpDirectory;
import xmp.XmpProperty;

/**
 * A container for storing information on extracted TIFF-based metadata, including EXIF and XMP
 * segments.
 * 
 * <p>
 * This class provides access to Image File Directories (IFDs), such as the primary IFD and the EXIF
 * sub-IFD, typically found in TIFF and JPEG files.
 * </p>
 * 
 * @author Trevor Maggs
 * @version 1.0
 * @since 12 March 2026
 */
public class TifMetadata implements TifMetadataProvider
{
    private final Map<DirectoryIdentifier, DirectoryIFD> ifdMap;
    private ByteOrder byteOrder;
    private XmpDirectory xmpDir;

    /**
     * Constructs an empty metadata container.
     */
    public TifMetadata()
    {
        this.ifdMap = new HashMap<>();
    }

    /**
     * Constructs a new {@code TifMetadata} object with the specified byte order for interpreting
     * multi-byte raw data correctly.
     *
     * @param byteOrder
     *        either {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     *
     * @throws NullPointerException
     *         if the byte order is null
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
     * @return either {@link java.nio.ByteOrder#BIG_ENDIAN} or
     *         {@link java.nio.ByteOrder#LITTLE_ENDIAN}
     */
    @Override
    public ByteOrder getByteOrder()
    {
        return byteOrder;
    }
    /**
     * Checks if a specific directory type is present.
     *
     * @param dir
     *        the {@link DirectoryIdentifier} to check for
     * @return {@code true} if the directory is present
     */
    public boolean isDirectoryPresent(DirectoryIdentifier dir)
    {
        return ifdMap.containsKey(dir);
    }

    /**
     * Adds a new {@link DirectoryIFD} to the container.
     *
     * @param directory
     *        the directory to add
     * 
     * @throws NullPointerException
     *         if the directory is null
     * @throws IllegalStateException
     *         if the byte order is not yet determined. Make sure the parameterised constructor is
     *         called first
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
            throw new IllegalStateException("ByteOrder is undefined. Please ensure the TIFF header is processed first");
        }

        ifdMap.put(directory.getDirectoryType(), directory);
    }

    /**
     * Removes a {@link DirectoryIFD} from the container.
     *
     * @param directory
     *        the {@link DirectoryIFD} to remove
     * @return {@code true} if the directory was successfully removed
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
     * Adds a new {@link XmpDirectory} directory to this container.
     *
     * @param dir
     *        the {@link XmpDirectory} to be added
     *
     * @throws NullPointerException
     *         if the specified directory is null
     */
    @Override
    public void addXmpDirectory(XmpDirectory dir)
    {
        if (dir == null)
        {
            throw new NullPointerException("XMP directory cannot be null");
        }

        this.xmpDir = dir;
    }

    /**
     * Retrieves a {@link DirectoryIFD} from the container by its identifier.
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
     * @return the {@link XmpDirectory}, or null if absent. Check {@link #hasXmpData()} first to
     *         avoid null handling
     */
    @Override
    public XmpDirectory getXmpDirectory()
    {
        return xmpDir;
    }

    /**
     * Checks if the metadata container is empty.
     *
     * @return {@code true} if no directories are present
     */
    @Override
    public boolean isEmpty()
    {
        return !hasMetadata();
    }

    /**
     * Checks if the collection contains any metadata.
     *
     * @return {@code true} if the container is not empty
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
     * Checks if the collection contains an EXIF sub-directory.
     *
     * @return {@code true} if an EXIF sub-IFD is present
     */
    @Override
    public boolean hasExifData()
    {
        return isDirectoryPresent(DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);
    }

    /**
     * Checks if the collection contains an XMP directory.
     *
     * @return {@code true} if XMP metadata is present
     */
    @Override
    public boolean hasXmpData()
    {
        return (xmpDir != null && xmpDir.size() > 0);
    }

    /**
     * Extracts the most authoritative creation date available.
     * 
     * <p>
     * The extraction follows a priority waterfall:
     * </p>
     * 
     * <ol>
     * <li>EXIF Sub-IFD {@code DateTimeOriginal}</li>
     * <li>XMP EXIF Schema {@code DateTimeOriginal}</li>
     * <li>XMP General Schema {@code CreateDate}</li>
     * </ol>
     *
     * @return the extracted {@link Date}, or {@code null} if no valid timestamp is present
     */
    @Override
    public Date extractDate()
    {
        if (hasExifData())
        {
            DirectoryIFD dir = getDirectory(DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);

            if (dir != null && dir.hasTag(TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL))
            {
                return dir.getDate(TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL);
            }
        }

        if (hasXmpData())
        {
            Optional<String> opt = xmpDir.getValueByPath(XmpProperty.EXIF_DATE_TIME_ORIGINAL);

            if (opt.isPresent())
            {
                Date date = SmartDateParser.convertToDate(opt.get());

                if (date != null)
                {
                    return date;
                }
            }

            opt = xmpDir.getValueByPath(XmpProperty.XMP_CREATEDATE);

            if (opt.isPresent())
            {
                Date date = SmartDateParser.convertToDate(opt.get());

                if (date != null)
                {
                    return date;
                }
            }
        }

        return null;
    }
}