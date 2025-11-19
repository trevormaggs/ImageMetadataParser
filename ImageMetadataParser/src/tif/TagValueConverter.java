package tif;

import java.nio.charset.StandardCharsets;
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
                    return new String(ByteValueConverter.readFirstNullTerminatedByteArray(bytes), StandardCharsets.US_ASCII);

                case HINT_BYTE:
                    StringBuilder sb = new StringBuilder();

                    for (byte b : bytes)
                    {
                        sb.append(String.format("%02X", b));
                    }

                    return sb.toString();

                case HINT_MASK:
                    return "[Masked items]";

                default:
                    return ByteValueConverter.toHex(bytes);
            }
        }

        System.out.printf("%s%n", entry.getTag());
        System.out.printf("%s%n", obj.getClass().getSimpleName());

        // throw new IllegalArgumentException(String.format("Tag [%s] has unsupported data type
        // [%s]", entry.getTag(), obj.getClass().getName()));
        return ByteValueConverter.toHex(entry.getByteArray());
    }

    /**
     * Returns a Date object if the tag is marked as a potential date entry.
     *
     * @param tag
     *        the enumeration tag to obtain the value for
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
     * Returns the integer value associated with the specified {@code EntryIFD} input.
     *
     * @param entry
     *        the EntryIFD object to retrieve
     * @return the tag's value as an integer
     */
    public static int getIntValue(EntryIFD entry)
    {
        if (entry.getFieldType() == TifFieldType.TYPE_LONG_S)
        {
            return toNumericValue(entry).intValue();
        }

        throw new IllegalArgumentException("Entry [" + entry.getTag() + "] is not a valid SLONG type (found: " + entry.getFieldType() + ")");
    }

    public static boolean validateIntValue(EntryIFD entry)
    {
        return canConvertToInt(entry.getFieldType());
    }

    public static int getIntValue2(EntryIFD entry)
    {
        Number num = toNumericValue(entry);

        return num.intValue();
    }

    public static int getIntValue3(EntryIFD entry)
    {
        // 1. Ensure the entry data is a numeric object
        Number number = toNumericValue(entry);

        // 2. Perform the strict type check
        if (!canConvertToInt(entry.getFieldType()))
        {
            throw new IllegalArgumentException(String.format("Entry [%s] has field type [%s], which is not a safe, lossless type for conversion to integer.", entry.getTag(), entry.getFieldType()));
        }

        // 3. Safe conversion
        return number.intValue();
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
        System.out.printf("LOOK0 %s%n", entry.getTag());
        System.out.printf("LOOK1 %s%n", toNumericValue(entry).longValue());

        if (entry.getFieldType() == TifFieldType.TYPE_LONG_U)
        {
            return toNumericValue(entry).longValue();
        }

        throw new IllegalArgumentException("Entry [" + entry.getTag() + "] is not a valid LONG type (found: " + entry.getFieldType() + ")");
    }

    public static long getLongValue2(EntryIFD entry)
    {
        Number num = toNumericValue(entry);

        return num.longValue();
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
     * Converts the value of an IFD entry into a numeric form.
     *
     * <p>
     * If the entry's data is a {@link Number}, it is returned directly. Otherwise, it will throw an
     * {@link IllegalArgumentException} to signal that the entry does not contain a numeric value.
     * </p>
     *
     * <p>
     * This method is used internally by numeric accessors. It throws an exception if the entry is
     * missing or not numeric.
     * </p>
     * 
     * @param entry
     *        the EntryIFD object
     * @return the numeric value as a Number
     *
     * @throws NullPointerException
     *         if the input parameter is null
     * @throws IllegalArgumentException
     *         if the entry is not a valid numeric type
     */
    private static Number toNumericValue(EntryIFD entry)
    {
        if (entry == null)
        {
            throw new NullPointerException("Entry cannot be null");
        }

        Object obj = entry.getData();

        if (entry.getFieldType().isNumber())
        {
            if (obj instanceof Number)
            {
                return (Number) obj;
            }
        }

        throw new IllegalArgumentException("Entry [" + entry.getTag() + "] is not a valid numeric type (found: " + (obj == null ? "null" : obj.getClass().getSimpleName()) + ")");
    }

    /**
     * Checks if the given TifFieldType can be converted to a Java int (32-bit signed) without data
     * loss or sign misinterpretation.
     *
     * @param type
     *        the TIFF field type
     * @return true if the conversion is safe and lossless
     */
    public static boolean canConvertToInt(TifFieldType type)
    {
        // Note: Java 8 does not support the switch expression (switch -> result;).
        // We use a traditional switch statement or if/else if structure instead.

        switch (type)
        {
            // Safe, lossless conversions (data fits within 32-bit signed range):
            case TYPE_BYTE_U:
            case TYPE_BYTE_S:
            case TYPE_SHORT_U:
            case TYPE_SHORT_S:
            case TYPE_LONG_S:
                return true;

            // Unsafe conversions (data loss or misinterpretation risk):
            case TYPE_LONG_U: // Unsigned 32-bit exceeds Java int max positive value
            case TYPE_RATIONAL_U: // Loss of precision/truncation
            case TYPE_RATIONAL_S: // Loss of precision/truncation
            case TYPE_FLOAT: // Loss of precision/truncation
            case TYPE_DOUBLE: // Loss of precision/truncation
                return false;

            // Handle all other non-numeric or unsupported types as false:
            default:
                return false;
        }
    }

}