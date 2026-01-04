package common;

import java.nio.ByteOrder;
import java.util.Objects;

/**
 * This abstract class provides the functionality to perform reader operations intended for
 * obtaining data from a byte array. Data can either be read sequentially or at random, depending on
 * the implementing sub-classes.
 * 
 * @author Trevor Maggs
 * @version 1.2
 * @since 13 August 2025
 */
public abstract class AbstractByteReader
{
    private ByteOrder byteOrder;
    private final byte[] buffer;
    private final int baseIndex;

    /**
     * Constructs an instance to store the specified byte array containing payload data and the byte
     * order to interpret the input bytes correctly. The offset specifies the starting position
     * within the array to read from.
     *
     * @param buf
     *        an array of bytes acting as the buffer
     * @param startIndex
     *        specifies the starting position within the specified array
     * @param order
     *        the byte order, either {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     */
    public AbstractByteReader(byte[] buf, int startIndex, ByteOrder order)
    {
        if (startIndex < 0)
        {
            throw new IndexOutOfBoundsException("Base offset cannot be less than zero. Detected offset [" + startIndex + "]");
        }

        if (startIndex > buf.length)
        {
            throw new IndexOutOfBoundsException("Base offset cannot exceed buffer length. Detected offset [" + startIndex + "], buffer length [" + buf.length + "]");
        }

        this.buffer = Objects.requireNonNull(buf, "Input buffer cannot be null");
        this.baseIndex = startIndex;
        this.byteOrder = Objects.requireNonNull(order, "Byte order cannot be null");
    }

    /**
     * Checks whether the specified position is within the byte array’s bounds. If the position is
     * out of range, an {@code IndexOutOfBoundsException} is thrown.
     *
     * @param position
     *        the relative index from baseIndex (0 means first readable byte)
     * @param length
     *        the total number of bytes to check (must be {@literal <=} Integer.MAX_VALUE)
     * 
     * @throws IndexOutOfBoundsException
     *         if the position is out of bounds
     */
    private void validateByteIndex(long position, int length)
    {
        if (position < 0L)
        {
            throw new IndexOutOfBoundsException("Cannot read the buffer with a negative index [" + position + "]");
        }

        if (length < 0)
        {
            throw new IndexOutOfBoundsException("Length of requested bytes cannot be negative [" + length + "]");
        }

        if (position + length > length())
        {
            throw new IndexOutOfBoundsException("Attempt to read beyond end of buffer. Relative index [" + position + "], Requested length [" + length + "], Readable length [" + length() + "]");
        }

        if (position > Integer.MAX_VALUE)
        {
            throw new IndexOutOfBoundsException("File position offset exceeds Java's maximum array index limit");
        }
    }

    /**
     * Returns a single byte from the array at the specified relative position.
     *
     * @param position
     *        the index (relative to baseIndex) in the byte array
     * @return the byte at the specified position
     */
    protected byte getByte(long position)
    {
        validateByteIndex(position, 1);

        return buffer[baseIndex + (int) position];
    }

    /**
     * Copies and returns a sub-array from the byte array, starting from the specified position.
     * 
     * @param position
     *        the index (relative to baseIndex) in the byte array
     * @param length
     *        the total number of bytes to include in the sub-array (must be {@literal <=} Integer.MAX_VALUE)
     * @return a new byte array containing the specified subset of the original array
     */
    protected byte[] getBytes(long position, int length)
    {
        byte[] bytes = new byte[length];

        validateByteIndex(position, length);

        System.arraycopy(buffer, baseIndex + (int) position, bytes, 0, length);

        return bytes;
    }

    /**
     * Retrieves the offset pointer to the byte array, where read operations can start from.
     *
     * @return the base offset
     */
    public int getBaseIndex()
    {
        return baseIndex;
    }

    /**
     * Sets the byte order for interpreting the input bytes correctly.
     *
     * @param order
     *        the byte order for interpreting the input bytes
     */
    public void setByteOrder(ByteOrder order)
    {
        byteOrder = Objects.requireNonNull(order, "Byte order cannot be null");
    }

    /**
     * Returns the byte order, indicating how data values will be interpreted correctly.
     *
     * @return either {@link java.nio.ByteOrder#BIG_ENDIAN} or {@link java.nio.ByteOrder#LITTLE_ENDIAN} 
     */
    public ByteOrder getByteOrder()
    {
        return byteOrder;
    }

    /**
     * Returns the length of the readable portion of the byte array (buffer length minus
     * baseIndex).
     *
     * @return the readable array length
     */
    public long length()
    {
        return buffer.length - baseIndex;
    }

    /**
     * Retrieves the data at the specified offset within the byte array without advancing the
     * position.
     * 
     * @param offset
     *        the offset (relative to baseIndex)
     * @return the byte of data
     */
    public byte peek(long offset)
    {
        return getByte(offset);
    }

    /**
     * Retrieves a sub-array of bytes at the specified offset without advancing the position.
     *
     * @param offset
     *        the offset (relative to baseIndex)
     * @param length
     *        the total number of bytes to include in the sub-array
     * @return the sub-array of bytes
     */
    public byte[] peek(long offset, int length)
    {
        return getBytes(offset, length);
    }

    /**
     * Returns a formatted string representation of the raw buffer contents, primarily intended for
     * debugging.
     * 
     * @return string containing a hex dump of the buffer
     */
    public String dumpRawBytes()
    {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length(); i++)
        {
            int absIndex = baseIndex + i;

            if (i % 16 == 0)
            {
                // Print the RELATIVE offset for clarity of the segment
                sb.append(String.format("%n%04X: ", i));
            }
            else if (i % 16 == 8)
            {
                sb.append("- ");
            }

            sb.append(String.format("%02X ", buffer[absIndex]));
        }

        sb.append(System.lineSeparator());
        sb.append(String.format("readable length: %d%s", length(), System.lineSeparator()));

        return sb.toString();
    }
}