package tif;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import common.Directory;
import common.MetadataConstants;
import common.RationalNumber;
import logger.LogFactory;
import tif.DirectoryIFD3.EntryIFD;
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
public class DirectoryIFD3 implements Directory<EntryIFD>
{
    private static final LogFactory LOGGER = LogFactory.getLogger(DirectoryIFD3.class);
    private final DirectoryIdentifier directoryType;
    private final Map<Integer, EntryIFD> entryMap;

    /**
     * Represents a single Image File Directory (IFD) entry within a TIFF structure.
     *
     * Each {@code EntryIFD} encapsulates metadata such as tag ID, data type, count, raw bytes, and
     * a parsed object representation. It is immutable and self-contained.
     *
     * @author Trevor Maggs
     * @since 21 June 2025
     */
    public final static class EntryIFD
    {
        private final Taggable tagEnum;
        private final TifFieldType fieldType;
        private final long count;
        private final int valueOffset;
        private final byte[] value;
        private final Object parsedData;
        private final boolean isArray;

        /**
         * Constructs an immutable {@code EntryIFD} instance from raw bytes.
         *
         * @param tag
         *        the tag descriptor (Taggable enum)
         * @param ttype
         *        the TIFF field type
         * @param length
         *        the number of values
         * @param offset
         *        the raw offset/value field (bytes 9-12 of the entry)
         * @param bytes
         *        the value bytes (may be null)
         * @param byteOrder
         *        the byte order, either {@code ByteOrder.BIG_ENDIAN} or
         *        {@code ByteOrder.LITTLE_ENDIAN}
         */
        public EntryIFD(Taggable tag, TifFieldType ttype, long length, int offset, byte[] bytes, ByteOrder byteOrder)
        {
            this.tagEnum = tag;
            this.fieldType = ttype;
            this.count = length;
            this.valueOffset = offset;
            this.value = (bytes != null ? Arrays.copyOf(bytes, bytes.length) : null);
            this.parsedData = fieldType.parse(value, count, byteOrder);
            this.isArray = (parsedData != null && parsedData.getClass().isArray());
            // System.out.printf("%-30s (%s)%n", getTag(), getData().getClass().getSimpleName());
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
         * @return the number of values (count) represented by this entry, returned as a long
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
         * @return a defensive copy of the byte array, or null if not set
         */

        public byte[] getByteArray()
        {
            return (value != null ? Arrays.copyOf(value, value.length) : null);
        }

        /**
         * @return the total byte length of the data, based on field type and count
         */
        public long getByteLength()
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
         * Returns the boolean value indicating whether the parsed data is an array.
         *
         * @return true if the parsed data is an array, otherwise false
         */
        public boolean isArray()
        {
            return isArray;
        }

        /**
         * @return formatted string describing the entry’s key characteristics
         */
        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(MetadataConstants.FORMATTER, "Tag Name", getTag() + " (Tag ID: " + String.format("0x%04X", getTagID()) + ")"));
            sb.append(String.format(MetadataConstants.FORMATTER, "Field Type", getFieldType() + " (count: " + getCount() + ")"));
            sb.append(String.format(MetadataConstants.FORMATTER, "Hint", getTag().getHint()));

            // sb.append(String.format(" %-20s %s%n", "[Value]",
            // TagValueConverter.toStringValue(this)));

            if (getByteLength() > IFDHandler.ENTRY_MAX_VALUE_LENGTH)
            {
                sb.append(String.format(MetadataConstants.FORMATTER, "Jump Offset", valueOffset));
            }

            retrieveRealValueString(sb, this);

            return sb.toString();
        }

        private static void retrieveRealValueString(StringBuilder sb, EntryIFD entry)
        {
            if (entry.getCount() == 1)
            {
                switch (entry.getFieldType())
                {
                    case TYPE_BYTE_U:
                        sb.append(String.format(MetadataConstants.FORMATTER, "Byte Number", TagValueConverter.getIntValue(entry)));
                    break;
                    case TYPE_ASCII:
                        sb.append(String.format(MetadataConstants.FORMATTER, "String", TagValueConverter.toStringValue(entry)));
                    break;
                    case TYPE_SHORT_U:
                        sb.append(String.format(MetadataConstants.FORMATTER, "Short Number", TagValueConverter.getIntValue(entry)));
                    break;
                    case TYPE_LONG_U:
                        sb.append(String.format(MetadataConstants.FORMATTER, "Long Number", TagValueConverter.getLongValue(entry)));
                    break;
                    case TYPE_RATIONAL_U:
                        sb.append(String.format(MetadataConstants.FORMATTER, "Rational Number", ((RationalNumber) entry.getData()).toSimpleString(true)));
                    break;
                    case TYPE_BYTE_S:
                        sb.append(String.format(MetadataConstants.FORMATTER, "Byte Number", TagValueConverter.getIntValue(entry)));
                    break;
                    case TYPE_UNDEFINED:
                        sb.append(String.format(MetadataConstants.FORMATTER, "Undefined Number", TagValueConverter.getIntValue(entry)));
                    break;
                    case TYPE_SHORT_S:
                        sb.append(String.format(MetadataConstants.FORMATTER, "Short Number", TagValueConverter.getIntValue(entry)));
                    break;
                    case TYPE_LONG_S:
                        sb.append(String.format(MetadataConstants.FORMATTER, "Long Number", TagValueConverter.getIntValue(entry)));
                    break;
                    case TYPE_RATIONAL_S:
                        sb.append(String.format(MetadataConstants.FORMATTER, "Rational Number", ((RationalNumber) entry.getData()).toSimpleString(true)));
                    break;
                    case TYPE_FLOAT:
                        sb.append(String.format(MetadataConstants.FORMATTER, "Float Number", TagValueConverter.getIntValue(entry)));
                    break;
                    case TYPE_DOUBLE:
                        sb.append(String.format(MetadataConstants.FORMATTER, "Double Number", TagValueConverter.getIntValue(entry)));
                    break;
                    default:
                        sb.append("Unknown");
                }
                
                return;
            }
            
            sb.append("Unknown\n");
        }

        public String toString2()
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

            sb.append(String.format("  %-20s %s%n", "[Hint]", getTag().getHint()));

            return sb.toString();
        }
    }

    /**
     * Constructs a new directory instance to manage a collection of IFD entries embedded within the
     * TIFF image file.
     *
     * @param dirType
     *        a directory type defined in the {@link DirectoryIdentifier} enumeration class
     */
    public DirectoryIFD3(DirectoryIdentifier dirType)
    {
        this.directoryType = dirType;
        this.entryMap = new LinkedHashMap<>();

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
     * Checks if the {@code TifFieldType} within the specified tag can safely be converted to a
     * Java {@code 32-bit signed int} losslessly.
     *
     * @param tag
     *        the specific enumeration tag to check
     * @return true if the underlying tag type is convertible to int without loss of precision,
     *         otherwise false
     */
    public boolean isConvertibleToInt(Taggable tag)
    {
        Optional<EntryIFD> opt = findEntryByTag(tag);

        return (opt.isPresent() ? TagValueConverter.canConvertToInt(opt.get().getFieldType()) : false);
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
        Optional<EntryIFD> opt = findEntryByTag(tag);

        if (!opt.isPresent())
        {
            throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), getDirectoryType().getDescription()));
        }

        EntryIFD entry = opt.get();

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
     *         if the tag is unknown or not numeric
     */
    public long getLongValue(Taggable tag)
    {
        Optional<EntryIFD> opt = findEntryByTag(tag);

        if (!opt.isPresent())
        {
            throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), getDirectoryType().getDescription()));
        }

        return TagValueConverter.getLongValue(opt.get());
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
        Optional<EntryIFD> opt = findEntryByTag(tag);

        if (!opt.isPresent())
        {
            throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), getDirectoryType().getDescription()));
        }

        return TagValueConverter.getFloatValue(opt.get());
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
        Optional<EntryIFD> opt = findEntryByTag(tag);

        if (!opt.isPresent())
        {
            throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), getDirectoryType().getDescription()));
        }

        return TagValueConverter.getDoubleValue(opt.get());
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
        Optional<EntryIFD> opt = findEntryByTag(tag);

        if (opt.isPresent())
        {
            Object obj = opt.get().getData();

            if (obj instanceof RationalNumber)
            {
                return (RationalNumber) obj;
            }

            else if (obj != null)
            {
                throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] is not a Rational Number. Found [%s]", tag, tag.getNumberID(), obj.getClass().getName()));
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
        Optional<EntryIFD> opt = findEntryByTag(tag);

        if (!opt.isPresent())
        {
            throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), getDirectoryType().getDescription()));
        }

        return TagValueConverter.toStringValue(opt.get());
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
        Optional<EntryIFD> opt = findEntryByTag(tag);

        if (!opt.isPresent())
        {
            throw new IllegalArgumentException(String.format("Tag [%s (0x%04X)] not found in directory [%s]", tag, tag.getNumberID(), getDirectoryType().getDescription()));
        }

        return TagValueConverter.getDate(opt.get());
    }

    /**
     * Returns a copy of the raw byte array value associated with the specified tag.
     * 
     * <p>
     * This is primarily intended for handling complex, embedded data structures like XMP (Adobe
     * Extensible Metadata Platform) where the payload is stored as a raw byte block.
     * </p>
     * 
     * @param tag
     *        the enumeration tag to obtain the raw bytes for
     * @return a copy of the tag's raw byte array if present, otherwise an empty array is returned
     */
    public byte[] getRawByteArray(Taggable tag)
    {
        EntryIFD entry = entryMap.get(tag.getNumberID());

        if (entry != null)
        {
            return entry.getByteArray();
        }

        return new byte[0];
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
     * Adds a new {@code EntryIFD} entry to the collection within this directory.
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
     * Checks if the specified tag entry has been set in this directory.
     *
     * @param tag
     *        the enumeration tag to look for
     * @return true if the specified entry is contained in the map
     */
    public boolean contains(Taggable tag)
    {
        return entryMap.containsKey(tag.getNumberID());
    }

    /**
     * Checks if the specified {@code EntryIFD} entry has been added to this directory.
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
     * Returns the count of IFD entries present in this directory.
     *
     * @return the total number of elements in the directory
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
     * @return an Optional containing the {@link EntryIFD}, or an empty Optional if not found
     */
    private Optional<EntryIFD> findEntryByTag(Taggable tag)
    {
        return Optional.ofNullable(entryMap.get(tag.getNumberID()));
    }

    /**
     * Checks whether the specified tag holds a value based on a general numerical representation.
     *
     * @param tag
     *        the specific enumeration tag to search for a match
     * @return boolean true if the value held by the tag is numeric, false otherwise
     */
    @Deprecated
    public boolean isTagNumeric(Taggable tag)
    {
        Optional<EntryIFD> opt = findEntryByTag(tag);

        return (opt.isPresent() ? opt.get().getFieldType().isNumber() : false);
    }
}