package common;

import java.util.Date;
import java.util.Iterator;

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
public final class MetadataContext<T extends MetadataStrategy<?>>
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
     * Returns the current encapsulated strategy derived from {@link MetadataStrategy} type.
     *
     * @return the currently encapsulated strategy of type T
     */
    public T getMetadataStrategy()
    {
        return strategy;
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
     * Checks if the encapsulated strategy contains EXIF metadata. Note, this method relies on the
     * polymorphic call to the strategy.
     *
     * @return true if the strategy has EXIF data, otherwise false
     */
    public boolean hasExifData()
    {
        return strategy.hasExifData();
    }

    /**
     * Checks if the encapsulated strategy contains textual metadata. Note, this method relies on
     * the polymorphic call to the strategy.
     *
     * @return true if the strategy has textual data, otherwise false
     */
    public boolean hasTextualData()
    {
        return strategy.hasTextualData();
    }

    public Date extractDate()
    {
        return strategy.extractDate();
    }

    /**
     * Returns an iterator over the metadata directories.
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
}