package common;

import java.util.Iterator;
import java.util.Optional;
import png.ChunkType;
import png.PngDirectory;
import png.PngStrategy;
import tif.DirectoryIFD;
import tif.DirectoryIdentifier;
import tif.ExifStrategy;
import tif.tagspecs.Taggable;

/**
 * A context class that acts as a wrapper for a {@link MetadataStrategy}, providing a simplified
 * interface for clients to interact with various types of metadata. It decouples the client from
 * the specific implementation of the metadata strategy, promoting flexibility and re-usability.
 *
 * <p>
 * This class uses generics to ensure type safety.
 * </p>
 *
 * @param <T>
 *        the type of MetadataStrategy, which this context encapsulates
 */
public class MetadataContext<T extends MetadataStrategy<?>>
{
    private final T strategy;

    /**
     * Constructs a new {@code MetadataContext} with the specified strategy.
     *
     * @param strategy
     *        the concrete metadata strategy to be used
     *
     * @throws NullPointerException
     *         if the specified strategy is null
     */
    public MetadataContext(T strategy)
    {
        if (strategy == null)
        {
            throw new NullPointerException("Strategy cannot be null");
        }

        this.strategy = strategy;
    }

    /**
     * Checks if the encapsulated strategy's metadata is empty.
     *
     * @return true if the metadata collection is empty, otherwise false
     */
    public boolean metadataIsEmpty()
    {
        return strategy.isEmpty();
    }

    /**
     * Checks if the encapsulated strategy contains EXIF metadata. Note, this methods relies on the
     * poly-morphic call to the strategy.
     *
     * @return true if the strategy has EXIF data, otherwise false
     */
    public boolean hasExifData()
    {
        return strategy.hasExifData();
    }

    /**
     * Checks if the encapsulated strategy contains textual metadata. Note, this methods relies on
     * the poly-morphic call to the strategy.
     *
     * @return true if the strategy has textual data, otherwise false
     */
    public boolean hasTextualData()
    {
        return strategy.hasTextualData();
    }

    /**
     * Returns a type-safe iterator for the strategy's Directory type.
     *
     * @return an Iterator over the metadata directories
     */
    public Iterator<?> iterator()
    {
        return strategy.iterator();
    }

    /**
     * Generates a string representation of all metadata entries.
     *
     * @return a string containing the string representation of each directory
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = this.strategy.iterator();

        while (it.hasNext())
        {
            sb.append(it.next()).append(System.lineSeparator());
        }

        return sb.toString();
    }

    /**
     * Returns a specific {@link DirectoryIFD} wrapped in an {@link Optional} if the encapsulated
     * strategy is an {@link ExifStrategy} type and the directory is found.
     *
     * @param key
     *        the DirectoryIdentifier key to search for
     * 
     * @return an Optional containing the DirectoryIFD if found, otherwise, an empty Optional
     */
    public Optional<DirectoryIFD> getDirectory(DirectoryIdentifier key)
    {
        if (strategy instanceof ExifStrategy)
        {
            return Optional.ofNullable(((ExifStrategy) strategy).getDirectory(key));
        }

        return Optional.empty();
    }

    /**
     * Returns a specific {@link PngDirectory} wrapped in an {@link Optional} if the encapsulated
     * strategy is a {@link PngStrategy} type and the directory is found.
     *
     * @param category
     *        the ChunkType.Category to search for
     * 
     * @return an Optional containing the PngDirectory if found, otherwise, an empty Optional
     */
    public Optional<PngDirectory> getDirectory(ChunkType.Category category)
    {
        if (strategy instanceof PngStrategy)
        {
            return Optional.ofNullable(((PngStrategy) strategy).getDirectory(category));
        }

        return Optional.empty();
    }

    /**
     * Returns a specific {@link PngDirectory} wrapped in an {@link Optional} if the encapsulated
     * strategy is a {@link PngStrategy} type and the directory is matched with the {@link Taggable}
     * identifier.
     *
     * @param tag
     *        the Taggable key to search for
     * @return an Optional containing the PngDirectory if found, otherwise, an empty Optional
     */
    public Optional<PngDirectory> getDirectory(Taggable tag)
    {
        if (strategy instanceof PngStrategy)
        {
            return Optional.ofNullable(((PngStrategy) strategy).getDirectory(tag));
        }

        return Optional.empty();
    }
}