package tif;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import tif.DirectoryIFD.EntryIFD;
import tif.tagspecs.TagIFD_Baseline;
import tif.tagspecs.TagIFD_Exif;

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
    private final Map<DirectoryIdentifier, DirectoryIFD> ifdMap;

    /**
     * Constructs a new {@code TifMetadata} instance, creating the internal map for storing
     * metadata directories.
     */
    public TifMetadata()
    {
        ifdMap = new HashMap<>();
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
        return !isEmpty();
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
        if (isDirectoryPresent(DirectoryIdentifier.IFD_DIRECTORY_IFD0))
        {
            DirectoryIFD ifd0 = getDirectory(DirectoryIdentifier.IFD_DIRECTORY_IFD0);

            if (ifd0.containsTag(TagIFD_Baseline.IFD_XMP_DATA))
            {
                for (EntryIFD entry : ifd0)
                {
                    System.out.printf("LOOK %s%n", entry);
                }
            }
        }

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
         * TifMetadata class needs to check if its IFD0 directory contains this tag.
         */
        return false;
    }

    /**
     * Extracts the {@code DateTimeOriginal} tag from an EXIF directory if available.
     *
     * @return a {@link Date} object extracted from the EXIF segment, otherwise null if not found
     */
    @Override
    public Date extractDate()
    {
        if (hasExifData())
        {
            DirectoryIFD dir = getDirectory(DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);

            if (dir != null && dir.containsTag(TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL))
            {
                return dir.getDate(TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL);
            }
        }

        return null;
    }
}