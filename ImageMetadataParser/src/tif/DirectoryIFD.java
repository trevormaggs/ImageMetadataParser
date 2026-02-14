package tif;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import common.Directory;
import common.MetadataConstants;
import common.RationalNumber;
import tif.DirectoryIFD.EntryIFD;
import tif.tagspecs.Taggable;

/**
 * A collection-based representation of a TIFF Image File Directory (IFD).
 *
 * <p>
 * A {@code DirectoryIFD2} serves as a specialised container for {@link EntryIFD} objects. It
 * provides a high-level API to retrieve metadata in native Java formats, such as {@link Date},
 * {@link String}, or {@link RationalNumber}, while handling the underlying TIFF type conversions
 * automatically.
 * </p>
 *
 * <p>
 * Each instance represents a single directory within the file. While some directories contain pixel
 * data, others may store only metadata structures, such as EXIF or GPS blocks.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.1
 * @since 13 August 2025
 * @see EntryIFD
 */
public class DirectoryIFD implements Directory<EntryIFD>
{
    private final Map<Integer, EntryIFD> entryMap = new LinkedHashMap<>();
    private DirectoryIdentifier directoryType;

    /**
     * Represents a single metadata entry within an Image File Directory.
     *
     * <p>
     * Each {@code EntryIFD} acts as a bridge between the raw TIFF field structure and Java objects.
     * It encapsulates the tag identity, field type, value count, and the resulting parsed data
     * (such as a String, Integer, or RationalNumber).
     * </p>
     *
     * <p>
     * Instances of this class are immutable. Internal byte arrays are defensively copied to ensure
     * data integrity.
     * </p>
     */
    public final static class EntryIFD
    {
        private final Taggable tagEnum;
        private final TifFieldType fieldType;
        private final long count;
        private final long valueOffset;
        private final byte[] value;
        private final Object parsedData;

        /**
         * Constructs an immutable {@code EntryIFD} instance from raw bytes.
         *
         * @param tag
         *        the tag descriptor (Taggable enum)
         * @param ttype
         *        the TIFF field type
         * @param length
         *        the number of values (count)
         * @param offset
         *        the raw offset or immediate value field
         * @param bytes
         *        the raw value bytes; the constructor performs a defensive copy
         * @param byteOrder
         *        the byte order used to parse the bytes
         */
        public EntryIFD(Taggable tag, TifFieldType ttype, long length, long offset, byte[] bytes, ByteOrder byteOrder)
        {
            this.tagEnum = tag;
            this.fieldType = ttype;
            this.count = length;
            this.valueOffset = offset;
            this.value = (bytes != null ? Arrays.copyOf(bytes, bytes.length) : null);
            this.parsedData = fieldType.parse(value, count, byteOrder);
        }

        /**
         * @return the tag enum that identifies this entry
         */
        public Taggable getTag()
        {
            return tagEnum;
        }

        /**
         * @return the numeric ID of the tag
         */
        public int getTagID()
        {
            return tagEnum.getNumberID();
        }

        /**
         * @return the TIFF field type for this entry
         */
        public TifFieldType getFieldType()
        {
            return fieldType;
        }

        /**
         * @return the number of values represented by this entry
         */
        public long getCount()
        {
            return count;
        }

        /**
         * @return the absolute file offset or the immediate value for this entry
         */
        public long getOffset()
        {
            return valueOffset;
        }

        /**
         * @return a defensive copy of the raw byte array, or null if not set
         */

        public byte[] getByteArray()
        {
            return (value != null ? Arrays.copyOf(value, value.length) : null);
        }

        /**
         * @return the total byte length of the data based on type and count
         */
        public long getByteLength()
        {
            return count * fieldType.getFieldSize();
        }

        /**
         * @return the parsed data object, or null if no value is available
         */
        public Object getData()
        {
            return parsedData;
        }

        /**
         * @return true if the parsed data is an array type
         */
        public boolean isArray()
        {
            return (parsedData != null && parsedData.getClass().isArray());
        }

        /**
         * Generates a human-readable summary of the entry, suitable for logging or metadata
         * inspection. Includes the tag name, numeric ID, field type, and a formatted representation
         * of the value.
         *
         * @return a multi-line formatted string
         */
        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();

            // Tag, Type, and Count Information
            sb.append(String.format(MetadataConstants.FORMATTER, "Tag Name", getTag() + " (Tag ID: " + String.format("0x%04X", getTagID()) + ")"));
            sb.append(String.format(MetadataConstants.FORMATTER, "Field Type", getFieldType() + " (count: " + getCount() + ")"));
            sb.append(String.format(MetadataConstants.FORMATTER, "Value", TagValueConverter.toStringValue(this)));
            sb.append(String.format(MetadataConstants.FORMATTER, "Hint", getTag().getHint()));

            if (getByteLength() > IFDHandler.ENTRY_MAX_VALUE_LENGTH)
            {
                sb.append(String.format(MetadataConstants.FORMATTER, "Jump Offset", String.format("0x%04X", valueOffset)));
            }

            return sb.toString();
        }
    }

    /**
     * Constructs a new directory instance for a specific directory type.
     * 
     * @param dirType
     *        the directory type identifier, for example: IFD0, EXIF, etc
     */
    public DirectoryIFD(DirectoryIdentifier dirType)
    {
        this.directoryType = dirType;
    }

    /**
     * Adds a new {@code EntryIFD} entry to the collection.
     *
     * @param entry
     *        {@code EntryIFD} object
     */
    @Override
    public void add(EntryIFD entry)
    {
        entryMap.put(entry.getTagID(), entry);
    }

    /**
     * Removes a {@code EntryIFD} entry from this directory.
     *
     * @param entry
     *        {@code EntryIFD} object to remove
     */
    @Override
    public boolean remove(EntryIFD entry)
    {
        return entryMap.remove(entry.getTagID(), entry);
    }

    /**
     * Checks if the specified {@code EntryIFD} entry has been added to this directory.
     *
     * @param entry
     *        {@code EntryIFD} object to check for
     * @return true if the specified entry is contained in the map
     */
    @Override
    public boolean contains(EntryIFD entry)
    {
        return entryMap.containsValue(entry);
    }

    /**
     * Returns the count of IFD entries present in this directory.
     *
     * @return the total number of entries
     */
    @Override
    public int size()
    {
        return entryMap.size();
    }

    /**
     * Returns true if the entry map is empty.
     *
     * @return true if empty, otherwise false
     */
    @Override
    public boolean isEmpty()
    {
        return entryMap.isEmpty();
    }

    /**
     * Retrieves an iterator to navigate through a collection of {@code EntryIFD} objects.
     *
     * @return an Iterator object
     */
    @Override
    public Iterator<EntryIFD> iterator()
    {
        return entryMap.values().iterator();
    }

    /**
     * Updates the directory type identifier. Used when a directory is promoted, for example: from
     * ROOT to IFD1, during the parsing phase.
     * 
     * @param dirType
     *        the new directory type
     */
    public void setDirectoryType(DirectoryIdentifier dirType)
    {
        directoryType = dirType;
    }

    /**
     * Gets the current Image File Directory type.
     *
     * @return an enumeration value of DirectoryIdentifier
     */
    public DirectoryIdentifier getDirectoryType()
    {
        return directoryType;
    }

    /**
     * Checks if the specified tag is present in this directory.
     *
     * @param tag
     *        the enumeration tag to look for
     * @return true if an entry for the specified tag exists in this directory, otherwise false
     */
    public boolean hasTag(Taggable tag)
    {
        return entryMap.containsKey(tag.getNumberID());
    }

    /**
     * Returns a copy of the raw bytes associated with a tag.
     *
     * <p>
     * Use this method for tags containing "black box" data, such as embedded XMP packets, ICC
     * profiles, or private maker notes.
     * </p>
     *
     * @param tag
     *        the tag to obtain raw bytes for
     * @return a raw byte array, or an empty array if the tag is missing
     */
    public byte[] getRawByteArray(Taggable tag)
    {
        EntryIFD entry = entryMap.get(tag.getNumberID());

        if (entry == null)
        {
            return new byte[0];
        }

        return entry.getByteArray();
    }

    /**
     * Determines if the specified tag's value can be converted to a 32-bit signed integer without
     * loss of precision.
     *
     * @param tag
     *        the tag to check
     * @return true if the tag exists and is convertible without loss of precision, otherwise false
     */
    public boolean isConvertibleToInt(Taggable tag)
    {
        EntryIFD entry = findEntryByTag(tag);

        return (entry == null ? false : TagValueConverter.canConvertToInt(entry.getFieldType()));
    }

    /**
     * Returns the integer value associated with the specified tag.
     *
     * <p>
     * If the tag is missing or if the entry is not convertible to an int, this method throws an
     * exception, since numeric values are considered required when calling this method.
     * </p>
     *
     * @param tag
     *        the enumeration tag to retrieve
     * @return the tag's value as an int
     *
     * @throws IllegalArgumentException
     *         if the tag is missing or the entry's value is not convertible (i.e. unsigned LONG or
     *         RATIONAL) to a Java 32-bit int safely and losslessly
     */
    public int getIntValue(Taggable tag)
    {
        EntryIFD entry = findEntryByTag(tag);

        if (entry == null)
        {
            throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), getDirectoryType().getDescription()));
        }

        if (!TagValueConverter.canConvertToInt(entry.getFieldType()))
        {
            String msg = String.format("Tag [%s (0x%04X)] has incompatible field type [%s] for safe lossless conversion to integer in directory [%s]",
                    tag, tag.getNumberID(), entry.getFieldType(), getDirectoryType().getDescription());

            throw new IllegalArgumentException(msg);
        }

        return TagValueConverter.getIntValue(entry);
    }

    /**
     * Returns the long value associated with the specified tag.
     *
     * @param tag
     *        the enumeration tag to retrieve
     * @return the tag's value as a long
     *
     * @throws IllegalArgumentException
     *         if the tag is missing or not numeric
     */
    public long getLongValue(Taggable tag)
    {
        EntryIFD entry = findEntryByTag(tag);

        if (entry == null)
        {
            throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), getDirectoryType().getDescription()));
        }

        return TagValueConverter.getLongValue(entry);
    }

    /**
     * Returns the float value associated with the specified tag.
     *
     * @param tag
     *        the enumeration tag to retrieve
     * @return the tag's value as a float
     *
     * @throws IllegalArgumentException
     *         if the tag is missing or not numeric
     */
    public float getFloatValue(Taggable tag)
    {
        EntryIFD entry = findEntryByTag(tag);

        if (entry == null)
        {
            throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), getDirectoryType().getDescription()));
        }

        return TagValueConverter.getFloatValue(entry);
    }

    /**
     * Returns the double value associated with the specified tag.
     *
     * @param tag
     *        the enumeration tag to retrieve
     * @return the tag's value as a double
     *
     * @throws IllegalArgumentException
     *         if the tag is missing or not numeric
     */
    public double getDoubleValue(Taggable tag)
    {
        EntryIFD entry = findEntryByTag(tag);

        if (entry == null)
        {
            throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), getDirectoryType().getDescription()));
        }

        return TagValueConverter.getDoubleValue(entry);
    }

    /**
     * Returns the rational number value associated with the specified tag.
     *
     * @param tag
     *        the enumeration tag to fetch
     * @return the tag's rational value
     *
     * @throws IllegalArgumentException
     *         if the tag is missing or the entry's data is not an instance of RationalNumber
     */
    public RationalNumber getRationalValue(Taggable tag)
    {
        EntryIFD entry = findEntryByTag(tag);

        if (entry != null)
        {
            Object obj = entry.getData();

            if (obj instanceof RationalNumber)
            {
                return (RationalNumber) obj;
            }

            else if (obj != null)
            {
                throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] found but data type is [%s], expected RationalNumber in directory [%s]",
                        tag, tag.getNumberID(), obj.getClass().getName(), getDirectoryType().getDescription()));
            }
        }

        throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), getDirectoryType().getDescription()));
    }

    /**
     * Returns the string value associated with the specified tag.
     *
     * @param tag
     *        the enumeration tag to obtain the value for
     * @return a string representing the tag's value
     *
     * @throws IllegalArgumentException
     *         if the tag is missing or information cannot be obtained
     */
    public String getString(Taggable tag)
    {
        EntryIFD entry = findEntryByTag(tag);

        if (entry == null)
        {
            throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), getDirectoryType().getDescription()));
        }

        return TagValueConverter.toStringValue(entry);
    }

    /**
     * Returns a Date object associated with the specified tag, delegating parsing and validation to
     * the {@code TagValueConverter} utility.
     *
     * @param tag
     *        the enumeration tag to obtain the value for
     * @return a Date object if present and successfully parsed
     *
     * @throws IllegalArgumentException
     *         if the tag is missing or its value cannot be parsed as a valid Date
     */
    public Date getDate(Taggable tag)
    {
        EntryIFD entry = findEntryByTag(tag);

        if (entry == null)
        {
            throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), getDirectoryType().getDescription()));
        }

        return TagValueConverter.getDate(entry);
    }

    /**
     * Retrieves the {@code EntryIFD} associated with the specified tag.
     *
     * <p>
     * This method provides access to the raw directory entry, including its type, count, and
     * offset/value data.
     * </p>
     *
     * @param tag
     *        the enumeration tag to look for
     * @return the matched {@code EntryIFD} resource, or {@code null} if the tag is not present in
     *         this directory
     */
    public EntryIFD getTagEntry(Taggable tag)
    {
        return entryMap.get(tag.getNumberID());
    }

    /**
     * Generates a formatted string showing current values of every IFD entry in the collection.
     *
     * @return a comprehensive, string-based representation of each IFD entry
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("Directory Type - ")
                .append(getDirectoryType().getDescription())
                .append(String.format(" (%d entries)%n", size()))
                .append(MetadataConstants.DIVIDER)
                .append(System.lineSeparator());

        for (EntryIFD entry : this)
        {
            sb.append(entry);
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    /**
     * Finds an IFD entry corresponding to the specified tag identifier.
     *
     * @param tag
     *        the tag to resolve
     * @return a {@link EntryIFD} resource, or null if not found
     */
    private EntryIFD findEntryByTag(Taggable tag)
    {
        return entryMap.get(tag.getNumberID());
    }
}