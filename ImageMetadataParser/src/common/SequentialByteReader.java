package common;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Performs sequential reading of primitive data types from a byte array.
 *
 * <p>
 * Supports reading of signed and unsigned integers, floating-point numbers, and byte sequences with
 * configurable byte order (big-endian or little-endian).
 * </p>
 * 
 * @author Trevor Maggs
 * @version 1.1
 * @since 13 September 2025
 */
public class SequentialByteReader extends AbstractByteReader
{
    private int bufferIndex;
    private final Deque<Integer> markPositionStack;

    /**
     * Constructs a reader for the given byte array with big-endian byte order.
     *
     * @param buf
     *        the byte array to read from
     */
    public SequentialByteReader(byte[] buf)
    {
        this(buf, ByteOrder.BIG_ENDIAN);
    }

    /**
     * Constructs a reader for the given byte array with a specified byte order.
     *
     * @param buf
     *        the byte array to read from
     * @param order
     *        the byte order to use
     */
    public SequentialByteReader(byte[] buf, ByteOrder order)
    {
        this(buf, 0, order);
    }

    /**
     * Constructs a reader for the given byte array, starting from a specified offset.
     *
     * @param buf
     *        the byte array to read from
     * @param offset
     *        the starting position
     */
    public SequentialByteReader(byte[] buf, int offset)
    {
        this(buf, offset, ByteOrder.BIG_ENDIAN);
    }

    /**
     * Constructs a reader for the given byte array, starting from the specified offset in specified
     * byte order.
     *
     * @param buf
     *        the byte array to read from
     * @param offset
     *        the starting position
     * @param order
     *        the byte order to use
     */
    public SequentialByteReader(byte[] buf, int offset, ByteOrder order)
    {
        super(buf, offset, order);

        this.bufferIndex = 0;
        this.markPositionStack = new ArrayDeque<>();
    }

    /**
     * Returns the current read position in the byte array.
     *
     * @return the current read position
     */
    public int getCurrentPosition()
    {
        return bufferIndex;
    }

    /**
     * Returns the number of unread bytes remaining in the buffer, relative to the current
     * read position.
     *
     * @return the number of bytes still available for reading
     */
    public int remaining()
    {
        return length() - bufferIndex;
    }

    /**
     * Checks whether at least the specified number of bytes are available to read without exceeding
     * the buffer's bounds.
     *
     * @param n
     *        the number of bytes to check for
     *
     * @return true if at least n bytes remain, otherwise false
     */
    public boolean hasRemaining(int n)
    {
        return remaining() >= n;
    }

    /**
     * Reads a single byte from the current position and advances the reader.
     *
     * @return the byte value
     */
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
    public byte[] readBytes(int length)
    {
        if (!hasRemaining(length))
        {
            throw new IndexOutOfBoundsException("Cannot read [" + length + "] bytes. Only [" + remaining() + "] remaining.");
        }

        byte[] bytes = getBytes(bufferIndex, length);

        bufferIndex += length;

        return bytes;
    }

    /**
     * Reads an unsigned 8-bit integer from the current position and advances the reader.
     *
     * @return the unsigned byte value (0-255)
     */
    public short readUnsignedByte()
    {
        return (short) (readByte() & 0xFF);
    }

    /**
     * Reads a signed 16-bit integer from the current position and advances the reader.
     *
     * @return the short value
     */
    public short readShort()
    {
        return (short) readValue(2);
    }

    /**
     * Reads an unsigned 16-bit integer from the current position and advances the reader.
     *
     * @return the unsigned short value (0-65535)
     */
    public int readUnsignedShort()
    {
        return readShort() & 0xFFFF;
    }

    /**
     * Reads a signed 32-bit integer from the current position and advances the reader.
     *
     * @return The integer value.
     */
    public int readInteger()
    {
        return (int) readValue(4);
    }

    /**
     * Reads an unsigned 32-bit integer from the current position and advances the reader.
     *
     * @return the unsigned integer value as a long
     */
    public long readUnsignedInteger()
    {
        return readInteger() & 0xFFFFFFFFL;
    }

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
    public double readDouble()
    {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Reads a null-terminated Latin-1 (ISO-8859-1) encoded string from the current position.
     *
     * <p>
     * The null terminator is consumed but not included in the returned string.
     * </p>
     *
     * @return the decoded string
     * 
     * @throws IllegalStateException
     *         if a null terminator is not found before the end of the buffer
     */
    public String readString()
    {
        int start = bufferIndex;
        int end = start;

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

        // Read bytes and advance the index past the terminator
        byte[] stringBytes = getBytes(start, end - start);

        bufferIndex = end + 1;

        return new String(stringBytes, StandardCharsets.ISO_8859_1);
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
     *         if skipping would exceed the buffer bounds
     */
    public int skip(int n)
    {
        int newPosition = bufferIndex + n;

        if (newPosition < 0 || newPosition > length())
        {
            throw new IndexOutOfBoundsException("Cannot skip by [" + n + "] bytes. New position [" + newPosition + "] is out of bounds [0, " + length() + "].");
        }

        bufferIndex = newPosition;

        return bufferIndex;
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
    public void seek(int pos)
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
    public void reset()
    {
        if (markPositionStack.isEmpty())
        {
            throw new IllegalStateException("Cannot reset position: mark stack is empty");
        }

        bufferIndex = markPositionStack.pop();
    }

    /**
     * Reads a signed integer of the specified byte length.
     *
     * @param numBytes
     *        number of bytes to read
     * 
     * @return the integer value
     */
    private long readValue(int numBytes)
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
}