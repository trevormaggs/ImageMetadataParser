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
 * @param <D>
 *        the type of metadata directory handled by the strategy, for example: DirectoryIFD
 * @param <T>
 *        the type of MetadataStrategy this context encapsulates
 */
public class MetadataContext<D, T extends MetadataStrategy<D>>
{
    // The context should know the Directory type (D) for the iterator to be type-safe
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
     * @return true if the strategy has EXIF data, otherwise false
     */
    public boolean hasExifData()
    {
        // No casting needed! Relies on the polymorphic call to the strategy.
        return strategy.hasExifData();
    }

    /**
     * Checks if the encapsulated strategy contains textual metadata.
     *
     * @return true if the strategy has textual data, otherwise false
     */
    public boolean hasTextualData()
    {
        // No casting needed! Relies on the polymorphic call to the strategy.
        return strategy.hasTextualData();
    }

    /**
     * Returns a type-safe iterator for the strategy's Directory type D.
     *
     * @return an Iterator over the metadata directories of type D
     */
    public Iterator<D> iterator()
    {
        // Now returns a type-safe Iterator<D>
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
        // The iterator is now type-safe (Iterator<D>), improving clarity
        Iterator<D> it = this.strategy.iterator();

        while (it.hasNext())
        {
            sb.append(it.next()).append(System.lineSeparator());
        }

        return sb.toString();
    }

    // NOTE: The directory-specific retrieval methods (getDirectory with DirectoryIdentifier,
    // ChunkType.Category, and Taggable) CANNOT be fully genericized because the key types are
    // different. They must remain coupled to the specific strategy types for their arguments to
    // work.
    // The methods below are the *only* ones that should use instanceof/casting.

    // --- Directory-Specific Retrieval Methods ---

    public Optional<DirectoryIFD> getDirectory(DirectoryIdentifier key)
    {
        if (strategy instanceof ExifStrategy)
        {
            return Optional.ofNullable(((ExifStrategy) strategy).getDirectory(key));
        }

        return Optional.empty();
    }

    public Optional<PngDirectory> getDirectory(ChunkType.Category category)
    {
        if (strategy instanceof PngStrategy)
        {
            return Optional.ofNullable(((PngStrategy) strategy).getDirectory(category));
        }

        return Optional.empty();
    }

    public Optional<PngDirectory> getDirectory(Taggable tag)
    {
        if (strategy instanceof PngStrategy)
        {
            return Optional.ofNullable(((PngStrategy) strategy).getDirectory(tag));
        }

        return Optional.empty();
    }

    // REMOVE the specific iterator methods (getExifIterator/getPngIterator)
    // as the generic iterator() is now type-safe and serves both purposes!
}