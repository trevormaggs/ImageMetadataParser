package common;

/**
 * This is the base interface to facilitate the strategy design pattern to support various
 * strategies involving the management of image metadata entries.
 */
public interface MetadataStrategy2<T> extends Iterable<T>
{
    /**
     * Adds a metadata directory to the collection.
     * 
     * @param directory
     *        the directory to add
     */
    public void addDirectory(T directory);

    /**
     * Removes a metadata directory from the collection.
     * 
     * @param directory
     *        the directory to remove
     * 
     * @return true if the directory was successfully removed, otherwise false
     */
    public boolean removeDirectory(T directory);

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
}