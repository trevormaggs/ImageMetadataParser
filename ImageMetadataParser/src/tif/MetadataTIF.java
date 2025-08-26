package tif;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import common.Metadata;

/**
 * A composite leaf component of the Composite design pattern that encapsulates Exif metadata stored
 * in TIFF-style directories. This class is typically used to manage and organise metadata extracted
 * from TIFF, JPEG, or PNG (via eXIf chunks) image files.
 *
 * <p>
 * It stores a map of {@link DirectoryIFD} instances, indexed by their {@link DirectoryIdentifier},
 * providing access, mutation, and query capabilities for structured image metadata.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class MetadataTIF implements Metadata<DirectoryIFD>
{
    private final Map<DirectoryIdentifier, DirectoryIFD> ifdMap;

    /**
     * Constructs an empty {@code MetadataTIF} container.
     */
    public MetadataTIF()
    {
        this.ifdMap = new HashMap<>();
    }

    /**
     * Adds a directory to the metadata map.
     *
     * @param directory
     *        the IFD directory to add
     */
    public void add(DirectoryIFD directory)
    {
        if (directory != null)
        {
            ifdMap.put(directory.getDirectoryType(), directory);
        }
    }

    /**
     * Removes a directory from the metadata map.
     *
     * @param directory
     *        the IFD directory to remove
     */
    public void remove(DirectoryIFD directory)
    {
        if (directory != null)
        {
            ifdMap.remove(directory.getDirectoryType());
        }
    }

    /**
     * Clears all metadata directories from this container.
     */
    public void clear()
    {
        ifdMap.clear();
    }

    /**
     * Checks whether a specific directory type is present.
     *
     * @param dir
     *        the directory identifier to check
     *
     * @return true if the directory is stored; otherwise false
     */
    public boolean isDirectoryPresent(DirectoryIdentifier dir)
    {
        return ifdMap.containsKey(dir);
    }

    /**
     * Adds a directory to this metadata container.
     *
     * @param directory
     *        the directory to be added
     */
    @Override
    public void addDirectory(DirectoryIFD directory)
    {
        add(directory);
    }

    /**
     * Retrieves a directory from the metadata map using a provided lookup component.
     *
     * <p>
     * If the component is a {@link DirectoryIdentifier}, a direct lookup is performed.
     * </p>
     *
     * @param <U>
     *        the type of the lookup key
     * @param component
     *        the lookup component (expected to be {@code DirectoryIdentifier})
     *
     * @return the matching {@link DirectoryIFD}, or null if not found
     */
    @Override
    public <U> DirectoryIFD getDirectory(U component)
    {
        if (component instanceof DirectoryIdentifier)
        {
            return ifdMap.get(component);
        }

        return null;
    }

    /**
     * Returns whether this metadata container stores any directories.
     *
     * @return true if no directories are stored, otherwise false
     */
    @Override
    public boolean isEmpty()
    {
        return ifdMap.isEmpty();
    }

    /**
     * Returns whether this metadata container has any metadata.
     *
     * @return true if one or more directories exist
     */
    @Override
    public boolean hasMetadata()
    {
        return !isEmpty();
    }

    /**
     * Checks whether Exif metadata is present in this structure.
     *
     * @return true if the EXIF directory is present
     */
    @Override
    public boolean hasExifData()
    {
        return isDirectoryPresent(DirectoryIdentifier.EXIF_DIRECTORY_SUBIFD);
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
     * Returns a simple concatenated string of all stored directories using their
     * {@code toString()} representations.
     *
     * @return a string containing all metadata directory values
     */
    @Override
    public String toString()
    {
        StringBuilder result = new StringBuilder();

        for (DirectoryIFD ifd : this)
        {
            result.append(ifd);
        }

        return result.toString();
    }
}