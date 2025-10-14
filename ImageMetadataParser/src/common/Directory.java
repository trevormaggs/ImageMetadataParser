package common;

/**
 * Represents a generic collection or directory of entries of a specific type. A Directory provides
 * basic operations for adding, checking for presence, and querying the size and emptiness of the
 * collection.
 * 
 * It also extends {@code Iterable}, allowing it to be used in enhanced for-loops.
 *
 * @param <T>
 *        the type of elements in this directory.
 * 
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 October 2025
 */
public interface Directory<T> extends Iterable<T>
{
    /**
     * Adds the specified entry to this directory. The behaviour regarding duplicate entries depends
     * on the implementing class.
     *
     * @param entry
     *        the entry to be added
     */
    public void add(T entry);

    /**
     * Returns {@code true} if this directory contains the specified entry.
     *
     * @param entry
     *        the entry whose presence in this directory is to be tested
     * @return true if this directory contains the specified entry
     */
    public boolean contains(T entry);

    /**
     * Returns the number of entries in this directory.
     *
     * @return the number of entries
     */
    public int size();

    /**
     * Returns {@code true} if this directory contains no entries.
     *
     * @return true if there are no entries
     */
    public boolean isEmpty();
}