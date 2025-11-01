package common;

import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import png.ChunkType;
import png.PngDirectory;
import png.PngMetadata;
import png.PngStrategy;
import tif.DirectoryIFD;
import tif.DirectoryIdentifier;
import tif.ExifMetadata;
import tif.ExifStrategy;
import tif.tagspecs.Taggable;

/**
 * A context class that acts as a wrapper for a {@link MetadataStrategy}, providing a simplified
 * interface for clients to interact with various types of metadata. It decouples the client from
 * the specific implementation of the metadata strategy, promoting flexibility and re-usability.
 * 
 * <p>
 * This class uses generics to ensure type safety while allowing for different concrete strategies,
 * for example: {@link ExifMetadata}, {@link PngMetadata}) to be used interchangeably.
 * </p>
 *
 * @param <T>
 *        the type of {@link MetadataStrategy} this context encapsulates
 */
public class MetadataContext2<T extends MetadataStrategy<?>>
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
    public MetadataContext2(T strategy)
    {
        if (strategy == null)
        {
            throw new NullPointerException("Strategy cannot be null");
        }

        this.strategy = strategy;
    }

    /**
     * Checks if the encapsulated strategy contains any metadata.
     *
     * @return true if metadata is present, otherwise false
     */
    public boolean containsMetadata()
    {
        return strategy.hasMetadata();
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
     * Checks if the encapsulated strategy contains EXIF metadata.
     * 
     * <p>
     * This method performs a check based on the specific type of strategy held by the context.
     * </p>
     *
     * @return true if the strategy has EXIF data, otherwise false
     */
    public boolean hasExifData()
    {
        if (strategy instanceof ExifStrategy)
        {
            return ((ExifStrategy) strategy).hasExifData();
        }

        else if (strategy instanceof PngStrategy)
        {
            return ((PngStrategy) strategy).hasExifData();
        }

        return false;
    }

    /**
     * Checks if the encapsulated strategy contains textual metadata.
     * 
     * <p>
     * This check is specific to {@link PngStrategy} as other types may not support textual data.
     * </p>
     *
     * @return true if the strategy has textual data, otherwise false
     */
    public boolean hasTextualData()
    {
        if (strategy instanceof PngStrategy)
        {
            return ((PngStrategy) strategy).hasTextualData();
        }

        return false;
    }

    /**
     * Returns a type-safe iterator for {@link DirectoryIFD} instances if the encapsulated strategy
     * is an {@link ExifStrategy}.
     *
     * @return an iterator of DirectoryIFD instances, otherwise an empty iterator
     */
    public Iterator<DirectoryIFD> getExifIterator()
    {
        if (strategy instanceof ExifStrategy)
        {
            return ((ExifStrategy) strategy).iterator();
        }

        return Collections.<DirectoryIFD> emptyIterator();
    }

    /**
     * Returns a type-safe iterator for {@link PngDirectory} instances if the encapsulated strategy
     * is a {@link PngStrategy}.
     *
     * @return an iterator of PngDirectory instances, otherwise an empty iterator
     */
    public Iterator<PngDirectory> getPngIterator()
    {
        if (strategy instanceof PngStrategy)
        {
            return ((PngStrategy) strategy).iterator();
        }

        return Collections.<PngDirectory> emptyIterator();
    }

    /**
     * Returns a specific {@link DirectoryIFD} wrapped in an {@link Optional} if the encapsulated
     * strategy is an {@link ExifStrategy} and the directory is found.
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
     * strategy is a {@link PngStrategy} and the directory is found.
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
     * strategy is a {@link PngStrategy} and the directory is found using a {@link Taggable}
     * key.
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

    /**
     * Generates a string representation of all metadata entries by iterating through the
     * encapsulated strategy's directories.
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
     * Returns a generic iterator over the directories contained within the encapsulated strategy.
     *
     * @return an Iterator over the metadata directories
     */
    public Iterator<?> iterator()
    {
        return strategy.iterator();
    }
}