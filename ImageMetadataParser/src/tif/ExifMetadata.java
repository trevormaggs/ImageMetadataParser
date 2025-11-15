package tif;

import static tif.tagspecs.TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A concrete metadata strategy for handling EXIF metadata, which is typically found in TIFF and
 * JPEG files. This class stores and provides access to various Image File Directories (IFDs),
 * such as the primary IFD, and EXIF sub-IFD.
 * *
 * <p>
 * This class implements the {@link ExifStrategy} interface, defining the specific behaviours
 * required for managing EXIF data, including checking for the presence of the EXIF sub-directory.
 * </p>
 */
public class ExifMetadata implements ExifStrategy
{
    private final Map<DirectoryIdentifier, DirectoryIFD> ifdMap;

    /**
     * Constructs a new {@code ExifMetadata} instance, creating the internal map for storing
     * metadata directories.
     */
    public ExifMetadata()
    {
        ifdMap = new HashMap<>();
    }

    /**
     * Checks if a specific directory type is present in the metadata.
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
     * Adds a {@link DirectoryIFD} to the metadata. The directory is stored and indexed by its type.
     *
     * @param directory
     *        the {@link DirectoryIFD} to add
     *
     * @throws NullPointerException
     *         if the provided directory is null
     */
    @Override
    public void addDirectory(DirectoryIFD directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
        }

        ifdMap.put(directory.getDirectoryType(), directory);
    }

    /**
     * Removes a {@link DirectoryIFD} from the metadata.
     *
     * @param directory
     *        the {@link DirectoryIFD} to remove
     * @return code if the directory was successfully removed, otherwise false
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
     * Retrieves a {@link DirectoryIFD} from the metadata using its identifier.
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
     * Checks if the metadata contains an EXIF directory, specifically, the EXIF sub-IFD.
     *
     * Note: This method re-declares the default method defined in the parent interface to
     * poly-morphically enable specialised behaviour. *
     *
     * @return true if an EXIF sub-IFD is present, otherwise false
     */
    @Override
    public boolean hasExifData()
    {
        return isDirectoryPresent(DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);
    }

    /**
     * Checks if the metadata contains an XMP directory.
     *
     * Note: This method re-declares the default method defined in the parent interface to
     * poly-morphically enable specialised behaviour.
     *
     * @return true if XMP metadata is present, otherwise false
     */
    @Override
    public boolean hasXmpData()
    {
        // TODO IMPLEMENT IT ASAP!

        /*
         * Assuming you define the XMP tag in TagIFD_Baseline (or similar)
         * private static final Taggable XMP_DATA_POINTER = TagIFD_Baseline.XMP_DATA_POINTER;
         * 
         * @Override
         * public boolean hasXmpData()
         * {
         * DirectoryIFD ifd0 = getDirectory(DirectoryIdentifier.IFD_DIRECTORY_IFD0);
         * return ifd0 != null && ifd0.containsTag(XMP_DATA_POINTER);
         * }
         * 
         * Implementation Note: XMP is usually stored in the Baseline IFD0 under tag 0x02BC
         * (XMP_DATA_POINTER). This tag holds the raw XML data. To implement hasXmpData(), the
         * ExifMetadata class needs to check if its IFD0 directory contains this tag.
         */
        return false;
    }

    /**
     * Extracts the {@code DateTimeOriginal} tag from a TIFF-based EXIF directory.
     *
     * @return a {@link Date} object extracted from the EXIF data, otherwise null if not found
     */
    @Override
    public Date extractDate()
    {
        if (hasExifData())
        {
            DirectoryIFD dir = getDirectory(DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);

            if (dir != null && dir.containsTag(EXIF_DATE_TIME_ORIGINAL))
            {
                return dir.getDate(EXIF_DATE_TIME_ORIGINAL);
            }
        }

        return null;
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
     * @return true if the collection is not empty, otherwise false
     */
    @Override
    public boolean hasMetadata()
    {
        return !isEmpty();
    }

    /**
     * Returns an iterator over all stored directories.
     *
     * @return an iterator of {@link DirectoryIFD} instances.
     */
    @Override
    public Iterator<DirectoryIFD> iterator()
    {
        return ifdMap.values().iterator();
    }
}