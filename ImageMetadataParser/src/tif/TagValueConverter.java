package tif;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
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
    public static String toStringValue2(EntryIFD entry)
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

        throw new IllegalArgumentException(String.format("Tag [%s] has unsupported data type [%s]", entry.getTag(), obj.getClass().getName()));
        // return ByteValueConverter.toHex(entry.getByteArray());
    }

    // Remove
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
            if (hint == TagHint.HINT_DATE)
            {
                Date date = DateParser.convertToDate(((String) obj).trim());

                if (date != null)
                {
                    return date.toString();
                }
            }
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

                case HINT_BYTE:
                    StringBuilder sb = new StringBuilder();

                    for (byte b : bytes)
                    {
                        sb.append(b);
                    }

                    return sb.toString();

                case HINT_MASK:
                    return "[Masked items]";

                default:
                    // throw new IllegalArgumentException("Entry [" + entry.getTag() + "] has byte[]
                    // data without a supported hint (found: " + hint + ")");
                    return ByteValueConverter.toHex(bytes);
            }
        }

        System.out.printf("%s%n", entry.getTag());
        System.out.printf("%s%n", obj.getClass().getSimpleName());

        // throw new IllegalArgumentException("Entry [" + entry.getTag() + "] has unsupported data
        // type: " + obj.getClass().getName());
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
     *         if the entry does not contain a numeric value
     */
    public static Number toNumericValue(EntryIFD entry)
    {
        Object obj = entry.getData();

        if (obj instanceof Number)
        {
            return (Number) obj;
        }

        throw new IllegalArgumentException("Entry [" + entry.getTag() + "] does not contain a numeric value (found: " + (obj == null ? "null" : obj.getClass().getSimpleName()) + ")");
    }

    /**
     * Returns the integer value associated with the specified tag.
     *
     * <p>
     * If the tag is missing or the entry is not numeric, this method throws an exception, since
     * numeric values are considered required when calling this method.
     * </p>
     *
     * @param tag
     *        the enumeration tag to retrieve
     * @return the tag's value as an int
     *
     * @throws IllegalArgumentException
     *         if the tag is unknown or not numeric
     */
    public static int getIntValue(Taggable tag)
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
    public static long getLongValue(Taggable tag)
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
    public static float getFloatValue(Taggable tag)
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
    public static double getDoubleValue(Taggable tag)
    {
        return getNumericValue(tag).doubleValue();
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
    private static Number getNumericValue(Taggable tag)
    {
        Optional<EntryIFD> opt = findEntryByTag(tag);

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