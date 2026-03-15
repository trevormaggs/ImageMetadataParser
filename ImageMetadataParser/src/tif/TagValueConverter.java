package tif;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import common.ByteValueConverter;
import common.RationalNumber;
import common.SmartDateParser;
import logger.LogFactory;
import tif.DirectoryIFD.EntryIFD;
import tif.tagspecs.TagIFD_GPS;
import tif.tagspecs.Taggable;

/**
 * Utility class for converting {@link EntryIFD} values into human-readable or numeric forms.
 * This class applies transformation rules based on TIFF field types and tag hints.
 *
 * @author Trevor Maggs
 * @version 1.2
 * @since 13 August 2025
 */
public final class TagValueConverter
{
    private static final LogFactory LOGGER = LogFactory.getLogger(TagValueConverter.class);
    private static final int ENCODING_HEADER_LENGTH = 8;
    private static final Map<String, Charset> ENCODING_MAP = new HashMap<>();

    static
    {
        ENCODING_MAP.put("ASCII\0\0\0", StandardCharsets.US_ASCII);
        ENCODING_MAP.put("UTF-8\0\0\0", StandardCharsets.UTF_8);
        ENCODING_MAP.put("\0\0\0\0\0\0\0\0", StandardCharsets.UTF_8);
        ENCODING_MAP.put("JIS\0\0\0\0\0", Charset.forName("Shift_JIS"));
    }

    /**
     * Default constructor will always throw an exception.
     *
     * @throws UnsupportedOperationException
     *         to indicate that instantiation is not supported
     */
    private TagValueConverter()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

    /**
     * Checks if a TIFF field type can be losslessly converted to a 32-bit signed integer.
     *
     * @param type
     *        the TIFF field type to evaluate
     * @return {@code true} if the type is compatible with 32-bit signed integers
     */
    public static boolean canConvertToInt(TifFieldType type)
    {
        switch (type)
        {
            case TYPE_BYTE_U:
            case TYPE_BYTE_S:
            case TYPE_SHORT_U:
            case TYPE_SHORT_S:
            case TYPE_LONG_S:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns the value as an integer.
     *
     * @param entry
     *        the {@link EntryIFD} to evaluate
     * @return the integer value
     * 
     * @throws IllegalArgumentException
     *         if the field type is incompatible with 32-bit integers
     */
    public static int getIntValue(EntryIFD entry)
    {
        if (!canConvertToInt(entry.getFieldType()))
        {
            throw new IllegalArgumentException("Field [" + entry.getFieldType() + "] exceeds 32-bit signed range.");
        }

        return toNumericValue(entry).intValue();
    }

    /**
     * Returns the value as a long.
     *
     * @param entry
     *        the {@link EntryIFD} to evaluate
     * @return the long value
     */
    public static long getLongValue(EntryIFD entry)
    {
        return toNumericValue(entry).longValue();
    }

    /**
     * Returns the value as a float.
     *
     * @param entry
     *        the {@link EntryIFD} to evaluate
     * @return the float value
     */
    public static float getFloatValue(EntryIFD entry)
    {
        return toNumericValue(entry).floatValue();
    }

    /**
     * Returns the value as a double.
     *
     * @param entry
     *        the {@link EntryIFD} to evaluate
     * @return the double value
     */
    public static double getDoubleValue(EntryIFD entry)
    {
        return toNumericValue(entry).doubleValue();
    }

    /**
     * Retrieves a byte array from the specified entry.
     *
     * @param entry
     *        the {@link EntryIFD} to evaluate
     * @return the data cast as a byte array
     * 
     * @throws IllegalArgumentException
     *         if the data is not an instance of byte[]
     */
    public static byte[] getByteArrayValue(EntryIFD entry)
    {
        return getArray(entry, byte[].class);
    }

    /**
     * Retrieves an integer array from the specified entry.
     *
     * @param entry
     *        the {@link EntryIFD} to evaluate
     * @return the data cast as an int array
     * 
     * @throws IllegalArgumentException
     *         if the data is not an instance of int[]
     */
    public static int[] getIntArrayValue(EntryIFD entry)
    {
        return getArray(entry, int[].class);
    }

    /**
     * Retrieves a short array from the specified entry.
     *
     * @param entry
     *        the {@link EntryIFD} to evaluate
     * @return the data cast as a short array
     * 
     * @throws IllegalArgumentException
     *         if the data is not an instance of short[]
     */
    public static short[] getShortArrayValue(EntryIFD entry)
    {
        return getArray(entry, short[].class);
    }

    /**
     * Retrieves a long array from the specified entry.
     *
     * @param entry
     *        the {@link EntryIFD} to evaluate
     * @return the data cast as a long array
     * 
     * @throws IllegalArgumentException
     *         if the data is not an instance of long[]
     */
    public static long[] getLongArrayValue(EntryIFD entry)
    {
        return getArray(entry, long[].class);
    }

    /**
     * Retrieves a RationalNumber array from the specified entry.
     *
     * @param entry
     *        the {@link EntryIFD} to evaluate
     * @return the data cast as a RationalNumber array
     * 
     * @throws IllegalArgumentException
     *         if the data is not an instance of RationalNumber[]
     */
    public static RationalNumber[] getRationalArrayValue(EntryIFD entry)
    {
        return getArray(entry, RationalNumber[].class);
    }

    /**
     * Converts an {@link EntryIFD} to a human-readable string.
     *
     * <p>
     * This method handles specific formatting for GPS coordinates (DMS), Rational arrays, and
     * encoded string types like UserComments.
     * </p>
     *
     * @param entry
     *        the entry to format
     * @param parentDir
     *        the directory context for reference lookups (e.g., GPS). It may be null
     * @return a formatted string representation of the entry data
     */
    public static String toStringValue(EntryIFD entry, DirectoryIFD parentDir)
    {
        Object obj = entry.getData();
        Taggable tag = entry.getTag();

        if (obj == null)
        {
            return "";
        }

        int tagId = tag.getNumberID();

        if (obj instanceof RationalNumber[] && (tagId == TagIFD_GPS.GPS_LATITUDE.getNumberID() || tagId == TagIFD_GPS.GPS_LONGITUDE.getNumberID()))
        {
            return decodeGpsArray((RationalNumber[]) obj, tag, parentDir);
        }

        if (obj instanceof RationalNumber)
        {
            return ((RationalNumber) obj).toSimpleString(true);
        }

        if (obj instanceof RationalNumber[])
        {
            StringBuilder sb = new StringBuilder();
            RationalNumber[] rationals = (RationalNumber[]) obj;

            for (int i = 0; i < rationals.length; i++)
            {
                sb.append(rationals[i].toSimpleString(true));

                if (i < rationals.length - 1)
                {
                    sb.append(", ");
                }
            }

            return sb.toString();
        }

        if (obj instanceof Number)
        {
            return obj.toString();
        }

        if (obj instanceof byte[])
        {
            return decodeByteArray(tag, (byte[]) obj);
        }

        if (obj instanceof int[])
        {
            return decodeIntArray(entry, (int[]) obj);
        }

        if (obj instanceof String)
        {
            return decodeString(tag, (String) obj);
        }

        LOGGER.warn("Unsupported field [" + entry.getFieldType() + "] for tag [" + tag + "]");

        return "";
    }

    /**
     * Extracts a {@link Date} object from an entry if a date hint is present.
     *
     * @param entry
     *        the entry to parse
     * @return a parsed Date object
     * @throws IllegalArgumentException
     *         if the hint is missing or the format is invalid
     */
    public static Date getDate(EntryIFD entry)
    {
        if (entry.getTag().getHint() != TagHint.HINT_DATE)
        {
            throw new IllegalArgumentException("Tag does not contain a date hint: " + entry.getTag());
        }

        if (entry.getData() instanceof String)
        {
            Date d = SmartDateParser.convertToDate((String) entry.getData());

            if (d != null)
            {
                return d;
            }
        }

        throw new IllegalArgumentException("Invalid or null date format in entry [" + entry.getTag() + "]");
    }

    /**
     * Overloaded string conversion when directory context is unavailable.
     *
     * @param entry
     *        the entry to format
     * @return a formatted string
     */
    public static String toStringValue(EntryIFD entry)
    {
        return toStringValue(entry, null);
    }

    /**
     * Validates and returns the entry data as a {@link Number}.
     *
     * @param entry
     *        the {@link EntryIFD} to evaluate
     * @return the data cast as a Number
     * 
     * @throws IllegalArgumentException
     *         if the data is not numeric
     */
    private static Number toNumericValue(EntryIFD entry)
    {
        Object obj = entry.getData();

        if (obj instanceof Number)
        {
            return (Number) obj;
        }

        throw new IllegalArgumentException("Tag [" + entry.getTag() + "] is not a numeric type");
    }

    private static String decodeGpsArray(RationalNumber[] rationals, Taggable tag, DirectoryIFD dir)
    {
        if (rationals.length < 3)
        {
            return Arrays.toString(rationals);
        }

        String d = rationals[0].toSimpleString(true);
        String m = rationals[1].toSimpleString(true);
        String s = rationals[2].toSimpleString(true);

        String ref = "";
        int refID = tag.getNumberID() - 1; // GPS Refs are always ID-1 relative to coordinate tags

        if (dir != null)
        {
            EntryIFD refEntry = dir.getEntry(refID);

            if (refEntry != null && refEntry.getData() instanceof String)
            {
                ref = (String) refEntry.getData();
            }
        }

        return String.format("%s° %s' %s\" %s", d, m, s, ref).trim();
    }

    /**
     * Decodes a string value.
     *
     * <p>
     * If the tag hint is {@link TagHint#HINT_DATE}, the method attempts to parse the input into a
     * standard date string. Otherwise, it returns the trimmed raw data.
     * </p>
     *
     * @param tag
     *        the tag descriptor used to check for formatting hints
     * @param data
     *        the raw string value to be decoded
     * @return a formatted string representation of the data, or the trimmed raw string if no
     *         special formatting is required
     */
    private static String decodeString(Taggable tag, String data)
    {
        String val = data.trim();

        if (tag.getHint() == TagHint.HINT_DATE)
        {
            Date d = SmartDateParser.convertToDate(val);

            return (d != null) ? d.toString() : val;
        }

        return val;
    }

    /**
     * Decodes the specified array of integers and converts it to a string representation. This
     * method handles TIFF types {@code TYPE_BYTE_U}, {@code TYPE_BYTE_S}, {@code TYPE_SHORT_U},
     * {@code TYPE_SHORT_S}, and {@code TYPE_LONG_S}.
     *
     * @param entry
     *        the {@link EntryIFD} to evaluate
     * @param ints
     *        the array of integers to decode
     * @return the string representation of the raw data, formatted according to the tag's hint
     */
    private static String decodeIntArray(EntryIFD entry, int[] ints)
    {
        TifFieldType type = entry.getFieldType();

        if (type == TifFieldType.TYPE_BYTE_U || type == TifFieldType.TYPE_BYTE_S)
        {
            byte[] b = ByteValueConverter.revertIntArrayToByteArray(ints);

            // Handle Windows-specific UCS-2 encoded tags (e.g., XPTitle)
            if (entry.getTag().getHint() == TagHint.HINT_UCS2)
            {
                return new String(b, StandardCharsets.UTF_16LE).replace("\u0000", "").trim();
            }

            return ByteValueConverter.toHex(b);
        }

        return Arrays.toString(ints);
    }

    /**
     * Decodes a raw byte array into a human-readable string based on the tag's hint.
     * 
     * <p>
     * This method handles several specialised binary formats:
     * </p>
     * 
     * <ul>
     * <li>{@code HINT_MASK}: Returns a placeholder for sensitive data.</li>
     * <li>{@code HINT_BYTE}: Formats the array as a hexadecimal string.</li>
     * <li>{@code HINT_ENCODED_STRING}: Decodes strings with embedded charset headers (e.g., UserComments).</li>
     * </ul>
     * 
     * If no specific hint is matched, the data is treated as a null-terminated UTF-8 string.
     *
     * @param tag
     *        the tag descriptor providing the transformation hint
     * @param bytes
     *        the raw binary data to be decoded
     * @return a formatted string representation of the byte array
     */
    private static String decodeByteArray(Taggable tag, byte[] bytes)
    {
        TagHint hint = tag.getHint();

        if (hint == TagHint.HINT_MASK)
        {
            return "[Masked items]";
        }

        if (hint == TagHint.HINT_BYTE)
        {
            return ByteValueConverter.toHex(bytes);
        }

        if (hint == TagHint.HINT_ENCODED_STRING)
        {
            return decodeUserComment(bytes);
        }

        return new String(ByteValueConverter.readFirstNullTerminatedByteArray(bytes), StandardCharsets.UTF_8);
    }

    /**
     * Decodes encoded strings containing an 8-byte charset identifier. Common in tags like
     * {@code EXIF_USER_COMMENT}.
     *
     * @param data
     *        the raw byte array
     * @return a decoded, trimmed string
     */
    private static String decodeUserComment(byte[] data)
    {
        if (data == null || data.length < ENCODING_HEADER_LENGTH)
        {
            return "";
        }

        String header = new String(data, 0, ENCODING_HEADER_LENGTH, StandardCharsets.US_ASCII);
        Charset charset = ENCODING_MAP.get(header);
        byte[] cleaned = ByteValueConverter.readFirstNullTerminatedByteArray(Arrays.copyOfRange(data, ENCODING_HEADER_LENGTH, data.length));

        if (charset == null)
        {
            charset = StandardCharsets.UTF_8;
        }

        return new String(cleaned, charset).trim();
    }

    /**
     * Internal helper to validate and cast data types safely.
     * 
     * @param <T>
     *        the type parameter
     * @param entry
     *        the entry containing the data
     * @param type
     *        the expected class type
     * @return the casted object
     * 
     * @throws IllegalArgumentException
     *         if type mismatch occurs
     */
    private static <T> T getArray(EntryIFD entry, Class<T> type)
    {
        Object obj = entry.getData();

        if (type.isInstance(obj))
        {
            return type.cast(obj);
        }

        String name = (obj == null ? "null" : obj.getClass().getSimpleName());

        throw new IllegalArgumentException("Entry [" + entry.getTag() + "] is " + name + ", not " + type.getSimpleName());
    }
}