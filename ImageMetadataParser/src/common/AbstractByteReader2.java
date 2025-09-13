package common;

import java.nio.ByteOrder;
import java.util.Objects;

/**
 * This abstract class provides the functionality to perform reader operations intended for
 * obtaining data from a byte array. Data can either be read sequentially or at random, depending on
 * the implementing sub-classes.
 * 
 * @author Trevor Maggs
 * @version 1.1
 * @since 13 August 2025
 */
public abstract class AbstractByteReader2
{
    private ByteOrder byteOrder;
    private final byte[] buffer;
    private final int baseOffset;

    /**
     * Constructs an instance to store the specified byte array containing payload data and the byte
     * order to interpret the input bytes correctly. The offset specifies the starting position
     * within the array to read from.
     *
     * @param buf
     *        an array of bytes acting as the buffer
     * @param offset
     *        specifies the starting position within the specified array
     * @param order
     *        the byte order, either {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     */
    public AbstractByteReader2(byte[] buf, int offset, ByteOrder order)
    {
        if (offset < 0)
        {
            throw new IllegalArgumentException("Base offset cannot be less than zero. Detected offset: [" + offset + "]");
        }

        if (offset > buf.length)
        {
            throw new IllegalArgumentException("Base offset cannot exceed buffer length. Detected offset: [" + offset + "], buffer length: [" + buf.length + "]");
        }

        this.buffer = Objects.requireNonNull(buf, "Input buffer cannot be null");
        this.baseOffset = offset;
        this.byteOrder = Objects.requireNonNull(order, "Byte order cannot be null");
    }

    /**
     * Checks whether the specified position is within the byte array’s bounds. If the position is
     * out of range, an {@code IndexOutOfBoundsException} is thrown.
     *
     * @param position
     *        the relative position in the byte array (0 = start at baseOffset)
     * @param length
     *        the total length within the byte array to check
     * 
     * @throws IndexOutOfBoundsException
     *         if the position is out of bounds
     */
    private void validateByteIndex(int position, int length)
    {
        if (position < 0)
        {
            throw new IndexOutOfBoundsException("Cannot read the buffer with a negative index [" + position + "]");
        }

        if (length < 0)
        {
            throw new IndexOutOfBoundsException("Length of requested bytes cannot be negative [" + length + "]");
        }

        if (position + length > length())
        {
            throw new IndexOutOfBoundsException("Attempt to read beyond end of buffer [baseOffset: " + baseOffset + ", index: " + position + ", requestedLength: " + length + ", bufferLength: " + buffer.length + "]");
        }
    }

    /**
     * Returns a single byte from the array at the specified relative position.
     *
     * @param position
     *        the index (relative to baseOffset) in the byte array
     *
     * @return the byte at the specified position
     */
    protected byte getByte(int position)
    {
        validateByteIndex(position, 1);

        return buffer[baseOffset + position];
    }

    /**
     * Copies and returns a sub-array from the byte array, starting from the specified position.
     * 
     * @param position
     *        the index (relative to baseOffset) in the byte array
     * @param length
     *        the total number of bytes to include in the sub-array
     * 
     * @return a new byte array containing the specified subset of the original array
     */
    protected byte[] getBytes(int position, int length)
    {
        validateByteIndex(position, length);

        byte[] bytes = new byte[length];

        System.arraycopy(buffer, baseOffset + position, bytes, 0, length);

        return bytes;
    }

    /**
     * Retrieves the offset pointer to the byte array, where read operations can start from.
     *
     * @return the base offset
     */
    public int getBaseOffset()
    {
        return baseOffset;
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
     * @return either {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     */
    public ByteOrder getByteOrder()
    {
        return byteOrder;
    }

    /**
     * Returns the length of the readable portion of the byte array (buffer length minus
     * baseOffset).
     *
     * @return the array length
     */
    public int length()
    {
        return buffer.length - baseOffset;
    }

    /**
     * Retrieves the data at the specified offset within the byte array without advancing the
     * position.
     *
     * @param offset
     *        the offset (relative to baseOffset)
     * 
     * @return the byte of data
     */
    public byte peek(int offset)
    {
        return getByte(offset);
    }

    /**
     * Retrieves a sub-array of bytes at the specified offset without advancing the position.
     *
     * @param offset
     *        the offset (relative to baseOffset)
     * @param length
     *        the total number of bytes to include in the sub-array
     * 
     * @return the sub-array of bytes
     */
    public byte[] peek(int offset, int length)
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

        for (int i = 0; i < buffer.length; i++)
        {
            if (i % 16 == 0)
            {
                sb.append(String.format("%n%04X: ", i));
            }
            else if (i % 16 == 8)
            {
                sb.append("- ");
            }

            sb.append(String.format("%02X ", buffer[i]));
        }

        sb.append(System.lineSeparator());
        sb.append(String.format("buffer length: %d%s", buffer.length, System.lineSeparator()));

        return sb.toString();
    }
}