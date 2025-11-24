package tif;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import common.ByteValueConverter;
import common.DateParser;
import common.RationalNumber;
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
public final class TagValueConverter
{
    /**
     * Default constructor is unsupported and will always throw an exception.
     *
     * @throws UnsupportedOperationException
     *         to indicate that instantiation is not supported
     */
    private TagValueConverter()
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
     * Converts the value of an IFD entry into a string, applying any hint-based interpretation.
     *
     * <p>
     * This method handles the following cases:
     * </p>
     * <ul>
     * <li>{@link Number} or {@link Byte} → string form of the number</li>
     * <li>{@link String} → trimmed string (parsed as a date if {@link TagHint#HINT_DATE})</li>
     * <li>{@link RationalNumber} → simplified fraction string</li>
     * <li>{@code byte[]} → interpretation depends on the {@link TagHint}:
     * <ul>
     * <li>{@link TagHint#HINT_STRING} → UTF-8 null-terminated string</li>
     * <li>{@link TagHint#HINT_BYTE} → concatenation of byte values</li>
     * <li>{@link TagHint#HINT_MASK} → literal {@code "[Masked items]"}</li>
     * </ul>
     * </li>
     * </ul>
     *
     * <p>
     * If the value is not one of the above supported types, this method throws
     * {@link IllegalArgumentException}.
     * </p>
     *
     * @param entry
     *        the EntryIFD object
     * @return the string representation of the entry’s value
     *
     * @throws IllegalArgumentException
     *         if the entry’s value type cannot be converted
     */
    public static String toStringValue(EntryIFD entry)
    {
        Object obj = entry.getData();
        TagHint hint = entry.getTag().getHint();

        if (obj == null)
        {
            return "";
        }

        toStringByType(entry);
        // System.out.printf("LOOK %s%n", entry);

        if (obj instanceof Byte || obj instanceof Number)
        {
            return obj.toString();
        }

        if (obj instanceof String)
        {
            String value = ((String) obj).trim();

            if (hint == TagHint.HINT_DATE)
            {
                Date date = DateParser.convertToDate(value);

                return (date != null) ? date.toString() : value;
            }

            return value;
        }

        if (obj instanceof RationalNumber)
        {
            return ((RationalNumber) obj).toSimpleString(true);
        }

        if (obj instanceof byte[])
        {
            byte[] bytes = (byte[]) obj;

            switch (hint)
            {
                case HINT_STRING:
                    return new String(ByteValueConverter.readFirstNullTerminatedByteArray(bytes), StandardCharsets.UTF_8);

                case HINT_MASK:
                    return "[Masked items]";

                default: // This also covers HINT_BYTE.
                    return ByteValueConverter.toHex(bytes);
            }
        }

        if (obj instanceof int[])
        {
            int[] ints = (int[]) obj;

            switch (hint)
            {
                /*
                 * If the original type is a TYPE_BYTE_U, convert the array of
                 * integers previously allocated as unsigned bytes back to raw bytes.
                 */
                case HINT_UCS2:
                    byte[] b = new byte[ints.length];

                    for (int i = 0; i < ints.length; i++)
                    {
                        b[i] = (byte) ints[i];
                    }

                    String decodedStr = new String(b, StandardCharsets.UTF_16LE);

                    return decodedStr.replace("\u0000", "").trim();

                case HINT_MASK:
                    return "[Masked items]";

                default:
                    // Fallback for TYPE_BYTE_U arrays that aren't strings
                    return ByteValueConverter.toHex(ByteValueConverter.convertIntsToBytes(ints));
            }
        }

        // Debugging only
        return ByteValueConverter.toHex(entry.getByteArray());

        // throw new IllegalArgumentException(String.format("Entry [%s] has unsupported data type
        // [%s] (TIFF Type: %s)", entry.getTag(), obj.getClass().getName(), entry.getFieldType()));
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

    public static String toStringByType2(EntryIFD entry)
    {
        String decoded = "";
        Object obj = entry.getData();
        TagHint hint = entry.getTag().getHint();
        TifFieldType type = entry.getFieldType();

        if (type == TifFieldType.TYPE_BYTE_U || type == TifFieldType.TYPE_UNDEFINED)
        {
            System.out.printf("BYTE ARRAY: %s\t%s\t%s\t%s%n", entry.getTag(), decoded, obj.getClass().getSimpleName(), hint);

            decoded = getByteData(entry);

            // System.out.printf("LOOK %s%n", entry);
        }

        else if (type == TifFieldType.TYPE_ASCII)
        {
        }

        else if (type == TifFieldType.TYPE_SHORT_U)
        {
            // EXIF_SUBJECT_AREA
            decoded = getShortData(entry);

            System.out.printf("SHORT ARRAY: %s\t%s\t%s%n", entry.getTag(), decoded, obj.getClass().getSimpleName());
        }

        else if (type == TifFieldType.TYPE_LONG_U)
        {
        }

        else if (type == TifFieldType.TYPE_RATIONAL_U)
        {
        }

        return decoded;
    }

    public static String getShortData(EntryIFD entry)
    {
        Object obj = entry.getData();

        if (obj instanceof Short || obj instanceof Number)
        {
            return obj.toString();
        }

        else if (obj instanceof int[])
        {
            int[] ints = (int[]) obj;

            return Arrays.toString(ints);
        }

        return "";
    }

    public static String getByteData(EntryIFD entry)
    {
        Object obj = entry.getData();

        if (obj instanceof Byte || obj instanceof Number)
        {
            return obj.toString();
        }

        else if (obj instanceof byte[])
        {
            byte[] bytes = (byte[]) obj;

            switch (entry.getTag().getHint())
            {
                case HINT_STRING:

                    System.out.printf("LOOK %s%n", new String(bytes, StandardCharsets.UTF_8).replace("\u0000", "").trim());

                    return new String(ByteValueConverter.readFirstNullTerminatedByteArray(bytes), StandardCharsets.UTF_8);

                case HINT_MASK:
                    return "[Masked items]";

                default: // This also covers HINT_BYTE.
                    return ByteValueConverter.toHex(bytes);
            }
        }

        else if (obj instanceof int[])
        {
            int[] ints = (int[]) obj;

            switch (entry.getTag().getHint())
            {
                case HINT_UCS2:
                    byte[] b = new byte[ints.length];

                    for (int i = 0; i < ints.length; i++)
                    {
                        b[i] = (byte) ints[i];
                    }

                    return new String(b, StandardCharsets.UTF_16LE).replace("\u0000", "").trim();

                case HINT_MASK:
                    return "[Masked items]";

                default:
                    return ByteValueConverter.toHex(ByteValueConverter.convertIntsToBytes(ints));
            }
        }

        else
        {
            return "";
        }
    }

    public static String toStringByType(EntryIFD entry)
    {
        String decoded = "";
        Object obj = entry.getData();
        TagHint hint = entry.getTag().getHint();
        TifFieldType type = entry.getFieldType();
        // System.out.printf("TEST %s\t%s\t%s\t%s%n", entry.getTag(), decoded,
        // obj.getClass().getSimpleName(), hint);

        if (obj instanceof Number)
        {
            decoded = obj.toString();
        }

        else if (obj instanceof byte[])
        {
            byte[] bytes = (byte[]) obj;

            if (hint == TagHint.HINT_BYTE)
            {
                decoded = ByteValueConverter.toHex(bytes);
            }

            else
            {
                decoded = new String(ByteValueConverter.readFirstNullTerminatedByteArray(bytes), StandardCharsets.US_ASCII);
            }
        }

        else if (obj instanceof int[])
        {
            int[] ints = (int[]) obj;

            if (hint == TagHint.HINT_UCS2)
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

            if (hint == TagHint.HINT_DATE)
            {
                Date date = DateParser.convertToDate(decoded);
                decoded = (date != null) ? date.toString() : decoded;
            }
        }

        else if (obj instanceof RationalNumber[])
        {
            RationalNumber[] arr = ((RationalNumber[]) obj);

//            for (RationalNumber rat : arr)
//                System.out.printf("TEST %s\t%s\t%s\t%s\t\t%s%n", entry.getTag(), type, obj.getClass().getSimpleName(), hint, rat.toSimpleString(false));
        }
        
        else        System.out.printf("TEST %s\t%s\t%s\t%s\t\t%s%n", entry.getTag(), type, obj.getClass().getSimpleName(), hint, decoded);

        return decoded;
    }
}