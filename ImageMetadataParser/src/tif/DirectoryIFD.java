package tif;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import common.ByteValueConverter;
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
            this.parsedData = parseData(byteOrder);
        }

        /**
         * Copy constructor to create an immutable {@code EntryIFD} instance by copying data from
         * another {@code EntryIFD} object.
         *
         * @param entry
         *        the original EntryIFD object to copy
         */
        public EntryIFD(EntryIFD entry)
        {
            this.tagEnum = entry.tagEnum;
            this.fieldType = entry.fieldType;
            this.count = entry.count;
            this.valueOffset = entry.valueOffset;
            this.value = (entry.value != null ? Arrays.copyOf(entry.value, entry.value.length) : null);
            this.parsedData = entry.parsedData;
        }

        /**
         * Parses the raw byte array into a typed Java object based on the TIFF field type.
         *
         * @param order
         *        the byte order used for parsing
         * @return parsed data as {@code Object}, or {@code null} if parsing fails or input is
         *         absent
         */
        private Object parseData(ByteOrder order)
        {
            if (value == null || value.length == 0)
            {
                return null;
            }

            int elementSize = fieldType.getElementLength();

            // Safety check: value length must match count * elementSize
            if (value.length < count * elementSize)
            {
                LOGGER.warn("Value length mismatch for tag [" + getTagID() + "], expected [" +
                        (count * elementSize) + "] bytes, got [" + value.length + "]");
                return null;
            }

            switch (fieldType)
            {
                case TYPE_BYTE_U:

                    if (count == 1)
                    {
                        return Byte.toUnsignedInt(value[0]);
                    }

                    int[] unsignedBytes = new int[count];

                    for (int i = 0; i < count; i++)
                    {
                        unsignedBytes[i] = Byte.toUnsignedInt(value[i]);
                    }

                    return unsignedBytes;

                case TYPE_BYTE_S:

                    if (count == 1)
                    {
                        return value[0];
                    }

                    return Arrays.copyOf(value, count);

                case TYPE_UNDEFINED:

                    return Arrays.copyOf(value, value.length);

                case TYPE_ASCII:

                    String str = new String(ByteValueConverter.readFirstNullTerminatedByteArray(value), StandardCharsets.UTF_8);
                    return str.isEmpty() ? "" : str;

                case TYPE_SHORT_U:

                    if (count == 1)
                    {
                        return ByteValueConverter.toUnsignedShort(value, 0, order);
                    }

                    int[] unsignedShorts = new int[count];

                    for (int i = 0; i < count; i++)
                    {
                        unsignedShorts[i] = ByteValueConverter.toUnsignedShort(value, i * 2, order);
                    }

                    return unsignedShorts;

                case TYPE_SHORT_S:

                    if (count == 1)
                    {
                        return ByteValueConverter.toShort(value, 0, order);
                    }

                    short[] signedShorts = new short[count];

                    for (int i = 0; i < count; i++)
                    {
                        signedShorts[i] = ByteValueConverter.toShort(value, i * 2, order);
                    }

                    return signedShorts;

                case TYPE_LONG_U:
                case TYPE_IFD_POINTER:

                    if (count == 1)
                    {
                        return ByteValueConverter.toUnsignedInteger(value, 0, order);
                    }

                    long[] unsignedLongs = new long[count];

                    for (int i = 0; i < count; i++)
                    {
                        unsignedLongs[i] = ByteValueConverter.toUnsignedInteger(value, i * 4, order);
                    }
                    
                    return unsignedLongs;

                case TYPE_LONG_S:

                    if (count == 1)
                    {
                        return ByteValueConverter.toInteger(value, 0, order);
                    }

                    int[] signedLongs = new int[count];

                    for (int i = 0; i < count; i++)
                    {
                        signedLongs[i] = ByteValueConverter.toInteger(value, i * 4, order);
                    }

                    return signedLongs;

                case TYPE_RATIONAL_U:

                    if (count == 1)
                    {
                        return ByteValueConverter.toRational(value, 0, order, RationalNumber.DataType.UNSIGNED);
                    }

                    RationalNumber[] rationalsU = new RationalNumber[count];

                    for (int i = 0; i < count; i++)
                    {
                        rationalsU[i] = ByteValueConverter.toRational(value, i * 8, order, RationalNumber.DataType.UNSIGNED);
                    }

                    return rationalsU;

                case TYPE_RATIONAL_S:

                    if (count == 1)
                    {
                        return ByteValueConverter.toRational(value, 0, order, RationalNumber.DataType.SIGNED);
                    }

                    RationalNumber[] rationalsS = new RationalNumber[count];

                    for (int i = 0; i < count; i++)
                    {
                        rationalsS[i] = ByteValueConverter.toRational(value, i * 8, order, RationalNumber.DataType.SIGNED);
                    }

                    return rationalsS;

                case TYPE_FLOAT:

                    if (count == 1)
                    {
                        return ByteValueConverter.toFloat(value, 0, order);
                    }

                    float[] floats = new float[count];

                    for (int i = 0; i < count; i++)
                    {
                        floats[i] = ByteValueConverter.toFloat(value, i * 4, order);
                    }

                    return floats;

                case TYPE_DOUBLE:

                    if (count == 1)
                    {
                        return ByteValueConverter.toDouble(value, 0, order);
                    }

                    double[] doubles = new double[count];

                    for (int i = 0; i < count; i++)
                    {
                        doubles[i] = ByteValueConverter.toDouble(value, i * 8, order);
                    }

                    return doubles;

                default:
                    return null;
            }
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
         * @return a copy of the raw value byte array, or null if not set
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
        this.entryMap = new HashMap<>();
        this.directoryType = dirType;
        this.headerByteOrder = order;
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
     *
     * @return always true
     */
    public boolean addEntry(Taggable tag, TifFieldType ttype, int length, int offset, byte[] bytes)
    {
        return add(new EntryIFD(tag, ttype, length, offset, bytes, headerByteOrder));
    }

    /**
     * Checks whether the specified tag holds a value based on a general numerical representation.
     *
     * @param tag
     *        the specific enumeration tag to search for a match
     *
     * @return boolean true if the value held by the tag is numeric, false otherwise
     */
    public boolean isTagNumeric(Taggable tag)
    {
        EntryIFD entry = findEntryByID(tag.getNumberID());

        return (entry == null ? false : entry.getFieldType().isNumber());
    }

    /**
     * Returns the string value associated with the specified tag entry.
     *
     * @param tag
     *        the enumeration tag to obtain the value
     *
     * @return a string representing the value of the specified tag entry
     */
    public String getString(Taggable tag)
    {
        EntryIFD entry = findEntryByID(tag.getNumberID());

        return (entry == null ? "" : getStringValue(entry));
    }

    /**
     * Retrieves the string value of the specified EntryIFD object.
     *
     * @param entry
     *        an EntryIFD object to retrieve the value from its field
     *
     * @return a String representation of the value associated with the specified EntryIFD object
     */
    public String getStringValue(EntryIFD entry)
    {
        return TagValueConverter.toStringValue(entry);
    }

    /**
     * Returns the integer value associated with the specified tag enumeration.
     *
     * @param tag
     *        the enumeration tag to retrieve the value
     *
     * @return an Integer representation of the tag's value
     */
    public int getIntValue(Taggable tag)
    {
        return getNumericValue(tag).intValue();
    }

    /**
     * Returns the long value associated with the specified tag enumeration.
     *
     * @param tag
     *        the enumeration tag to retrieve the value
     *
     * @return a Long representation of the tag's value
     */
    public long getLongValue(Taggable tag)
    {
        return getNumericValue(tag).longValue();
    }

    /**
     * Returns the float value associated with the specified tag enumeration.
     *
     * @param tag
     *        the enumeration tag to retrieve the value
     *
     * @return a Float representation of the tag's value
     */
    public float getFloatValue(Taggable tag)
    {
        return getNumericValue(tag).floatValue();
    }

    /**
     * Returns the double value associated with the specified tag enumeration.
     *
     * @param tag
     *        the enumeration tag to retrieve the value
     *
     * @return a Double representation of the tag's value
     */
    public double getDoubleValue(Taggable tag)
    {
        return getNumericValue(tag).doubleValue();
    }

    /**
     * Returns the rational number value associated with the specified tag enumeration.
     *
     * @param tag
     *        the enumeration tag to fetch the value
     *
     * @return a Rational Number representation of the tag's value, otherwise null is returned
     *
     * @throws IllegalArgumentException
     *         if the tag is not found in the IFD structure
     */
    public RationalNumber getRationalValue(Taggable tag)
    {
        EntryIFD entry = findEntryByID(tag.getNumberID());

        if (entry != null)
        {
            Object obj = entry.getData();

            if (obj instanceof RationalNumber)
            {
                return ((RationalNumber) obj);
            }
        }

        return null;
    }

    /**
     * Returns a Date object if the tag is marked as a potential Date entry.
     *
     * @param tag
     *        the enumeration tag to obtain the value
     *
     * @return a Date object representing the value of the specified tag entry, or null if the tag
     *         is not a Date type
     */
    public Date getDate(Taggable tag)
    {
        if (tag.getHint() == TagHint.HINT_DATE)
        {
            EntryIFD entry = findEntryByID(tag.getNumberID());

            if (entry != null)
            {
                Object data = entry.getData();

                if (data instanceof String)
                {
                    return DateParser.convertToDate((String) data);
                }
            }
        }

        return null;
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
     *
     * @return always true
     */
    @Override
    public boolean add(EntryIFD entry)
    {
        EntryIFD newEntry = new EntryIFD(entry);

        entryMap.put(newEntry.getTagID(), newEntry);

        return true;
    }

    /**
     * Removes an entry from this Directory.
     *
     * @param entry
     *        {@code EntryIFD} object to remove
     *
     * @return true if this collection changed as a result of the call if the tag exists, otherwise
     *         false
     */
    @Override
    public boolean remove(EntryIFD entry)
    {
        return (entryMap.remove(entry.getTagID()) != null);
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

    @Override
    public boolean isEmpty()
    {
        return entryMap.isEmpty();
    }

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
     * @param tagid
     *        the tag ID identifying the entry
     *
     * @return an {@code EntryIFD} entry, or null if no matching entry is found
     */
    private EntryIFD findEntryByID(int tagid)
    {
        EntryIFD entry = entryMap.get(tagid);

        if (entry == null || entry.getData() == null)
        {
            LOGGER.debug("Entry is null or cannot find directory entry for tag ID [" + tagid + "]");
            return null;
        }

        return entry;
    }

    /**
     * Retrieves the value of a tag in numeric form, if available.
     *
     * This method attempts to return the value of the specified tag as a {@link Number}. If the tag
     * is not present, or the value cannot be interpreted as a numeric type, a default value of
     * {@code 0} is returned.
     *
     * @param tag
     *        the tag for which the numeric value is requested
     * @return a Number instance if the tag exists and has numeric content, otherwise, zero
     */
    private Number getNumericValue(Taggable tag)
    {
        EntryIFD entry = findEntryByID(tag.getNumberID());

        if (entry == null || !entry.getFieldType().isNumber())
        {
            return 0;
        }

        return TagValueConverter.toNumericValue(entry);
    }
}