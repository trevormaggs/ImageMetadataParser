package common;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Performs sequential reading of primitive data types from a byte array.
 *
 * <p>
 * Supports reading of signed and unsigned integers, floating-point numbers, and byte sequences with
 * configurable byte order (big-endian or little-endian).
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.2
 * @since 12 December 2025
 */
public class SequentialByteArrayReader implements ByteStreamReader
{
    private final byte[] buffer;
    private final int baseIndex;
    private final Deque<Long> markPositionStack;
    private long bufferIndex;
    private ByteOrder byteOrder;

    /**
     * Constructs a reader for the given byte array with big-endian byte order.
     *
     * @param buf
     *        the source byte array
     */
    public SequentialByteArrayReader(byte[] buf)
    {
        this(buf, ByteOrder.BIG_ENDIAN);
    }

    /**
     * Constructs a reader for the given byte array with a specified byte order.
     *
     * @param buf
     *        the source byte array
     * @param order
     *        the byte order to use
     */
    public SequentialByteArrayReader(byte[] buf, ByteOrder order)
    {
        this(buf, 0, order);
    }

    /**
     * Constructs a reader for the given byte array, starting from a specified offset.
     *
     * @param buf
     *        the source byte array
     * @param offset
     *        the starting index/position from which to begin reading
     */
    public SequentialByteArrayReader(byte[] buf, int offset)
    {
        this(buf, offset, ByteOrder.BIG_ENDIAN);
    }

    /**
     * Constructs a reader for the given byte array, starting from the specified offset in specified
     * byte order.
     *
     * @param buf
     *        the source byte array
     * @param startIndex
     *        the starting index/position from which to begin reading
     * @param order
     *        the byte order to use
     */
    public SequentialByteArrayReader(byte[] buf, int startIndex, ByteOrder order)
    {
        this.buffer = Objects.requireNonNull(buf, "Input buffer cannot be null");
        this.baseIndex = startIndex;
        this.byteOrder = order;
        this.bufferIndex = 0;
        this.markPositionStack = new ArrayDeque<>();
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
     * Returns the number of unread bytes remaining in the buffer, relative to the current
     * read position.
     *
     * @return the number of bytes still available for reading
     */
    public long remaining()
    {
        return length() - bufferIndex;
    }

    /**
     * Checks if at least the specified number of bytes are available to read.
     *
     * @param n
     *        the number of bytes to check for
     *
     * @return true if {@code n} bytes or more remain, otherwise false
     */
    public boolean hasRemaining(int n)
    {
        return remaining() >= n;
    }

    /**
     * Sets the byte order for interpreting the input bytes correctly.
     *
     * @param order
     *        the byte order for interpreting the input bytes
     */
    @Override
    public void setByteOrder(ByteOrder order)
    {
        byteOrder = Objects.requireNonNull(order, "Byte order cannot be null");
    }

    /**
     * Returns the byte order, indicating how data values will be interpreted correctly.
     *
     * @return either {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     */
    @Override
    public ByteOrder getByteOrder()
    {
        return byteOrder;
    }

    /**
     * Returns the current read position.
     * 
     * @return the current position relative to the starting offset (baseIndex)
     */
    @Override
    public long getCurrentPosition()
    {
        return bufferIndex;
    }

    /**
     * Skips forward by the specified number of bytes.
     *
     * @param n
     *        the number of bytes to skip
     *
     * @return the new position after skipping
     *
     * @throws IndexOutOfBoundsException
     *         if the position is outside the valid range [0..length()]
     */
    @Override
    public void skip(long n)
    {
        long newPosition = bufferIndex + n;

        if (newPosition < 0 || newPosition > length())
        {
            throw new IndexOutOfBoundsException("Cannot skip by [" + n + "] bytes. New position [" + newPosition + "] is out of bounds [0, " + length() + "].");
        }

        bufferIndex = newPosition;
    }

    /**
     * Moves to the specified position within the byte array.
     *
     * @param pos
     *        the position to move to
     *
     * @throws IndexOutOfBoundsException
     *         if the position is invalid
     */
    @Override
    public void seek(long pos)
    {
        if (pos < 0 || pos > length())
        {
            throw new IndexOutOfBoundsException("Position [" + pos + "] out of bounds. Valid range is [0.." + length() + "].");
        }

        bufferIndex = pos;
    }

    /**
     * Marks the current position in the buffer, allowing a subsequent {@link #reset()} call to
     * return to this position.
     */
    @Override
    public void mark()
    {
        markPositionStack.push(bufferIndex);
    }

    /**
     * Resets the reader's position to the last marked position.
     *
     * @throws IllegalStateException
     *         if the mark stack is empty
     */
    @Override
    public void reset()
    {
        if (markPositionStack.isEmpty())
        {
            throw new IllegalStateException("Cannot reset position: mark stack is empty");
        }

        bufferIndex = markPositionStack.pop();
    }

    /**
     * Reads a single byte from the current position and advances the reader.
     *
     * @return the byte value
     */
    @Override
    public byte readByte()
    {
        if (!hasRemaining(1))
        {
            throw new IndexOutOfBoundsException("End of buffer reached. Cannot read beyond position [" + length() + "]");
        }

        return getByte(bufferIndex++);
    }

    /**
     * Reads a sequence of bytes from the current position and advances the reader.
     *
     * @param length
     *        the number of bytes to read
     * @return a new byte array containing the read bytes
     */
    @Override
    public byte[] readBytes(int length)
    {
        if (!hasRemaining(length))
        {
            throw new IndexOutOfBoundsException("Cannot read [" + length + "] bytes. Only [" + remaining() + "] remaining");
        }

        byte[] bytes = getBytes(bufferIndex, length);

        bufferIndex += length;

        return bytes;
    }

    /**
     * Reads an unsigned 8-bit integer from the current position and advances the reader.
     *
     * @return the unsigned 8-bit value (0-255)
     */
    @Override
    public int readUnsignedByte()
    {
        return (readByte() & 0xFF);
    }

    /**
     * Reads a signed 16-bit integer from the current position and advances the reader.
     *
     * @return the short value
     */
    @Override
    public short readShort()
    {
        return (short) readValue(2);
    }

    /**
     * Reads an unsigned 16-bit integer from the current position and advances the reader.
     *
     * @return the unsigned short value (0-65535)
     */
    @Override
    public int readUnsignedShort()
    {
        return readShort() & 0xFFFF;
    }

    /**
     * Reads a signed 32-bit integer from the current position and advances the reader.
     *
     * @return the signed 32-bit integer value
     */
    @Override
    public int readInteger()
    {
        return (int) readValue(4);
    }

    /**
     * Reads an unsigned 32-bit integer from the current position and advances the reader.
     *
     * @return the unsigned integer value as a long
     */
    @Override
    public long readUnsignedInteger()
    {
        return readInteger() & 0xFFFFFFFFL;
    }

    @Override
    /**
     * Reads a signed 64-bit long from the current position and advances the reader.
     *
     * @return the long value
     */
    public long readLong()
    {
        return readValue(8);
    }

    /**
     * Reads a 32-bit IEEE 754 floating-point value from the current position and advances the
     * reader.
     *
     * @return the float value
     */
    @Override
    public float readFloat()
    {
        return Float.intBitsToFloat(readInteger());
    }

    /**
     * Reads a 64-bit IEEE 754 floating-point value from the current position and advances the
     * reader.
     *
     * @return the double value
     */
    @Override
    public double readDouble()
    {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Reads a null-terminated string (ISO-8859-1).
     * 
     * <p>
     * Scans for {@code 0x00} starting from the current position. The pointer is advanced past the
     * null terminator.
     * </p>
     * 
     * @return the decoded string excluding the null terminator
     * 
     * @throws IllegalStateException
     *         if the end of the buffer is reached before a null terminator
     */
    @Override
    public String readString()
    {
        int start = (int) bufferIndex;
        int limit = (int) length();
        int end = start;

        while (end < limit)
        {
            if (buffer[baseIndex + end] == 0x00)
            {
                break;
            }

            end++;
        }

        if (end == limit)
        {
            throw new IllegalStateException("Null terminator not found starting at position [" + start + "]");
        }

        int strLength = end - start;
        byte[] stringBytes = new byte[strLength];

        System.arraycopy(buffer, baseIndex + start, stringBytes, 0, strLength);

        bufferIndex = end + 1;

        return new String(stringBytes, StandardCharsets.ISO_8859_1);
    }

    /**
     * Optimized readValue to minimize validation overhead.
     */
    private long readValue(int numBytes)
    {
        if (!hasRemaining(numBytes))
        {
            throw new IndexOutOfBoundsException("Insufficient bytes remaining.");
        }

        long value = 0;
        int start = baseIndex + (int) bufferIndex;

        if (byteOrder == ByteOrder.BIG_ENDIAN)
        {
            for (int i = 0; i < numBytes; i++)
            {
                value = (value << 8) | (buffer[start + i] & 0xFF);
            }
        }

        else
        {
            for (int i = 0; i < numBytes; i++)
            {
                value |= ((long) (buffer[start + i] & 0xFF)) << (i * 8);
            }
        }

        bufferIndex += numBytes;

        return value;
    }

    /**
     * Checks whether the specified position is within the byte array’s bounds. If the position is
     * out of range, an {@code IndexOutOfBoundsException} is thrown.
     *
     * @param position
     *        the relative index from baseIndex (0 means first readable byte)
     * @param length
     *        the total number of bytes to check (must be <= Integer.MAX_VALUE)
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
    private byte getByte(long position)
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
     *        the total number of bytes to include in the sub-array (must be <= Integer.MAX_VALUE)
     * @return a new byte array containing the specified subset of the original array
     */
    private byte[] getBytes(long position, int length)
    {
        byte[] bytes = new byte[length];

        validateByteIndex(position, length);

        System.arraycopy(buffer, baseIndex + (int) position, bytes, 0, length);

        return bytes;
    }

    @Deprecated
    public long readValueOld(int numBytes)
    {
        if (hasRemaining(numBytes))
        {
            long value = 0;

            if (getByteOrder() == ByteOrder.BIG_ENDIAN)
            {
                for (int i = 0; i < numBytes; i++)
                {
                    value = (value << 8) | readUnsignedByte();
                }
            }

            else
            {
                for (int i = 0; i < numBytes; i++)
                {
                    value |= ((long) readUnsignedByte()) << (i * 8);
                }
            }

            return value;
        }

        else
        {
            throw new IndexOutOfBoundsException("Cannot read [" + numBytes + "] bytes. Only [" + remaining() + "] remaining.");
        }
    }

    @Deprecated
    public String readStringOld()
    {
        long start = bufferIndex;
        long end = start;

        while (end < length())
        {
            if (getByte(end) == 0x00)
            {
                break;
            }

            end++;
        }

        if (end == length())
        {
            throw new IllegalStateException("Null terminator not found for string starting at position [" + start + "]");
        }

        long length = end - start;

        if (length > Integer.MAX_VALUE)
        {
            throw new UnsupportedOperationException("String length exceeds Java's maximum array size");
        }

        byte[] stringBytes = getBytes(start, (int) length);

        bufferIndex = end + 1;

        return new String(stringBytes, StandardCharsets.ISO_8859_1);
    }

    /**
     * Implementation of close for the byte array reader. Since this reader operates on an in-memory
     * buffer, no system resources need to be released. This method is provided for compatibility
     * with the AutoCloseable interface.
     */
    @Override
    public void close()
    {
        // Nothing to do
    }
}