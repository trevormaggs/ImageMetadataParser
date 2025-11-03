package tif;

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
        return false;
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
        return (!isEmpty());
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