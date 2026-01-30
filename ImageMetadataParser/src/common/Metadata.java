package common;

import java.nio.ByteOrder;
import java.util.Date;

/**
 * A unified container for image metadata, organised into one or more {@link Directory} objects.
 * This interface provides a standard way to manage, query, and traverse metadata extracted from
 * various image formats, i.e. TIFF, PNG, JPEG, etc.
 *
 * @param <D>
 *        the specific type of Directory handled by this metadata container, such as
 *        {@code DirectoryIFD} or {@code PngDirectory}
 */
public interface Metadata<D extends Directory<?>> extends Iterable<D>
{
    /**
     * Adds a metadata directory to the collection.
     *
     * @param directory
     *        the directory to add
     */
    void addDirectory(D directory);

    /**
     * Removes a metadata directory from the collection.
     *
     * @param directory
     *        the directory to remove
     * @return {@code true} if the directory was found and removed, otherwise {@code false}
     */
    boolean removeDirectory(D directory);

    /**
     * Checks if the metadata collection is empty.
     *
     * @return {@code true} if the collection contains no directories, otherwise {@code false}
     */
    boolean isEmpty();

    /**
     * Returns the global byte order used by the image format. Note: specific metadata segments
     * (like EXIF) may define their own internal endian-ness.
     *
     * @return either {@link java.nio.ByteOrder#BIG_ENDIAN} or
     *         {@link java.nio.ByteOrder#LITTLE_ENDIAN}
     */
    ByteOrder getByteOrder();

    /**
     * Checks if the metadata collection contains any metadata entries.
     *
     * @return {@code true} if at least one directory is present, otherwise {@code false}
     */
    default boolean hasMetadata()
    {
        return !isEmpty();
    }

    /**
     * Indicates whether the container holds EXIF metadata.
     * 
     * @return {@code true} if EXIF metadata is present, otherwise {@code false}
     */
    default boolean hasExifData()
    {
        return false;
    }

    /**
     * Indicates whether the container holds XMP (Adobe Extensible Metadata Platform) metadata.
     * 
     * @return {@code true} if XMP metadata is present, otherwise {@code false}
     */
    default boolean hasXmpData()
    {
        return false;
    }

    /**
     * Performs a best-effort extraction of the image's creation or capture date.
     * 
     * <p>
     * Implementations should prioritise high-fidelity sources, i.e. EXIF {@code DateTimeOriginal}
     * before falling back to less reliable sources, i.e. file modification time.
     * </p>
     *
     * @return the prioritised {@link Date} instance, or {@code null} if no temporal metadata is
     *         found
     */
    default Date extractDate()
    {
        return null;
    }
}