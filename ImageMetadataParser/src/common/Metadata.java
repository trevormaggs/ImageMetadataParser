package common;

import java.nio.ByteOrder;
import java.util.Date;

/**
 * A unified container for image metadata, organised into one or more {@link Directory} objects.
 * This interface provides a standard way to manage, query, and traverse metadata extracted from
 * various image formats, i.e. TIFF, PNG, JPEG.
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
     * Checks if the metadata collection contains any directories.
     *
     * @return {@code true} if at least one directory is present, otherwise {@code false}
     */
    default boolean hasMetadata()
    {
        return !isEmpty();
    }

    /**
     * Returns the byte order used by the underlying image data, indicating how multi-byte values
     * are interpreted.
     *
     * @return either {@link java.nio.ByteOrder#BIG_ENDIAN} or
     *         {@link java.nio.ByteOrder#LITTLE_ENDIAN}
     */
    ByteOrder getByteOrder();

    /**
     * Checks if this container contains EXIF metadata. Default implementation returns
     * {@code false}.
     * 
     * @return {@code true} if EXIF metadata is present, otherwise {@code false}
     */
    default boolean hasExifData()
    {
        return false;
    }

    /**
     * Checks if this container contains textual metadata (e.g., PNG tEXt/iTXt chunks). Default
     * implementation returns {@code false}.
     * 
     * @return {@code true} if textual information is present, otherwise {@code false}
     */
    default boolean hasTextualData()
    {
        return false;
    }

    /**
     * Checks if this container contains XMP (Adobe Extensible Metadata Platform) metadata. Default
     * implementation returns {@code false}.
     * 
     * @return {@code true} if XMP metadata is present, otherwise {@code false}
     */
    default boolean hasXmpData()
    {
        return false;
    }

    /**
     * Scans all available metadata directories and attempts to extract the most reliable
     * {@code creation/capture date}.
     * 
     * <p>
     * This method prioritizes specific tags based on accuracy, i.e., {@code EXIF:DateTimeOriginal}
     * is usually preferred over {@code TIFF:DateTime}.
     * </p>
     *
     * @return the prioritised {@link Date} instance, or {@code null} if no valid date metadata is
     *         found
     */
    default Date extractDate()
    {
        return null;
    }
}