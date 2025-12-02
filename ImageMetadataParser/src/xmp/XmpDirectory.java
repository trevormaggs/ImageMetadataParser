package xmp;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import common.Directory;
import common.MetadataConstants;
import xmp.XmpDirectory.XmpRecord;

/**
 * Creates an XMP directory to encapsulate a collection of {@link XmpRecord} properties to manage
 * XMP data.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 10 November 2025
 */
public final class XmpDirectory implements Directory<XmpRecord>
{
    private final Map<String, XmpRecord> propertyMap;

    /**
     * Represents a single, immutable XMP property record.
     *
     * Each {@code XmpRecord} encapsulates the namespace URI, cleaned property path, and the
     * property value. It is immutable and self-contained.
     *
     * @author Trevor Maggs
     * @since 10 November 2025
     */
    public final static class XmpRecord
    {
        private static final String REGEX_PATH = "^\\s*(\\w+):(.+)$";
        private final String namespace;
        private final String path;
        private final String value;
        private final String prefix;
        private final String name;

        /**
         * Constructs an immutable {@code XmpRecord} instance to hold a single record.
         *
         * @param namespace
         *        the namespace URI of the property
         * @param path
         *        the path of the property (e.g., dc:creator)
         * @param value
         *        the value of the property
         */
        public XmpRecord(String namespace, String path, String value)
        {
            this.namespace = namespace;
            this.path = path;
            this.value = value;
            this.prefix = path.matches(REGEX_PATH) ? path.replaceAll(REGEX_PATH, "$1") : "";
            this.name = path.matches(REGEX_PATH) ? path.replaceAll(REGEX_PATH, "$2") : path;
        }

        /**
         * @return the namespace URI of the property
         */
        public String getNamespace()
        {
            return namespace;
        }

        /**
         * @return the path of the property
         */
        public String getPath()
        {
            return path;
        }

        /**
         * @return the short identifier of the path
         */
        public String getPrefix()
        {
            return prefix;
        }

        /**
         * @return the property name of the path
         */
        public String getName()
        {
            return name;
        }

        /**
         * @return the value of the property
         */
        public String getValue()
        {
            return value;
        }

        /**
         * Returns a string representation of this {@link XmpRecord} object.
         *
         * @return formatted string describing the entry’s key characteristics
         */
        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();

            sb.append(String.format(MetadataConstants.FORMATTER, "Namespace", getNamespace()));
            sb.append(String.format(MetadataConstants.FORMATTER, "Prefix", getPrefix()));
            sb.append(String.format(MetadataConstants.FORMATTER, "Name", getName()));
            sb.append(String.format(MetadataConstants.FORMATTER, "Full Path", getPath()));
            sb.append(String.format(MetadataConstants.FORMATTER, "Value", getValue()));
            sb.append(System.lineSeparator());

            return sb.toString();
        }

    }

    /**
     * Constructs a new {@code XmpDirectory} to manage a collection of {@link XmpRecord}
     * properties.
     */
    public XmpDirectory()
    {
        this.propertyMap = new LinkedHashMap<>();
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
        XmpRecord xmp = propertyMap.get(prop.getQualifiedPath());

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
        propertyMap.put(prop.getPath(), prop);
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

        return (propertyMap.remove(prop.getPath()) != null);
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
        return propertyMap.containsKey(prop.getPath());
    }

    /**
     * Returns the number of {@link XmpRecord} objects in this directory.
     *
     * @return the size of the directory
     */
    @Override
    public int size()
    {
        return propertyMap.size();
    }

    /**
     * Checks if this directory contains at least one {@link XmpRecord} object.
     *
     * @return true if this directory is empty, otherwise false
     */
    @Override
    public boolean isEmpty()
    {
        return propertyMap.isEmpty();
    }

    /**
     * Returns an iterator over the extracted XMP properties. The properties are returned in the
     * order they appeared in the original XMP payload.
     * 
     * @return an iterator of {@link XmpRecord} objects
     */
    @Override
    public Iterator<XmpRecord> iterator()
    {
        return propertyMap.values().iterator();
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

        for (XmpRecord record : propertyMap.values())
        {
            sb.append(record).append(System.lineSeparator());
        }

        return sb.toString();
    }
}