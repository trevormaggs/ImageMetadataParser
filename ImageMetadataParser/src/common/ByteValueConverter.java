package common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class providing static methods for converting raw byte arrays to primitive types,
 * strings, and {@link RationalNumber} representations, especially in the context of image metadata
 * parsing, for example: TIFF or PNG.
 *
 * <p>
 * This class is non-instantiable and thread-safe due to its state-less and static design.
 * </p>
 * 
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public final class ByteValueConverter
{
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    /**
     * Default constructor is unsupported and will always throw an exception.
     *
     * @throws UnsupportedOperationException
     *         to indicate that instantiation is not supported
     */
    private ByteValueConverter()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

    /**
     * Checks if the specified byte array contains a null (0x00) byte.
     * 
     * @param data
     *        the byte array to examine
     * @return true if a null byte (0x00) is found, false otherwise
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     */
    public static boolean containsNullByte(byte[] data)
    {
        if (data == null)
        {
            throw new NullPointerException("Data cannot be null");
        }

        for (byte b : data)
        {
            if (b == 0)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Extracts a byte array segment terminated by the first null ({@code 0x00}) byte.
     *
     * <p>
     * This method searches for the first occurrence of a null byte ({@code 0x00}) in the specified
     * {@code data} array. If a null byte is found, it returns a new array containing all bytes from
     * the beginning of {@code data} up to, but not including, the first null byte. Any bytes after
     * the first null terminator are ignored.
     * </p>
     *
     * If no null byte is found in the {@code data} array, a copy of the entire original
     * {@code data} array is returned.
     *
     * @param data
     *        the input byte array to be searched for a null terminator
     * @return a new byte array containing the segment before the first null terminator, or a copy
     *         of the entire original array if no null terminator is present
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     */
    public static byte[] readFirstNullTerminatedByteArray(byte[] data)
    {
        if (data == null)
        {
            throw new NullPointerException("Data cannot be null");
        }

        int nullIndex = data.length;

        for (int i = 0; i < data.length; i++)
        {
            if (data[i] == 0)
            {
                nullIndex = i;
                break;
            }
        }

        return Arrays.copyOf(data, nullIndex);
    }

    /**
     * Reads a string from a byte array starting at the specified offset. The string is terminated
     * by the first null (0x00) byte, or by the end of the array if no null byte is found.
     *
     * @param data
     *        the source byte array
     * @param offset
     *        the starting index
     * @param charset
     *        the charset to decode the string
     * @return the decoded string, which runs until the first null byte (exclusive) or the end of
     *         the array. Returns an empty string if offset equals the array length
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     * @throws IndexOutOfBoundsException
     *         if the offset is out of bounds
     */
    public static String readNullTerminatedString(byte[] data, int offset, Charset charset)
    {
        if (data == null)
        {
            throw new NullPointerException("Data cannot be null");
        }

        if (offset < 0 || offset > data.length)
        {
            throw new IndexOutOfBoundsException("Offset out of bounds [" + offset + "]. Must be between 0 and [" + data.length + "]");
        }

        if (offset == data.length)
        {
            return "";
        }

        int pos = offset;

        while (pos < data.length && data[pos] != 0)
        {
            pos++;
        }

        return new String(Arrays.copyOfRange(data, offset, pos), charset);
    }

    /**
     * Splits a byte array of null-delimited strings into individual strings using UTF-8.
     *
     * @param data
     *        the byte array to split
     * @return an array of strings
     */
    public static String[] splitNullDelimitedStrings(byte[] data)
    {
        return splitNullDelimitedStrings(data, StandardCharsets.UTF_8);
    }

    /**
     * Splits a byte array of null-delimited strings into individual strings using the specified
     * charset.
     *
     * @param data
     *        the byte array to split
     * @param format
     *        the character encoding
     * @return an array of strings
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     */
    public static String[] splitNullDelimitedStrings(byte[] data, Charset format)
    {
        if (data == null)
        {
            throw new NullPointerException("Data cannot be null");
        }

        int start = 0;
        List<String> result = new ArrayList<>();

        for (int i = 0; i < data.length; i++)
        {
            if (data[i] == 0)
            {
                if (i > start)
                {
                    result.add(new String(data, start, i - start, format));
                }

                start = i + 1;
            }
        }

        /*
         * Sometimes, the last string portion lacks a valid null terminator,
         * this will make sure the last part is added.
         */
        if (start < data.length)
        {
            result.add(new String(data, start, data.length - start, format));
        }

        return result.toArray(new String[0]);
    }

    /**
     * Converts a Java {@code int} array to a {@code byte} array by encoding each 32-bit integer
     * into four bytes using Little Endian byte order.
     *
     * <p>
     * This process is common when serialising 32-bit integer data (such as TIFF LONG values) for
     * storage in a byte-oriented file format where the least significant byte is stored first.
     * </p>
     *
     * @param ints
     *        the int[] array to convert. Each element is 4 bytes wide
     * @return a new byte[] array, which is exactly four times the length of the input array.
     *         Returns an empty array if the input ints array is null
     */
    public static byte[] convertIntsToBytes(int[] ints)
    {
        if (ints == null)
        {
            return new byte[0];
        }

        final byte[] newBytes = new byte[ints.length * 4];

        for (int i = 0; i < ints.length; i++)
        {
            final int value = ints[i];
            final int byteIndex = i * 4;

            newBytes[byteIndex] = (byte) value; // LSB
            newBytes[byteIndex + 1] = (byte) (value >> 8);
            newBytes[byteIndex + 2] = (byte) (value >> 16);
            newBytes[byteIndex + 3] = (byte) (value >> 24); // MSB
        }

        return newBytes;
    }

    /**
     * Converts a byte array to a hexadecimal string representation.
     *
     * @param data
     *        the input byte array
     * @return a hexadecimal string
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     */
    public static String toHex(byte[] data)
    {
        if (data == null)
        {
            throw new NullPointerException("Data bytes cannot be null");
        }

        StringBuilder sb = new StringBuilder(data.length * 5);

        for (int j = 0; j < data.length; j++)
        {
            int v = data[j] & 0xFF;

            sb.append("0x").append(HEX_ARRAY[v >>> 4]).append(HEX_ARRAY[v & 0x0F]);

            if (j < data.length - 1)
            {
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    /**
     * Reads the entire contents of the file at the given {@link Path} into a byte array.
     * 
     * @param filePath
     *        a Path instance encapsulating the image file
     * @return a byte array of the file's raw contents, or empty if file is zero-length
     * 
     * @throws IOException
     *         if the file cannot be read
     */
    public static byte[] readAllBytes(Path filePath) throws IOException
    {
        try (InputStream inputStream = Files.newInputStream(filePath))
        {
            return readAllBytes(inputStream);
        }
    }

    /**
     * Reads all bytes from the given {@link InputStream} resource and returns them as a byte array.
     * 
     * Internally, it uses an 8 KB buffer for efficient reading, making it suitable for large
     * streams such as files or network input.
     *
     * @param stream
     *        the input stream to read from
     * @return a byte array containing all bytes read from the stream
     * 
     * @throws IOException
     *         if an I/O error occurs while reading
     */
    public static byte[] readAllBytes(InputStream stream) throws IOException
    {
        int bytesRead;
        byte[] buffer = new byte[8192];

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream())
        {
            while ((bytesRead = stream.read(buffer)) != -1)
            {
                outputStream.write(buffer, 0, bytesRead);
            }

            return outputStream.toByteArray();
        }
    }

    /**
     * Returns a signed 16-bit short based on the specified two bytes of data.
     *
     * @param data
     *        an array of 2 bytes or more
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return the 16 bit short value
     */
    public static short toShort(byte[] data, ByteOrder order)
    {
        return toShort(data, 0, order);
    }

    /**
     * Returns a signed 16-bit short based on the specified two bytes of data.
     *
     * @param data
     *        an array of 2 bytes or more
     * @param offset
     *        the offset at which the position of the byte array starts
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return the 16 bit short value
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     * @throws IndexOutOfBoundsException
     *         if the offset is out of bounds
     */
    public static short toShort(byte[] data, int offset, ByteOrder order)
    {
        if (data == null)
        {
            throw new NullPointerException("Data bytes cannot be null");
        }

        if (offset < 0 || offset + 2 > data.length)
        {
            throw new IndexOutOfBoundsException("Offset is out of bounds");
        }

        int byte0 = data[offset + 0] & 0xFF;
        int byte1 = data[offset + 1] & 0xFF;

        if (order == ByteOrder.BIG_ENDIAN)
        {
            return (short) (byte0 << 8 | byte1);
        }

        return (short) (byte1 << 8 | byte0);
    }

    /**
     * Returns an unsigned 16-bit short based on the specified two bytes of data.
     *
     * @param data
     *        an array of 2 bytes or more
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return the 16 bit short value, between 0x0000 and 0xFFFF
     */
    public static int toUnsignedShort(byte[] data, ByteOrder order)
    {
        return toUnsignedShort(data, 0, order);
    }

    /**
     * Returns an unsigned 16-bit short based on the specified two bytes of data.
     *
     * @param data
     *        an array of 2 bytes or more
     * @param offset
     *        the offset at which the position of the byte array starts
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return the 16 bit short value, between 0x0000 and 0xFFFF
     */
    public static int toUnsignedShort(byte[] data, int offset, ByteOrder order)
    {
        return toShort(data, offset, order) & 0xFFFF;
    }

    /**
     * Reads four bytes from the specified byte array and returns the result as a signed integer
     * (32-bit) value, based on the current byte ordering.
     *
     * @param data
     *        an array of 4 bytes or more
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return a signed integer value
     */
    public static int toInteger(byte[] data, ByteOrder order)
    {
        return toInteger(data, 0, order);
    }

    /**
     * Reads four bytes from the specified byte array and returns the result as a signed integer
     * (32-bit) value, based on the current byte ordering.
     *
     * @param data
     *        an array of 4 bytes or more
     * @param offset
     *        the offset at which the position of the byte array starts
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return a signed integer value
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     * @throws IndexOutOfBoundsException
     *         if the offset is out of bounds
     */
    public static int toInteger(byte[] data, int offset, ByteOrder order)
    {
        if (data == null)
        {
            throw new NullPointerException("Data bytes cannot be null");
        }

        if (offset < 0 || offset + 4 > data.length)
        {
            throw new IndexOutOfBoundsException("Offset is out of bounds");
        }

        final int byte0 = data[offset + 0] & 0xFF;
        final int byte1 = data[offset + 1] & 0xFF;
        final int byte2 = data[offset + 2] & 0xFF;
        final int byte3 = data[offset + 3] & 0xFF;

        if (order == ByteOrder.BIG_ENDIAN)
        {
            return byte0 << 24 | byte1 << 16 | byte2 << 8 | byte3;
        }

        else
        {
            return byte3 << 24 | byte2 << 16 | byte1 << 8 | byte0;
        }
    }

    /**
     * Reads a 32-bit unsigned integer value (4 bytes) from the given byte array, interpreting the
     * bytes based on the current byte ordering.
     *
     * @param data
     *        an array containing 4 bytes or more
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return an unsigned 32-bit integer value, ranging from 0x00000000 to 0xFFFFFFFF, masked as a
     *         long
     */
    public static long toUnsignedInteger(byte[] data, ByteOrder order)
    {
        return toUnsignedInteger(data, 0, order);
    }

    /**
     * Reads a 32-bit unsigned integer value (4 bytes) from the given byte array, interpreting the
     * bytes based on the current byte ordering.
     *
     * @param data
     *        an array containing 4 bytes or more
     * @param offset
     *        the offset at which the position of the byte array starts
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return an unsigned 32-bit integer value, ranging from 0x00000000 to 0xFFFFFFFF, masked as a
     *         long
     */
    public static long toUnsignedInteger(byte[] data, int offset, ByteOrder order)
    {
        return Integer.toUnsignedLong(toInteger(data, offset, order));
    }

    /**
     * Reads a 64-bit signed long value (8 bytes) from the specified byte array, interpreting the
     * bytes based on the current byte ordering.
     *
     * @param data
     *        an array containing 8 bytes or more
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return an 8-byte signed long value
     */
    public static long toLong(byte[] data, ByteOrder order)
    {
        return toLong(data, 0, order);
    }

    /**
     * Reads a 64-bit signed long value (8 bytes) from the specified byte array, interpreting the
     * bytes based on the current byte ordering.
     *
     * @param data
     *        an array containing 8 bytes or more
     * @param offset
     *        the offset at which the position of the byte array starts
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return an 8-byte signed long value
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     * @throws IndexOutOfBoundsException
     *         if the offset is out of bounds
     */
    public static long toLong(byte[] data, int offset, ByteOrder order)
    {
        if (data == null)
        {
            throw new NullPointerException("Data bytes cannot be null");
        }

        if (offset < 0 || offset + 8 > data.length)
        {
            throw new IndexOutOfBoundsException("Invalid input for toLong: byte offset is out of bounds");
        }

        long byte0 = data[offset + 0] & 0xFFL;
        long byte1 = data[offset + 1] & 0xFFL;
        long byte2 = data[offset + 2] & 0xFFL;
        long byte3 = data[offset + 3] & 0xFFL;
        long byte4 = data[offset + 4] & 0xFFL;
        long byte5 = data[offset + 5] & 0xFFL;
        long byte6 = data[offset + 6] & 0xFFL;
        long byte7 = data[offset + 7] & 0xFFL;

        if (order == ByteOrder.BIG_ENDIAN)
        {
            return byte0 << 56 | byte1 << 48 | byte2 << 40 | byte3 << 32 | byte4 << 24 | byte5 << 16 | byte6 << 8 | byte7;
        }

        return byte7 << 56 | byte6 << 48 | byte5 << 40 | byte4 << 32 | byte3 << 24 | byte2 << 16 | byte1 << 8 | byte0;
    }

    /**
     * Interprets 4 bytes from the specified position in the array as a 32-bit float, honoring the
     * specified byte order.
     * 
     * @param data
     *        an array containing 4 bytes or more
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return a float value interpreted from the byte array
     */
    public static float toFloat(byte[] data, ByteOrder order)
    {
        return toFloat(data, 0, order);
    }

    /**
     * Interprets 4 bytes from the specified position in the array as a 32-bit float, adhering to
     * the given byte order.
     * 
     * @param data
     *        an array containing 4 bytes or more
     * @param offset
     *        the offset at which the position of the byte array starts
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return a float value interpreted from the byte array
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     * @throws IndexOutOfBoundsException
     *         if the offset is out of bounds
     */
    public static float toFloat(byte[] data, int offset, ByteOrder order)
    {
        if (data == null)
        {
            throw new NullPointerException("Data bytes cannot be null");
        }

        if (offset < 0 || offset + 4 > data.length)
        {
            throw new IndexOutOfBoundsException("Invalid input for toFloat: byte offset is out of bounds");
        }

        return Float.intBitsToFloat(toInteger(data, offset, order));
    }

    /**
     * Retrieves 8 bytes from the byte array and returns the result as a double value, based on the
     * current byte ordering.
     * 
     * @param data
     *        an array containing 8 bytes or more
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return a double value interpreted from the byte array
     */
    public static double toDouble(byte[] data, ByteOrder order)
    {
        return toDouble(data, 0, order);
    }

    /**
     * Retrieves 8 bytes from the byte array and returns the result as a double value, based on the
     * current byte ordering.
     * 
     * @param data
     *        an array containing 8 bytes or more
     * @param offset
     *        the offset at which the position of the byte array starts
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return a double value interpreted from the byte array
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     * @throws IndexOutOfBoundsException
     *         if the offset is out of bounds
     */
    public static double toDouble(byte[] data, int offset, ByteOrder order)
    {
        if (data == null)
        {
            throw new NullPointerException("Data bytes cannot be null");
        }

        if (offset < 0 || offset + 8 > data.length)
        {
            throw new IndexOutOfBoundsException("Invalid input for toDouble: byte offset is out of bounds");
        }

        return Double.longBitsToDouble(toLong(data, offset, order));
    }

    /**
     * Converts 8 bytes into a {@link RationalNumber}, using signed or unsigned type.
     *
     * @param data
     *        the byte array containing numerator and denominator
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @param type
     *        whether the values should be treated as signed or unsigned
     * @return a new RationalNumber instance
     */
    public static RationalNumber toRational(byte[] data, ByteOrder order, RationalNumber.DataType type)
    {
        return toRational(data, 0, order, type);
    }

    /**
     * Reads an 8-byte segment from the specified byte array at the given offset and converts it
     * into a {@link RationalNumber} object.
     * 
     * The first four bytes represent the numerator and the next four bytes represent the
     * denominator, interpreted according to the specified byte order and data type (signed or
     * unsigned).
     *
     * @param data
     *        a byte array containing at least 8 bytes from the offset
     * @param offset
     *        the offset at which to start reading the 8-byte segment
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @param type
     *        indicates whether the values should be treated as signed or unsigned. It can only be
     *        either {@code RationalNumber.DataType.UNSIGNED} or
     *        {@code RationalNumber.DataType.SIGNED}
     * @return a new RationalNumber object
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     * @throws IndexOutOfBoundsException
     *         if the offset is out of bounds, or the array is too short to read 8 bytes starting at
     *         the offset
     */
    public static RationalNumber toRational(byte[] data, int offset, ByteOrder order, RationalNumber.DataType type)
    {
        if (data == null)
        {
            throw new NullPointerException("Input byte array cannot be null");
        }

        if (offset < 0 || offset + 8 > data.length)
        {
            throw new IndexOutOfBoundsException("Offset is out of bounds, or array is too short for 8 bytes starting at offset");
        }

        int numeratorRaw = toInteger(data, offset, order);
        int denominatorRaw = toInteger(data, offset + 4, order);

        return new RationalNumber(numeratorRaw, denominatorRaw, type);
    }

    /**
     * Convenience method for converting an entire byte array to an integer array using the
     * specified byte order.
     * 
     * @param data
     *        the input byte array
     * @param order
     *        the byte order to interpret the float values
     * @return an array of integer values
     */
    public static int[] toUnsignedShortArray(byte[] data, ByteOrder order)
    {
        return toUnsignedShortArray(data, 0, order);
    }

    /**
     * Converts a byte array to an array of unsigned 16-bit integers, honoring the specified byte
     * order.
     *
     * @param data
     *        the input byte array
     * @param offset
     *        the starting offset in the byte array
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return an integer array containing the unsigned short values
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     * @throws IllegalArgumentException
     *         if the input has an invalid length
     */
    public static int[] toUnsignedShortArray(byte[] data, int offset, ByteOrder order)
    {
        if (data == null)
        {
            throw new NullPointerException("Input byte array cannot be null");
        }

        if ((data.length - offset) % 2 != 0)
        {
            throw new IllegalArgumentException("Byte array length minus offset must be even to convert to unsigned short array");
        }

        int count = (data.length - offset) / 2;
        int[] result = new int[count];

        for (int i = 0; i < count; i++)
        {
            result[i] = toUnsignedShort(data, offset + (i * 2), order);
        }

        return result;
    }

    /**
     * Convenience method for converting an entire byte array to an integer array using the
     * specified byte order.
     * 
     * @param data
     *        the input byte array
     * @param order
     *        the byte order to interpret the float values
     * @return an array of integer values
     */
    public static int[] toIntegerArray(byte[] data, ByteOrder order)
    {
        return toIntegerArray(data, 0, order);
    }

    /**
     * Converts a byte array to an array of signed 32-bit integers, honouring the specified byte
     * order.
     *
     * @param data
     *        the input byte array
     * @param offset
     *        the starting offset in the byte array
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return an int array containing the signed integer values
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     * @throws IndexOutOfBoundsException
     *         if the offset is out of bounds
     * @throws IllegalArgumentException
     *         if the remaining length of the input is not a multiple of 4
     */
    public static int[] toIntegerArray(byte[] data, int offset, ByteOrder order)
    {
        final int dataSize = 4;

        if (data == null)
        {
            throw new NullPointerException("Input byte array cannot be null");
        }

        if (offset < 0 || offset > data.length)
        {
            throw new IndexOutOfBoundsException("Offset [" + offset + "] is out of bounds for array of length [" + data.length + "]");
        }

        final int remainingLength = data.length - offset;

        if (remainingLength % dataSize != 0)
        {
            throw new IllegalArgumentException("Byte array length minus offset (" + remainingLength + ") must be a multiple of [" + dataSize + "] to convert to integer array");
        }

        int count = remainingLength / dataSize;
        int[] result = new int[count];

        for (int i = 0; i < count; i++)
        {
            result[i] = toInteger(data, offset + (i * dataSize), order);
        }

        return result;
    }

    /**
     * Convenience method for converting an entire byte array to a signed 64-bit long integer array,
     * using the specified byte order.
     *
     * @param data
     *        the input byte array
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return a long array containing the signed long values
     * 
     * @throws IllegalArgumentException
     *         if the input is null, or the length is not a multiple of 8
     */
    public static long[] toLongArray(byte[] data, ByteOrder order)
    {
        return toLongArray(data, 0, order);
    }

    /**
     * Converts a byte array to an array of signed 64-bit long integers, honouring the specified
     * byte order.
     *
     * @param data
     *        the input byte array
     * @param offset
     *        the starting offset in the byte array
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @return a long array containing the signed long values
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     * @throws IndexOutOfBoundsException
     *         if the offset is out of bounds
     * @throws IllegalArgumentException
     *         if the remaining length of the input is not a multiple of 8
     */
    public static long[] toLongArray(byte[] data, int offset, ByteOrder order)
    {
        final int dataSize = 8;

        if (data == null)
        {
            throw new NullPointerException("Input byte array cannot be null");
        }

        if (offset < 0 || offset > data.length)
        {
            throw new IndexOutOfBoundsException("Offset [" + offset + "] is out of bounds for array of length " + data.length);
        }

        final int remainingLength = data.length - offset;

        if (remainingLength % dataSize != 0)
        {
            throw new IllegalArgumentException("Byte array length minus offset [" + remainingLength + "] must be a multiple of [" + dataSize + "] to convert to long array");
        }

        int count = remainingLength / dataSize;
        long[] result = new long[count];

        for (int i = 0; i < count; i++)
        {
            result[i] = toLong(data, offset + (i * dataSize), order);
        }

        return result;
    }

    /**
     * Convenience method for converting an entire byte array to a float array using the specified
     * byte order.
     * 
     * @param data
     *        the input byte array
     * @param order
     *        the byte order to interpret the float values
     * @return an array of float values
     */
    public static float[] toFloatArray(byte[] data, ByteOrder order)
    {
        return toFloatArray(data, 0, order);
    }

    /**
     * Converts a byte array to an array of 32-bit float values, honouring the specified byte order.
     *
     * @param data
     *        the input byte array
     * @param offset
     *        the starting offset in the byte array
     * @param order
     *        the byte order to interpret the float values
     * @return an array of float values
     *
     * @throws NullPointerException
     *         if the input byte array is null
     * @throws IndexOutOfBoundsException
     *         if the offset is out of bounds, or the remaining length is not a multiple of 4
     */
    public static float[] toFloatArray(byte[] data, int offset, ByteOrder order)
    {
        final int dataSize = 4;

        if (data == null)
        {
            throw new NullPointerException("Input byte array cannot be null");
        }

        if (offset < 0 || offset > data.length)
        {
            throw new IndexOutOfBoundsException("Offset [" + offset + "] is out of bounds for array of length [" + data.length + "]");
        }

        int remaining = data.length - offset;

        if (remaining % dataSize != 0)
        {
            throw new IndexOutOfBoundsException("Byte array length minus offset [" + remaining + "] must be a multiple of [" + dataSize + "] to convert to float array");
        }

        int count = remaining / dataSize;
        float[] result = new float[count];

        for (int i = 0; i < count; i++)
        {
            result[i] = toFloat(data, offset + (i * dataSize), order);
        }

        return result;
    }

    /**
     * Convenience method for converting an entire byte array to a double array using the specified
     * byte order.
     * 
     * @param data
     *        the input byte array
     * @param order
     *        the byte order to interpret the double values
     * @return an array of double values
     */
    public static double[] toDoubleArray(byte[] data, ByteOrder order)
    {
        return toDoubleArray(data, 0, order);
    }

    /**
     * Converts a byte array to an array of 64-bit double values, honouring the specified byte
     * order.
     *
     * @param data
     *        the input byte array
     * @param offset
     *        the starting offset in the byte array
     * @param order
     *        the byte order to interpret the double values
     * @return an array of double values
     *
     * @throws NullPointerException
     *         if the input byte array is null
     * @throws IndexOutOfBoundsException
     *         if the offset is out of bounds, or the remaining length is not a multiple of 8
     */
    public static double[] toDoubleArray(byte[] data, int offset, ByteOrder order)
    {
        final int dataSize = 8;

        if (data == null)
        {
            throw new NullPointerException("Input byte array cannot be null");
        }

        if (offset < 0 || offset > data.length)
        {
            throw new IndexOutOfBoundsException("Offset [" + offset + "] is out of bounds for array of length [" + data.length + "]");
        }

        int remaining = data.length - offset;

        if (remaining % dataSize != 0)
        {
            throw new IndexOutOfBoundsException("Byte array length minus offset [" + remaining + "] must be a multiple of [" + dataSize + "] to convert to double array");
        }

        int count = remaining / dataSize;
        double[] result = new double[count];

        for (int i = 0; i < count; i++)
        {
            result[i] = toDouble(data, offset + (i * dataSize), order);
        }

        return result;
    }

    /**
     * Converts a byte array into an array of {@link RationalNumber} objects, interpreting each
     * 8-byte segment as a numerator-denominator pair.
     *
     * @param data
     *        the input byte array
     * @param offset
     *        the offset in the array to start reading from
     * @param order
     *        the byte order for interpreting the specified bytes, using either
     *        {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * @param type
     *        the rational number type (SIGNED or UNSIGNED)
     * @return an array of RationalNumber objects
     * 
     * @throws NullPointerException
     *         if the input byte array is null
     * @throws IllegalArgumentException
     *         if data is null or the length is invalid
     * @throws IndexOutOfBoundsException
     *         if the offset is out of bounds
     */
    public static RationalNumber[] toRationalArray(byte[] data, int offset, ByteOrder order, RationalNumber.DataType type)
    {
        if (data == null)
        {
            throw new NullPointerException("Input byte array cannot be null");
        }

        if ((data.length - offset) % 8 != 0)
        {
            throw new IndexOutOfBoundsException("Byte array length minus offset must be divisible by 8");
        }

        int count = (data.length - offset) / 8;
        RationalNumber[] result = new RationalNumber[count];

        for (int i = 0; i < count; i++)
        {
            result[i] = toRational(data, offset + (i * 8), order, type);
        }

        return result;
    }
}