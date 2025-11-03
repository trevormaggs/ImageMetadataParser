package tif;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import common.DateParser;
import common.Directory;
import common.RationalNumber;
import logger.LogFactory;
import tif.DirectoryIFD.EntryIFD;
import tif.tagspecs.Taggable;

/**
 * Represents an Image File Directory (IFD) in a TIFF file, conforming to the TIFF 6.0
 * specification. An IFD serves as a container for image-related data and metadata, composed of a
 * series of tag entries that store various attributes.
 *
 * <p>
 * Each IFD can represent an image or a metadata structure associated with an image. Although not
 * all directories contain pixel data, every image is described by at least one IFD.
 * </p>
 *
 * <p>
 * This implementation focuses on basic access to EXIF and standard IFD metadata entries, providing
 * convenient methods for retrieving values in common data formats.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 * @see EntryIFD
 */
public class DirectoryIFD implements Directory<EntryIFD>
{
    private static final LogFactory LOGGER = LogFactory.getLogger(DirectoryIFD.class);
    private final DirectoryIdentifier directoryType;
    private final ByteOrder headerByteOrder;
    private final Map<Integer, EntryIFD> entryMap;

    /**
     * Represents a single Image File Directory (IFD) entry within a TIFF structure.
     *
     * Each {@code EntryIFD} encapsulates metadata such as tag ID, data type, count, raw bytes, and
     * a parsed object representation. It is immutable and self-contained.
     *
     *
     * @author Trevor Maggs
     * @since 21 June 2025
     */
    public final static class EntryIFD
    {
        private final Taggable tagEnum;
        private final TifFieldType fieldType;
        private final int count;
        private final int valueOffset;
        private final byte[] value;
        private final Object parsedData;

        /**
         * Constructs an immutable {@code EntryIFD} instance from raw bytes.
         *
         * @param tag
         *        the tag descriptor
         * @param ttype
         *        the TIFF field type
         * @param length
         *        the number of values
         * @param offset
         *        the raw offset/value field
         * @param bytes
         *        the value bytes (may be null)
         * @param byteOrder
         *        the byte order used to interpret binary values
         */
        public EntryIFD(Taggable tag, TifFieldType ttype, int length, int offset, byte[] bytes, ByteOrder byteOrder)
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
         * @return the offset or immediate value field for this entry
         */
        public int getOffset()
        {
            return valueOffset;
        }

        /**
         * Returns a copy of this entry's original raw bytes.
         *
         * @return a copy of the byte array, or null if not set
         */
        public byte[] getByteArray()
        {
            return (value != null ? Arrays.copyOf(value, value.length) : null);
        }

        /**
         * @return the total byte length of the data, based on field type and count
         */
        public int getByteLength()
        {
            return count * fieldType.getElementLength();
        }

        /**
         * Returns the parsed data object representing this entry’s value.
         *
         * @return the parsed data, or null if no value is available
         */
        public Object getData()
        {
            return parsedData;
        }

        /**
         * @return formatted string describing the entry’s key characteristics
         */
        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();

            sb.append(String.format("  %-20s 0x%04X (%d)%n", "[Tag ID]", getTagID(), getTagID()));
            sb.append(String.format("  %-20s %s%n", "[Tag Name]", getTag()));
            sb.append(String.format("  %-20s %s%n", "[Field Type]", getFieldType()));
            sb.append(String.format("  %-20s %s%n", "[Count]", getCount()));
            sb.append(String.format("  %-20s %s%n", "[Value]", TagValueConverter.toStringValue(this)));

            if (getByteLength() > IFDHandler.ENTRY_MAX_VALUE_LENGTH)
            {
                sb.append(String.format("  %-20s 0x%04X%n", "[Jump Offset]", valueOffset));
            }

            return sb.toString();
        }
    }

    /**
     * Constructs a new directory instance to manage a collection of IFD entries embedded within the
     * TIFF image file.
     *
     * @param dirType
     *        a directory type defined in the {@link DirectoryIdentifier} enumeration class
     * @param order
     *        the byte order, either {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     */
    public DirectoryIFD(DirectoryIdentifier dirType, ByteOrder order)
    {
        this.entryMap = new LinkedHashMap<>();
        this.directoryType = dirType;
        this.headerByteOrder = order;

        LOGGER.debug("New directory [" + directoryType + "] added");
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
     * Adds a new field to the collection of IFD entries identified within the TIFF image file.
     *
     * @param tag
     *        the tag descriptor
     * @param ttype
     *        the TIFF field type
     * @param length
     *        the number of values
     * @param offset
     *        the raw offset/value field
     * @param bytes
     *        the value bytes (may be null)
     */
    public void addEntry(Taggable tag, TifFieldType ttype, int length, int offset, byte[] bytes)
    {
        add(new EntryIFD(tag, ttype, length, offset, bytes, headerByteOrder));
    }

    /**
     * Checks if the specified tag has been set in this directory.
     *
     * @param tag
     *        the enumeration tag to check for
     * @return true if the specified tag is contained in this directory
     */
    public boolean containsTag(Taggable tag)
    {
        return entryMap.containsKey(tag.getNumberID());
    }

    /**
     * Checks whether the specified tag holds a value based on a general numerical representation.
     *
     * @param tag
     *        the specific enumeration tag to search for a match
     * @return boolean true if the value held by the tag is numeric, false otherwise
     */
    public boolean isTagNumeric(Taggable tag)
    {
        Optional<EntryIFD> opt = findEntryByID(tag);

        return (opt.isPresent() ? opt.get().getFieldType().isNumber() : false);
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
        Optional<EntryIFD> opt = findEntryByID(tag);

        if (opt.isPresent())
        {
            return TagValueConverter.toStringValue(opt.get());
        }

        throw new IllegalArgumentException(String.format("Entry [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), tag.getDirectoryType().getDescription()));
    }

    /**
     * Returns the integer value associated with the specified tag.
     *
     * <p>
     * If the tag is missing or the entry is not numeric, this method throws an exception, since
     * numeric values are considered required when requested in this form.
     * </p>
     *
     * @param tag
     *        the enumeration tag to retrieve
     * @return the tag's value as an int
     *
     * @throws IllegalArgumentException
     *         if the tag is unknown or not numeric
     */
    public int getIntValue(Taggable tag)
    {
        return getNumericValue(tag).intValue();
    }

    /**
     * Returns the long value associated with the specified tag.
     *
     * @param tag
     *        the enumeration tag to retrieve
     * @return the tag's value as a long
     *
     * @throws IllegalArgumentException
     *         if the tag is unknown or not numeric
     */
    public long getLongValue(Taggable tag)
    {
        return getNumericValue(tag).longValue();
    }

    /**
     * Returns the float value associated with the specified tag.
     *
     * @param tag
     *        the enumeration tag to retrieve
     * @return the tag's value as a float
     *
     * @throws IllegalArgumentException
     *         if the tag is unknown or not numeric
     */
    public float getFloatValue(Taggable tag)
    {
        return getNumericValue(tag).floatValue();
    }

    /**
     * Returns the double value associated with the specified tag.
     *
     * @param tag
     *        the enumeration tag to retrieve
     * @return the tag's value as a double
     *
     * @throws IllegalArgumentException
     *         if the tag is unknown or not numeric
     */
    public double getDoubleValue(Taggable tag)
    {
        return getNumericValue(tag).doubleValue();
    }

    /**
     * Returns the rational number value associated with the specified tag.
     *
     * @param tag
     *        the enumeration tag to fetch
     * @return the tag's rational value
     *
     * @throws IllegalArgumentException
     *         if the tag is either not a Rational Number or not set in the directory
     */
    public RationalNumber getRationalValue(Taggable tag)
    {
        Optional<EntryIFD> opt = findEntryByID(tag);

        if (opt.isPresent())
        {
            Object obj = opt.get().getData();

            if (obj instanceof RationalNumber)
            {
                return (RationalNumber) obj;
            }

            else if (obj != null)
            {
                throw new IllegalArgumentException(String.format("Mismatched entry in tag [%s (0x%04X)]. Not a Rational Number. Found [%s]", tag, tag.getNumberID(), obj.getClass().getName()));
            }
        }

        throw new IllegalArgumentException(String.format("Entry [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), tag.getDirectoryType().getDescription()));
    }

    /**
     * Returns a Date object if the tag is marked as a potential date entry.
     *
     * @param tag
     *        the enumeration tag to obtain the value for
     * @return a Date object if present and valid
     *
     * @throws IllegalArgumentException
     *         if the tag is missing, not a date hint, or cannot be parsed
     */
    public Date getDate(Taggable tag)
    {
        if (tag.getHint() == TagHint.HINT_DATE)
        {
            Optional<EntryIFD> opt = findEntryByID(tag);

            if (opt.isPresent())
            {
                Object data = opt.get().getData();

                if (data instanceof String)
                {
                    Date parsed = DateParser.convertToDate((String) data);

                    if (parsed != null)
                    {
                        return parsed;
                    }
                }
            }
        }

        throw new IllegalArgumentException(String.format("Entry [%s (0x%04X)] is missing or could not be parsed as a valid date in directory [%s]", tag, tag.getNumberID(), tag.getDirectoryType().getDescription()));
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
     * Adds a new {@code EntryIFD} entry to this Directory.
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
     * Removes a {@code EntryIFD} entry from this Directory.
     *
     * @param entry
     *        {@code EntryIFD} object to remove
     */
    @Override
    public boolean remove(EntryIFD entry)
    {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * Returns the count of IFD entries present in this Directory.
     *
     * @return the total number of elements in the Directory
     */
    @Override
    public int size()
    {
        return entryMap.size();
    }

    /**
     * Returns true if the entry map is empty.
     *
     * @return true if the map is empty, otherwise false
     */
    @Override
    public boolean isEmpty()
    {
        return entryMap.isEmpty();
    }

    /**
     * Checks if the specified {@code EntryIFD} entry has been added to this Directory.
     *
     * @param entry
     *        {@code EntryIFD} object
     * @return true if the specified entry is contained in the map
     */
    @Override
    public boolean contains(EntryIFD entry)
    {
        return entryMap.containsValue(entry);
    }

    /**
     * Generates a formatted string showing current values of every IFD entry in the collection.
     *
     * @return a comprehensive, string-based representation of each IFD entry
     */
    @Override
    public String toString()
    {
        StringBuilder output = new StringBuilder();

        for (EntryIFD entry : entryMap.values())
        {
            output.append(String.format("  %-20s %s%n", "[Directory Type]", getDirectoryType().getDescription()));
            output.append(entry);
            output.append(System.lineSeparator());
        }

        return output.toString();
    }

    /**
     * Finds an IFD entry corresponding to the specified tag ID.
     *
     * @param tag
     *        the tag to resolve
     * @return an Optional containing the EntryIFD, or an empty Optional if not found
     */
    private Optional<EntryIFD> findEntryByID(Taggable tag)
    {
        return Optional.ofNullable(entryMap.get(tag.getNumberID()));
    }

    /**
     * Retrieves the value of a tag in numeric form.
     *
     * <p>
     * This method is used internally by numeric accessors. It throws if the tag is missing or not
     * numeric.
     * </p>
     *
     * @param tag
     *        the tag to resolve
     * @return the numeric value as a Number
     *
     * @throws IllegalArgumentException
     *         if the tag is missing or not numeric
     */
    private Number getNumericValue(Taggable tag)
    {
        Optional<EntryIFD> opt = findEntryByID(tag);

        if (opt.isPresent())
        {
            EntryIFD entry = opt.get();

            if (entry.getFieldType().isNumber())
            {
                return TagValueConverter.toNumericValue(entry);
            }
        }

        throw new IllegalArgumentException(String.format("Entry [%s (0x%04X)] is missing or not numeric in directory [%s]", tag, tag.getNumberID(), tag.getDirectoryType().getDescription()));
    }
}