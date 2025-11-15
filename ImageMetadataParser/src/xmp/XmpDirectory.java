package xmp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import common.Directory;
import xmp.XmpHandler.XmpRecord;

/**
 * Encapsulates a collection of {@link XmpRecord} objects to manage XMP data.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 10 November 2025
 */
public class XmpDirectory implements Directory<XmpRecord>
{
    private final Map<String, XmpRecord> propMap;

    /**
     * Constructs a new {@code XmpDirectory} to manage a collection of {@link XmpRecord}
     * properties.
     */
    public XmpDirectory()
    {
        this.propMap = new HashMap<>();
    }

    /**
     * Retrieves the value in a string form corresponding to the specified property name.
     *
     * @param prop
     *        an XmpProperty instance having a fully qualified path
     * @return an Optional containing the value that is found, or Optional#empty() if none is found
     */
    public Optional<String> getValueByPath(XmpProperty prop)
    {
        XmpRecord xmp = propMap.get(prop.getQualifiedPath());

        if (xmp != null)
        {
            return Optional.of(xmp.getValue());
        }

        return Optional.empty();
    }

    /**
     * Adds a single {@link XmpRecord} to this directory.
     *
     * @param prop
     *        the XmpRecord to be added
     */
    @Override
    public void add(XmpRecord prop)
    {
        propMap.put(prop.getPath(), prop);
    }

    /**
     * Removes a {@code XmpRecord} property from this directory.
     *
     * @param prop
     *        {@code XmpRecord} object to remove
     */
    @Override
    public boolean remove(XmpRecord prop)
    {
        if (prop == null)
        {
            throw new NullPointerException("Property cannot be null");
        }

        return (propMap.remove(prop.getPath()) != null);
    }

    /**
     * Checks if a specific {@link XmpRecord} property is present in this directory.
     *
     * @param prop
     *        the XmpRecord to check for
     * @return true if the property is found, otherwise false
     */
    @Override
    public boolean contains(XmpRecord prop)
    {
        return propMap.containsKey(prop.getPath());
    }

    /**
     * Returns the number of {@link XmpRecord} objects in this directory.
     *
     * @return the size of the directory
     */
    @Override
    public int size()
    {
        return propMap.size();
    }

    /**
     * Checks if this directory contains at least one {@link XmpRecord} object.
     *
     * @return true if this directory is empty, otherwise false
     */
    @Override
    public boolean isEmpty()
    {
        return propMap.isEmpty();
    }

    /**
     * Returns an iterator over the {@link XmpRecord} objects defined in this class.
     *
     * @return an {@link Iterator} instance
     */
    @Override
    public Iterator<XmpRecord> iterator()
    {
        return propMap.values().iterator();
    }

    /**
     * Returns a string representation of this directory, which is the concatenation of the string
     * representations of all contained {@link XmpRecord} objects, each on a new line.
     *
     * @return a multi-line string representing the properties in the directory
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        for (XmpRecord prop : propMap.values())
        {
            sb.append(prop).append(System.lineSeparator());
        }

        return sb.toString();
    }
}