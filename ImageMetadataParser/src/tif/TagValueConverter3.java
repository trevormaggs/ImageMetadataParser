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
public final class TagValueConverter3
{
    private static final LogFactory LOGGER = LogFactory.getLogger(TagValueConverter3.class);
    private static final String ASCII_IDENTIFIER2 = "ASCII\0\0\0";
    private static final String UTF8_IDENTIFIER2 = "UTF-8\0\0\0";
    private static final String UNDEFINED_IDENTIFIER2 = "\0\0\0\0\0\0\0\0";
    private static final String JIS_IDENTIFIER2 = "JIS\0\0\0\0\0";

    private static final Map<String, Charset> ENCODING_MAP;

    static
    {
        ENCODING_MAP = new HashMap<String, Charset>()
        {
            {
                put(ASCII_IDENTIFIER2, StandardCharsets.US_ASCII);
                put(UTF8_IDENTIFIER2, StandardCharsets.UTF_8);
                put(UNDEFINED_IDENTIFIER2, StandardCharsets.UTF_8);
                put(JIS_IDENTIFIER2, Charset.forName("Shift_JIS"));
            }
        };

        // Note: Shift_JIS (or SJIS) is the common Java Charset name for JIS encoding in Exif/TIFF
    }

    /**
     * Default constructor is unsupported and will always throw an exception.
     *
     * @throws UnsupportedOperationException
     *         to indicate that instantiation is not supported
     */
    private TagValueConverter3()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

    /**
     * Checks if the given TifFieldType can be converted to a Java int (32-bit signed) without data
     * loss or sign mis-interpretation.
     *
     * @param type
     *        the TIFF field type
     * @return true if the conversion is safe and lossless, otherwise false explicitly for the types
     *         that cause loss of precision
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
     *        the EntryIFD object to retrieve
     * @return the tag's value as an integer
     *
     * @throws IllegalArgumentException
     *         if the entry's value is not numeric (i.e. ASCII or UNDEFINED) or if its TIFF type
     *         (i.e. unsigned LONG or RATIONAL) is not convertible to a Java 32-bit int safely and
     *         losslessly
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
     *        the EntryIFD object containing the array
     * @return the tag's value as an {@code int[]} array
     * 
     * @throws IllegalArgumentException
     *         if the entry does not contain an int[] array
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
     *        the EntryIFD object to retrieve
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
     *        the EntryIFD object containing the array
     * @return the tag's value as a {@code long[]} array
     * 
     * @throws IllegalArgumentException
     *         if the entry does not contain a long[] array
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
     * Returns the float value associated with the specified {@code EntryIFD} input.
     *
     * @param entry
     *        the EntryIFD object to retrieve
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
     *        the EntryIFD object to retrieve
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
     *        the EntryIFD object containing the date value to parse
     * @return a Date object if present and valid
     *
     * @throws IllegalArgumentException
     *         if the entry is not marked as a date hint, or its value cannot be parsed as a valid
     *         Date
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
     *        the EntryIFD object
     * @return the numeric value as a Number
     *
     * @throws IllegalArgumentException
     *         if the entry is not a valid numeric type
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

    /**
     * Converts the value of an IFD entry into a string, applying any hint-based interpretation.
     *
     * @param entry
     *        the EntryIFD object
     * @return the string representation of the entry’s value
     */
    public static String toStringValue(EntryIFD entry)
    {
        String decoded = "";
        Object obj = entry.getData();
        Taggable tag = entry.getTag();
        TifFieldType type = entry.getFieldType();

        if (obj == null)
        {
            return "";
        }

        else if (obj instanceof Number)
        {
            decoded = obj.toString();
        }

        else if (obj instanceof RationalNumber)
        {
            decoded = ((RationalNumber) obj).toSimpleString(true);
        }

        else if (obj instanceof byte[])
        {
            byte[] bytes = (byte[]) obj;

            if (tag.getHint() == TagHint.HINT_MASK)
            {
                decoded = "[Masked items]";
            }

            else if (tag.getHint() == TagHint.HINT_BYTE)
            {
                decoded = ByteValueConverter.toHex(bytes);
            }

            else if (tag.getHint() == TagHint.HINT_ENCODED_STRING) // Assuming you define this hint
            {
                return decodeUserCommentString(bytes);
            }

            else
            {
                decoded = new String(ByteValueConverter.readFirstNullTerminatedByteArray(bytes), StandardCharsets.US_ASCII);
            }
        }

        else if (obj instanceof int[])
        {
            int[] ints = (int[]) obj;

            if (tag.getHint() == TagHint.HINT_UCS2)
            {
                byte[] b = new byte[ints.length];

                for (int i = 0; i < ints.length; i++)
                {
                    b[i] = (byte) ints[i];
                }

                decoded = new String(b, StandardCharsets.UTF_16LE).replace("\u0000", "").trim();
            }

            else if (type == TifFieldType.TYPE_SHORT_U)
            {
                decoded = Arrays.toString(ints);
            }
        }

        else if (obj instanceof String)
        {
            decoded = ((String) obj).trim();

            if (tag.getHint() == TagHint.HINT_DATE)
            {
                Date date = DateParser.convertToDate(decoded);
                decoded = (date != null) ? date.toString() : decoded;
            }
        }

        else
        {
            LOGGER.info("Entry [" + entry.getTag() + "] not processed. Contact the developer for investigation");

            System.err.printf("%-30s%-10s\t%s\t%s\t%n", entry.getTag(), type, obj.getClass().getSimpleName(), tag.getHint());
        }

        return decoded;
    }

    /**
     * Decodes the raw byte array of EXIF_USER_COMMENT, which starts with an 8-byte
     * character set identifier (e.g., "ASCII\0\0\0" or "UTF-8\0\0\0").
     *
     * @param data
     *        The byte array containing the identifier and the comment body.
     * @return The decoded, trimmed string. Returns an empty string if no content is found.
     */
    private static String decodeUserCommentString(final byte[] data)
    {
        int len = UNDEFINED_IDENTIFIER2.getBytes(StandardCharsets.US_ASCII).length;
        
        if (data == null || data.length <= len)
        {
            return "";
        }

        // 1. Extract the 8-byte identifier
        byte[] identifierBytes = Arrays.copyOf(data, len);

        // Use the trimmed key for map lookup (e.g., "ASCII", "UTF-8")
        String identifierKey = new String(identifierBytes, StandardCharsets.US_ASCII).trim();

        // 2. Determine the Charset
        Charset charset = ENCODING_MAP.get(identifierKey);

        // FIX: The original logic for UNDEFINED was flawed.
        // If the map lookup fails and the bytes match the UNDEFINED pattern,
        // use the map's default (UTF-8).
        if (charset == null)
        {
            // Check if the bytes are all nulls (UNDEFINED)
            if (Arrays.equals(identifierBytes, UNDEFINED_IDENTIFIER2.getBytes()))
            {
                // Use the defined default for UNDEFINED (which is UTF-8 in the map)
                charset = StandardCharsets.UTF_8;
            }
            
            else
            {
                // Fallback for unknown identifier, default to a robust standard (UTF-8)
                LOGGER.warn("EXIF_USER_COMMENT has unknown encoding identifier: " + identifierKey);
                charset = StandardCharsets.UTF_8;
            }
        }
        // Note: No need for the 'else if' block from the original code now.

        // 3. Extract the comment body (bytes starting at index 8)
        byte[] commentBody = Arrays.copyOfRange(data, len, data.length);

        // 4. Decode the comment body (remove trailing nulls/padding first)
        byte[] trimmedBody = ByteValueConverter.readFirstNullTerminatedByteArray(commentBody);

        return new String(trimmedBody, charset).trim();
    }
}