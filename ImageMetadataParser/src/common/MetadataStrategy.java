package common;

import java.nio.ByteOrder;
import java.util.Date;

/**
 * This is the base interface to facilitate the strategy design pattern to support various
 * strategies involving the management of image metadata entries.
 *
 * @param <D>
 *        the type of Directory, i.e. PngDirectory or DirectoryIFD, handled by this strategy
 */
public interface MetadataStrategy<D extends Directory<?>> extends Iterable<D>
{
    /**
     * Adds a metadata directory to the collection.
     *
     * @param directory
     *        the directory to add
     */
    public void addDirectory(D directory);

    /**
     * Removes a metadata directory from the collection.
     *
     * @param directory
     *        the directory to remove
     * @return true if the directory was successfully removed, otherwise false
     */
    public boolean removeDirectory(D directory);

    /**
     * Checks if the metadata collection is empty.
     *
     * @return true if the collection is empty, otherwise false
     */
    public boolean isEmpty();

    /**
     * Checks if the metadata collection contains any metadata.
     *
     * @return true if metadata is present, otherwise false
     */
    public boolean hasMetadata();

    /**
     * Returns the byte order, indicating how data values will be interpreted correctly.
     *
     * @return either {@link java.nio.ByteOrder#BIG_ENDIAN} or
     *         {@link java.nio.ByteOrder#LITTLE_ENDIAN}
     */
    public ByteOrder getByteOrder();

    /**
     * Checks if the strategy contains EXIF metadata. Default implementation returns false for
     * strategies that don't support it.
     * 
     * @return the true if EXIF metadata is present, otherwise false
     */
    default boolean hasExifData()
    {
        return false;
    }

    /**
     * Checks if the strategy contains textual metadata, for example: PNG tEXt/iTXt chunks etc.
     * Default implementation returns false for strategies that don't support it.
     * 
     * @return the true if Textual information is present, otherwise false
     */
    default boolean hasTextualData()
    {
        return false;
    }

    /**
     * Checks if the strategy contains XMP metadata. Default implementation returns false for
     * strategies that don't support it.
     * 
     * @return the true if XMP metadata is present, otherwise false
     */
    default boolean hasXmpData()
    {
        return false;
    }

    /**
     * Scans all available metadata segments and attempts to extract the best available
     * {@code creation/capture date} instance, prioritising segments based on a defined hierarchy,
     * for example: EXIF:DateTimeOriginal, XMP:CreateDate, TIFF:DateTime, etc.
     * 
     * <p>
     * This method abstracts the complexity of searching across different formats, such as EXIF,
     * XMP, or Textual chunks within PNG files to provide a single, prioritised date.
     * </p>
     *
     * @return the prioritised date instance, or null if no supported date metadata is found
     */
    default Date extractDate()
    {
        return null;
    }
}