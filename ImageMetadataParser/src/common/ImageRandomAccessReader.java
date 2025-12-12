package common;

import java.io.RandomAccessFile;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Utility for reading binary data from image files with configurable byte order.
 *
 * <p>
 * This class wraps a {@link RandomAccessFile} for efficient, reliable seeking (seek, skip,
 * mark/reset stack) and provides methods to read signed and unsigned values of various primitive
 * types. It supports both big-endian and little-endian formats.
 * </p>
 * 
 * <p>
 * Unlike standard input streams, this class maintains a <b>stack</b> of marked positions, allowing
 * for nested "diving" into sub-structures and returning to previous offsets in LIFO order.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.1
 * @since 12 December 2025
 */
public class ImageRandomAccessReader implements ByteStreamReader
{
    private final RandomAccessFile raf;
    private ByteOrder byteOrder;
    private final java.util.Deque<Long> positionStack = new java.util.ArrayDeque<>();

    /**
     * Constructs a reader for the specified file path, respecting the byte order. This is the
     * primary constructor using RandomAccessFile. Note, this is configured as a read-only
     * operation.
     *
     * @param fpath
     *        the path to the image file to be read
     * @param order
     *        the byte order used for interpreting multi-byte values
     * 
     * @throws IOException
     *         if an I/O error occurs while opening the file
     */
    public ImageRandomAccessReader(Path fpath, ByteOrder order) throws IOException
    {
        this.raf = new RandomAccessFile(fpath.toFile(), "r");
        this.byteOrder = Objects.requireNonNull(order, "Byte order cannot be null");
    }

    /**
     * Constructs a reader for the specified input file with big-endian byte order as default.
     *
     * @param fpath
     *        the path to the image file to be read
     * 
     * @throws IOException
     *         if an I/O error occurs when opening the file
     */
    public ImageRandomAccessReader(Path fpath) throws IOException
    {
        this(fpath, ByteOrder.BIG_ENDIAN);
    }

    /**
     * Sets the byte order for interpreting the random file access correctly.
     *
     * @param order
     *        the byte order for interpreting the input bytes
     */
    public void setByteOrder(ByteOrder order)
    {
        if (order == null)
        {
            throw new NullPointerException("Byte order cannot be null");
        }

        byteOrder = order;
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
     * Returns the current byte position in the stream.
     *
     * @return the current position
     */
    @Override
    public long getCurrentPosition() throws IOException
    {
        return raf.getFilePointer();
    }

    /**
     * Records the current position in the file by pushing it onto an internal stack. A subsequent
     * call to {@link #reset()} will pop this position and return the reader to it.
     * 
     * @throws IOException
     *         if an I/O error occurs while retrieving the file pointer
     */
    public void mark() throws IOException
    {
        positionStack.push(getCurrentPosition());
    }

    /**
     * Repositions the stream to the last position recorded by the {@link #mark()} method. This
     * operation removes the position from the internal stack.
     *
     * @throws IOException
     *         if an I/O error occurs during seeking
     * @throws IllegalStateException
     *         if the mark stack is empty (no corresponding mark exists)
     */
    public void reset() throws IOException
    {
        if (positionStack.isEmpty())
        {
            throw new IllegalStateException("Cannot reset position: mark stack is empty");
        }

        long lastPosition = positionStack.pop();

        raf.seek(lastPosition);
    }

    /**
     * Seeks to a specific position in the stream.
     *
     * @param n
     *        the position to seek to
     * 
     * @throws IOException
     *         if an I/O error occurs or the position is out of bounds
     */
    @Override
    public void seek(long n) throws IOException
    {
        if (n < 0)
        {
            throw new IllegalArgumentException("Position cannot be negative");
        }

        raf.seek(n);
    }

    /**
     * Skips {@code n} bytes relative to the current file pointer in the stream.
     *
     * @param n
     *        the number of bytes to skip. Can be negative to skip backward
     * 
     * @throws IOException
     *         if an I/O error occurs or the stream ends prematurely
     */
    @Override
    public void skip(long n) throws IOException
    {
        seek(getCurrentPosition() + n);
    }

    /**
     * Reads a single byte from the current position in the stream and advances the file pointer.
     * Returns it as a signed byte value.
     * 
     * @return the byte value
     * 
     * @throws IOException
     *         if an I/O error occurs or if the file has reached end of file
     */
    @Override
    public byte readByte() throws IOException
    {
        return raf.readByte();
    }

    /**
     * Reads a sequence of bytes from the current position in the stream and advances the file
     * pointer.
     *
     * @param length
     *        the number of bytes to read
     * 
     * @return a new byte array containing the read bytes
     * 
     * @throws IOException
     *         if an I/O error occurs
     * @throws EOFException
     *         if this file reaches the end of file before reading all the bytes
     */
    @Override
    public byte[] readBytes(int length) throws IOException
    {
        byte[] bytes = new byte[length];

        raf.readFully(bytes);

        return bytes;
    }

    /**
     * Reads a single byte and returns it as an unsigned integer (0-255).
     * 
     * @return the unsigned 8-bit value (0-255)
     * 
     * @throws IOException
     *         if an I/O error occurs
     * @throws EOFException
     *         if this file has reached the end of file
     */
    @Override
    public int readUnsignedByte() throws IOException
    {
        int b = raf.readUnsignedByte();

        if (b == -1)
        {
            throw new java.io.EOFException("Premature end of file encountered while reading unsigned byte");
        }

        return b;
    }

    /**
     * Reads two bytes and returns a signed 16-bit short value.
     * 
     * @return the short value
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
    public short readShort() throws IOException
    {
        short value = raf.readShort();

        if (byteOrder == ByteOrder.LITTLE_ENDIAN)
        {
            value = Short.reverseBytes(value);
        }

        return value;
    }

    /**
     * Reads two bytes and returns an unsigned 16-bit short value as an integer.
     * 
     * @return the unsigned short value (0-65535)
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
    public int readUnsignedShort() throws IOException
    {
        return readShort() & 0xFFFF;
    }

    /**
     * Reads four bytes and returns a signed 32-bit integer.
     * 
     * @return the signed 32-bit integer value
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
    public int readInteger() throws IOException
    {
        int value = raf.readInt();

        if (byteOrder == ByteOrder.LITTLE_ENDIAN)
        {
            value = Integer.reverseBytes(value);
        }

        return value;
    }

    /**
     * Reads four bytes and returns an unsigned 32-bit integer as a long.
     * 
     * @return the unsigned integer value as a long
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
    public long readUnsignedInteger() throws IOException
    {
        return readInteger() & 0xFFFFFFFFL;
    }

    /**
     * Reads eight bytes and returns a signed 64-bit long.
     * 
     * @return the long value
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
    public long readLong() throws IOException
    {
        long value = raf.readLong();

        if (byteOrder == ByteOrder.LITTLE_ENDIAN)
        {
            value = Long.reverseBytes(value);
        }

        return value;
    }

    /**
     * Reads four bytes and returns a 32-bit floating-point value.
     * 
     * @return the float value
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
    public float readFloat() throws IOException
    {
        return Float.intBitsToFloat(readInteger());
    }

    /**
     * Reads eight bytes and returns a 64-bit floating-point value.
     * 
     * @return the double value
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
    public double readDouble() throws IOException
    {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Reads a null-terminated string (C-style string) from the current position. The file pointer
     * is advanced past the string data and the null terminator.
     * 
     * <p>
     * The null terminator is consumed but not included in the returned string.
     * </p>
     *
     * @return the string content
     * 
     * @throws IOException
     *         if an I/O error occurs or the end of file is reached before finding the null
     *         terminator
     */

    /**
     * Reads a null-terminated string (C-style string) from the current position.
     * 
     * <p>
     * This method scans the file for a null byte ({@code 0x00}). The file pointer is advanced past
     * both the string data and the null terminator. The returned string is decoded using
     * {@code ISO-8859-1} and does not include the null terminator.
     * </p>
     *
     * @return the string content, not including the null terminator
     * 
     * @throws IOException
     *         if an I/O error occurs or the end of file is reached before finding a null terminator
     * @throws UnsupportedOperationException
     *         if the detected string length exceeds {@link Integer#MAX_VALUE}
     */
    @Override
    public String readString() throws IOException
    {
        long startPosition = getCurrentPosition();

        try
        {
            while (true) // The loop terminates by finding 0x00 or hitting EOF
            {
                if (raf.readByte() == 0x00)
                {
                    long finalPosition = getCurrentPosition();
                    long length = finalPosition - 1 - startPosition;

                    if (length > Integer.MAX_VALUE)
                    {
                        raf.seek(finalPosition);

                        throw new UnsupportedOperationException("String too long");
                    }

                    raf.seek(startPosition);

                    byte[] stringBytes = readBytes((int) length);

                    raf.seek(finalPosition);

                    return new String(stringBytes, StandardCharsets.ISO_8859_1);
                }
            }
        }

        catch (EOFException exc)
        {
            throw new IOException("Null terminator not found for string starting at [" + startPosition + "]");
        }
    }

    /**
     * Reads a single byte at the specified offset from the current position without advancing the
     * stream's position.
     * 
     * @param offset
     *        the offset, relative to current position (file pointer)
     * @return the byte of data
     * 
     * @throws IOException
     *         if an I/O error occurs
     */

    /**
     * Reads a single byte at the specified offset relative to the current position.
     * 
     * <p>
     * The file pointer is restored to its original position before this method returns, even if a
     * read error occurs.
     * </p>
     * 
     * @param offset
     *        the offset relative to the current file pointer
     * @return the byte value at the target position
     * 
     * @throws IOException
     *         if an I/O error occurs or the target position is invalid
     */
    public byte peek(long offset) throws IOException
    {
        long currentPosition = getCurrentPosition();

        try
        {
            raf.seek(currentPosition + offset);

            return raf.readByte();
        }

        finally
        {
            raf.seek(currentPosition);
        }
    }

    /**
     * Reads a sequence of bytes at the specified offset from the current position without advancing
     * the stream's position.
     * 
     * @param offset
     *        the offset, relative to current position (file pointer)
     * @param length
     *        the total number of bytes to include in the sub-array
     * @return the sub-array of bytes
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public byte[] peek(long offset, int length) throws IOException
    {
        byte[] bytes = new byte[length];
        long currentPosition = getCurrentPosition();

        try
        {
            raf.seek(currentPosition + offset);
            raf.readFully(bytes);

            return bytes;
        }

        finally
        {
            raf.seek(currentPosition);
        }
    }

    /**
     * Reads all remaining bytes from the stream into a new array.
     *
     * @return a byte array containing all remaining data
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public byte[] readAllBytes() throws IOException
    {
        long currentPosition = getCurrentPosition();
        long fileSize = raf.length();
        int remainingLength = (int) (fileSize - currentPosition);

        if (remainingLength < 0)
        {
            return new byte[0];
        }

        byte[] bytes = new byte[remainingLength];

        raf.readFully(bytes);

        return bytes;
    }

    /**
     * Closes the underlying RandomAccessFile.
     */
    @Override
    public void close() throws IOException
    {
        raf.close();
    }
}