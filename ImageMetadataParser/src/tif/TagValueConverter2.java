package tif;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import common.ByteValueConverter;
import common.DateParser;
import common.RationalNumber;
import logger.LogFactory;
import tif.DirectoryIFD.EntryIFD;
import tif.tagspecs.Taggable;

/**
 * Utility class for converting EntryIFD values to human-readable or numeric forms, using hint-aware
 * transformation rules.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public final class TagValueConverter2
{
    private static final LogFactory LOGGER = LogFactory.getLogger(TagValueConverter2.class);
    private static final String ASCII_IDENTIFIER = "ASCII\0\0\0";
    private static final String UTF8_IDENTIFIER = "UTF-8\0\0\0";
    private static final String UNDEFINED_IDENTIFIER = "\0\0\0\0\0\0\0\0";
    private static final String JIS_IDENTIFIER = "JIS\0\0\0\0\0";
    private static final int ENCODING_HEADER_LENGTH = 8;
    private static final Map<String, Charset> ENCODING_MAP;
    
    // For reliable comparison of the UNDEFINED identifier bytes
    private static final byte[] UNDEFINED_IDENTIFIER_BYTES = UNDEFINED_IDENTIFIER.getBytes(StandardCharsets.US_ASCII);

    static
    {
        ENCODING_MAP = new HashMap<String, Charset>()
        {
            {
                /* Keys are the full 8-byte strings, including nulls/padding */
                put(ASCII_IDENTIFIER, StandardCharsets.US_ASCII);
                put(UTF8_IDENTIFIER, StandardCharsets.UTF_8);
                put(UNDEFINED_IDENTIFIER, StandardCharsets.UTF_8);

                /*
                 * Note: Shift_JIS (or SJIS) is the common Java Charset name
                 * for JIS encoding in Exif/TIFF
                 */
                put(JIS_IDENTIFIER, Charset.forName("Shift_JIS"));
            }
        };
    }

    /**
     * Default constructor is unsupported and will always throw an exception.
     *
     * @throws UnsupportedOperationException
     * to indicate that instantiation is not supported
     */
    private TagValueConverter2()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

    /**
     * Checks if the given TifFieldType can be converted to a Java int (32-bit signed) without data
     * loss or sign mis-interpretation.
     *
     * @param type
     * the TIFF field type
     * @return true if the conversion is safe and lossless, otherwise false explicitly for the types
     * that cause loss of precision
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
     * Returns the integer value associated with the specified {@code EntryIFD} input.
     *
     * <p>
     * This method first checks that the entry contains a general numeric value (an instance of
     * {@link Number}), and then verifies that the underlying TIFF field type, specifically BYTE,
     * SHORT, or signed LONG can be safely converted to a Java 32-bit {@code int} without data loss
     * or incorrect sign interpretation.
     * </p>
     *
     * @param entry
     * the EntryIFD object to retrieve
     * @return the tag's value as an integer
     *
     * @throws IllegalArgumentException
     * if the entry's value is not numeric (i.e. ASCII or UNDEFINED) or if its TIFF type
     * (i.e. unsigned LONG or RATIONAL) is not convertible to a Java 32-bit int safely and
     * losslessly
     */
    public static int getIntValue(EntryIFD entry)
    {
        Number number = toNumericValue(entry);

        if (!canConvertToInt(entry.getFieldType()))
        {
            throw new IllegalArgumentException(String.format("Entry [%s] has field type [%s], which is not a safe, lossless type for conversion to integer.", entry.getTag(), entry.getFieldType()));
        }

        return number.intValue();
    }

    /**
     * Returns the array of integer values associated with the specified {@code EntryIFD}.
     *
     * <p>
     * This method is used for TIFF types like SHORT, LONG, and BYTE where the entry count is
     * greater than one.
     * </p>
     *
     * @param entry
     * the EntryIFD object containing the array
     * @return the tag's value as an {@code int[]} array
     * @throws IllegalArgumentException
     * if the entry does not contain an int[] array
     */
    public static int[] getIntArrayValue(EntryIFD entry)
    {
        Object obj = entry.getData();

        if (obj instanceof int[])
        {
            return (int[]) obj;
        }

        String simpleName = (obj == null ? "null" : obj.getClass().getSimpleName());
        String errmsg = String.format("Entry [%s (0x%04X)] data type is [%s], not a valid int array", entry.getTag(), entry.getTag().getNumberID(), simpleName);

        throw new IllegalArgumentException(errmsg);
    }

    /**
     * Returns the long value associated with the specified {@code EntryIFD} input.
     *
     * @param entry
     * the EntryIFD object to retrieve
     * @return the tag's value as a long
     */
    public static long getLongValue(EntryIFD entry)
    {
        return toNumericValue(entry).longValue();
    }

    /**
     * Returns the array of long values associated with the specified {@code EntryIFD}.
     *
     * @param entry
     * the EntryIFD object containing the array
     * @return the tag's value as a {@code long[]} array
     * @throws IllegalArgumentException
     * if the entry does not contain a long[] array
     */
    public static long[] getLongArrayValue(EntryIFD entry)
    {
        Object obj = entry.getData();

        if (obj instanceof long[])
        {
            return (long[]) obj;
        }

        String simpleName = (obj == null ? "null" : obj.getClass().getSimpleName());
        String errmsg = String.format("Entry [%s (0x%04X)] data type is [%s], not a valid long array", entry.getTag(), entry.getTag().getNumberID(), simpleName);

        throw new IllegalArgumentException(errmsg);
    }

    /**
     * Returns the array of RationalNumber objects associated with the specified {@code EntryIFD}.
     *
     * @param entry
     * the EntryIFD object containing the array
     * @return the tag's value as a {@code RationalNumber[]} array
     * @throws IllegalArgumentException
     * if the entry does not contain a RationalNumber array
     */
    public static RationalNumber[] getRationalArrayValue(EntryIFD entry)
    {
        Object obj = entry.getData();

        if (obj instanceof RationalNumber[])
        {
            return (RationalNumber[]) obj;
        }

        String simpleName = (obj == null ? "null" : obj.getClass().getSimpleName());
        String errmsg = String.format("Entry [%s (0x%04X)] data type is [%s], not a valid RationalNumber array", entry.getTag(), entry.getTag().getNumberID(), simpleName);

        throw new IllegalArgumentException(errmsg);
    }

    /**
     * Returns the float value associated with the specified {@code EntryIFD} input.
     *
     * @param entry
     * the EntryIFD object to retrieve
     * @return the tag's value as a float
     */
    public static float getFloatValue(EntryIFD entry)
    {
        return toNumericValue(entry).floatValue();
    }

    /**
     * Returns the double value associated with the specified {@code EntryIFD} input.
     *
     * @param entry
     * the EntryIFD object to retrieve
     * @return the tag's value as a double
     */
    public static double getDoubleValue(EntryIFD entry)
    {
        return toNumericValue(entry).doubleValue();
    }

    /**
     * Returns a Date object if the tag is marked as a potential date entry.
     *
     * @param entry
     * the EntryIFD object containing the date value to parse
     * @return a Date object if present and valid
     *
     * @throws IllegalArgumentException
     * if the entry is not marked as a date hint, or its value cannot be parsed as a valid
     * Date
     */
    public static Date getDate(EntryIFD entry)
    {
        Taggable tag = entry.getTag();

        if (tag.getHint() == TagHint.HINT_DATE)
        {
            Object obj = entry.getData();

            if (obj instanceof String)
            {
                Date parsed = DateParser.convertToDate((String) obj);

                if (parsed != null)
                {
                    return parsed;
                }
            }

            throw new IllegalArgumentException(String.format("Entry [%s (0x%04X)] could not be parsed as a valid date in directory [%s]", tag, tag.getNumberID(), tag.getDirectoryType().getDescription()));
        }

        else
        {
            throw new IllegalArgumentException(String.format("Entry [%s (0x%04X)] is not marked with a date hint", tag, tag.getNumberID()));
        }
    }

    /**
     * Converts the value of an IFD entry into a numeric form.
     *
     * <p>
     * If the entry's data is a {@link Number}, it is returned directly. Otherwise, it will throw an
     * {@link IllegalArgumentException} to signal that the entry does not contain a numeric value.
     * </p>
     *
     * @param entry
     * the EntryIFD object
     * @return the numeric value as a Number
     *
     * @throws IllegalArgumentException
     * if the entry is not a valid numeric type
     */
    private static Number toNumericValue(EntryIFD entry)
    {
        Object obj = entry.getData();

        if (obj instanceof Number)
        {
            return (Number) obj;
        }

        String errmsg = String.format("Entry [%s (0x%04X)] is not a valid numeric type in directory [%s]. Found [%s]",
                entry.getTag(), entry.getTag().getNumberID(), entry.getTag().getDirectoryType().getDescription(),
                (obj == null ? "null" : obj.getClass().getSimpleName()));

        throw new IllegalArgumentException(errmsg);
    }

    // ---------------------------------------------------------------------------------------------
    // Refactored String Conversion Logic
    // ---------------------------------------------------------------------------------------------

    /**
     * Converts the value of an IFD entry into a string, applying any hint-based interpretation.
     *
     * @param entry
     * the EntryIFD object
     * @return the string representation of the entry’s value
     */
    public static String toStringValue(EntryIFD entry)
    {
        Object obj = entry.getData();
        Taggable tag = entry.getTag();
        TifFieldType type = entry.getFieldType();

        if (obj == null)
        {
            return "";
        }

        else if (obj instanceof RationalNumber)
        {
            return decodeRationalValue((RationalNumber) obj);
        }

        else if (obj instanceof Number)
        {
            return decodeNumberValue(obj);
        }

        else if (obj instanceof byte[])
        {
            return decodeByteArrayValue((byte[]) obj, tag);
        }

        else if (obj instanceof int[])
        {
            return decodeIntArrayValue((int[]) obj, tag, type);
        }

        else if (obj instanceof String)
        {
            return decodeStringValue((String) obj, tag);
        }

        else
        {
            LOGGER.info("Entry [" + entry.getTag() + "] not processed. Contact the developer for investigation");
            return "";
        }
    }

    private static String decodeRationalValue(RationalNumber rational)
    {
        // Utilizes the RationalNumber's logic to produce a simple, human-readable string.
        return rational.toSimpleString(true);
    }

    private static String decodeNumberValue(Object obj)
    {
        // Simple conversion for standard numeric primitives (Integer, Long, Float, Double)
        return obj.toString();
    }

    private static String decodeStringValue(String value, Taggable tag)
    {
        String decoded = value.trim();

        if (tag.getHint() == TagHint.HINT_DATE)
        {
            Date date = DateParser.convertToDate(decoded);
            decoded = (date != null) ? date.toString() : decoded;
        }
        
        return decoded;
    }

    private static String decodeIntArrayValue(int[] ints, Taggable tag, TifFieldType type)
    {
        if (tag.getHint() == TagHint.HINT_UCS2)
        {
            byte[] b = new byte[ints.length];

            for (int i = 0; i < ints.length; i++)
            {
                b[i] = (byte) ints[i];
            }

            return new String(b, StandardCharsets.UTF_16LE).replace("\u0000", "").trim();
        }

        else if (type == TifFieldType.TYPE_SHORT_U || type == TifFieldType.TYPE_SHORT_S)
        {
            // Array representation for unsigned/signed short lists
            return Arrays.toString(ints);
        }
        
        // Fallback for other int arrays
        return Arrays.toString(ints);
    }

    private static String decodeByteArrayValue(byte[] bytes, Taggable tag)
    {
        if (tag.getHint() == TagHint.HINT_MASK)
        {
            return "[Masked items]";
        }

        else if (tag.getHint() == TagHint.HINT_BYTE)
        {
            return ByteValueConverter.toHex(bytes);
        }

        else if (tag.getHint() == TagHint.HINT_ENCODED_STRING) // Used for UserComment, GPSMethod
        {
            return decodeUserCommentString(bytes);
        }

        else
        {
            // Default for TYPE_ASCII
            return new String(ByteValueConverter.readFirstNullTerminatedByteArray(bytes), StandardCharsets.US_ASCII);
        }
    }

    /**
     * Decodes the raw byte array of a field like {@code EXIF_USER_COMMENT} or
     * {@code GPS_PROCESSING_METHOD}. These fields start with an 8-byte character set identifier,
     * for example: {@code ASCII\0\0\0} or {@code UTF-8\0\0\0} followed by the encoded data.
     *
     * @param data
     * the byte array containing the 8-byte identifier and the data body
     * @return the decoded, null-terminated, and trimmed string. Returns an empty string if the
     * input is null, too short, or contains no data after the identifier
     */
    private static String decodeUserCommentString(final byte[] data)
    {
        /* Header length is always 8 bytes, including paddings */
        final int len = ENCODING_HEADER_LENGTH;

        if (data == null || data.length < len)
        {
            return "";
        }

        /* Example for realHeaderStr: ASCII\0\0\0 */
        byte[] realHeaderBytes = Arrays.copyOf(data, len);
        String realHeaderStr = new String(realHeaderBytes, StandardCharsets.US_ASCII);
        Charset charset = ENCODING_MAP.get(realHeaderStr);

        if (charset == null)
        {
            // Check if the bytes are all nulls (UNDEFINED)
            if (Arrays.equals(realHeaderBytes, UNDEFINED_IDENTIFIER_BYTES))
            {
                // Assign UTF_8 by default, retrieving from the map
                charset = ENCODING_MAP.get(UNDEFINED_IDENTIFIER);
            }

            else
            {
                // Fallback for unknown identifier
                charset = StandardCharsets.UTF_8;
                LOGGER.warn("Encoded byte array tag has unknown encoding identifier [" + realHeaderStr + "]");
            }
        }
        
        // Extract data body (starting after the 8-byte header)
        byte[] commentBody = Arrays.copyOfRange(data, len, data.length);
        
        // Remove trailing nulls/padding before decoding
        byte[] cleaned = ByteValueConverter.readFirstNullTerminatedByteArray(commentBody);

        return new String(cleaned, charset).trim();
    }
}