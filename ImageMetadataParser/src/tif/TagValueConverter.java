package tif;

import java.nio.charset.StandardCharsets;
import common.ByteValueConverter;
import common.DateParser;
import common.RationalNumber;
import tif.DirectoryIFD.EntryIFD;

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
            if (hint == TagHint.HINT_DATE)
            {
                return DateParser.convertToDate((String) obj).toString();
            }

            return ((String) obj).trim();
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

        //throw new IllegalArgumentException("Entry [" + entry.getTag() + "] has unsupported data type: " + obj.getClass().getName());
        return ByteValueConverter.toHex(entry.getByteArray());
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

}