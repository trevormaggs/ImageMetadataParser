package common;

import java.io.RandomAccessFile;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
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
 * Additionally supports in-place file modification when opened in a writable mode.
 * </p>
 * 
 * <p>
 * Unlike standard input streams, this class maintains a <b>stack</b> of marked positions, allowing
 * for nested "diving" into sub-structures and returning to previous offsets in LIFO order.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.2
 * @since 25 January 2026
 */
public class ImageRandomAccessReader2 implements ByteStreamReader
{
    private final RandomAccessFile raf;
    private final long realFileSize;
    private final Deque<Long> positionStack = new ArrayDeque<>();
    private final String mode;
    private ByteOrder byteOrder;

    /**
     * Constructs a reader for the specified file path with a specific access mode. Use {@code r}
     * for read-only or {@code rw} for read-write. Useful for surgical patching tasks.
     *
     * @param fpath
     *        the path to the image file
     * @param order
     *        the byte order for interpreting multi-byte values
     * @param mode
     *        the access mode. It can only recognise {@code r}, {@code rw}, {@code rws}, or
     *        {@code rwd}
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public ImageRandomAccessReader2(Path fpath, ByteOrder order, String mode) throws IOException
    {
        this.raf = new RandomAccessFile(fpath.toFile(), mode);
        this.byteOrder = Objects.requireNonNull(order, "Byte order cannot be null");
        this.mode = mode;
        this.realFileSize = raf.length();
    }

    /**
     * Convenience constructor with Big Endian default. Note, this is configured as a read-only
     * operation.
     * 
     * @param fpath
     *        the path to the image file
     */
    public ImageRandomAccessReader2(Path fpath) throws IOException
    {
        this(fpath, ByteOrder.BIG_ENDIAN, "r");
    }

    /**
     * Constructs a reader for the specified file path, respecting the given byte order. Note, this
     * is configured as a read-only operation.
     * 
     * @param fpath
     *        the path to the image file
     * @param order
     *        the byte order for interpreting multi-byte values
     */
    public ImageRandomAccessReader2(Path fpath, ByteOrder order) throws IOException
    {
        this(fpath, order, "r");
    }

    /**
     * Closes the underlying RandomAccessFile.
     */
    @Override
    public void close() throws IOException
    {
        raf.close();
    }

    /**
     * Returns the file size in which this utility class deals with.
     *
     * @return the readable file length
     */
    @Override
    public long length()
    {
        return realFileSize;
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
     * Reads a single byte from the current position in the stream and advances the file pointer.
     * 
     * @return the signed byte value
     * 
     * @throws IOException
     *         if an I/O error occurs or if the file has reached end of file
     */
    @Override
    public byte readByte() throws IOException
    {
        checkBounds(1);

        return raf.readByte();
    }

    /**
     * Reads a sequence of bytes from the current position in the stream and advances the file
     * pointer.
     *
     * @param length
     *        the number of bytes to read
     * @return a new byte array containing the read bytes
     * 
     * @throws IOException
     *         if an I/O error occurs or when the file reaches the end of file before reading all
     *         the bytes
     */
    @Override
    public byte[] readBytes(int length) throws IOException
    {
        if (length < 0)
        {
            throw new IllegalArgumentException("Length cannot be negative");
        }

        if (length == 0)
        {
            return new byte[0];
        }

        checkBounds(length);
        byte[] bytes = new byte[length];

        raf.readFully(bytes);

        return bytes;
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
        long offset = getCurrentPosition() + n;

        if (offset < 0 || offset > realFileSize)
        {
            throw new EOFException("Attempted to skip to [" + offset + "], which is out of file bounds [0-" + realFileSize + "]");
        }

        raf.seek(offset);
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
        return raf.readUnsignedByte();
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
        checkBounds(2);
        short value = raf.readShort();

        return (byteOrder == ByteOrder.LITTLE_ENDIAN) ? Short.reverseBytes(value) : value;
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
        checkBounds(4);
        int value = raf.readInt();

        return (byteOrder == ByteOrder.LITTLE_ENDIAN) ? Integer.reverseBytes(value) : value;
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
     * Reads a 3-byte integer and returns it as a 32-bit signed integer.
     * 
     * @return the 24-bit value as an integer
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
    public int readUnsignedInt24() throws IOException
    {
        byte[] b = readBytes(3);

        if (byteOrder == ByteOrder.LITTLE_ENDIAN)
        {
            return ((b[2] & 0xFF) << 16) | ((b[1] & 0xFF) << 8) | (b[0] & 0xFF);
        }

        else
        {
            return ((b[0] & 0xFF) << 16) | ((b[1] & 0xFF) << 8) | (b[2] & 0xFF);
        }
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
        checkBounds(8);
        long value = raf.readLong();

        return (byteOrder == ByteOrder.LITTLE_ENDIAN) ? Long.reverseBytes(value) : value;
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
     * Reads a null-terminated string (C-style string) from the current position, consuming the null
     * terminator.
     * 
     * <p>
     * This method scans the file for a null byte ({@code 0x00}). The file pointer is advanced past
     * both the string data and the null terminator. The returned string is decoded using
     * {@code ISO-8859-1} and does not include the null terminator.
     * </p>
     *
     * @return the string content without the null terminator
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
            /*
             * Just advances the pointer to reach
             * the null terminator if present
             */
            while (raf.readByte() != 0x00);
        }

        catch (EOFException exc)
        {
            throw new IOException("Null terminator not found for string starting at [" + startPosition + "]", exc);
        }

        long endPosition = getCurrentPosition();
        long length = (endPosition - startPosition - 1);

        if (length > Integer.MAX_VALUE)
        {
            throw new UnsupportedOperationException("String length exceeds maximum supported size");
        }

        mark();

        try

        {
            seek(startPosition);

            byte[] stringBytes = readBytes((int) length);

            return new String(stringBytes, StandardCharsets.ISO_8859_1);
        }

        finally
        {
            seek(endPosition);
            positionStack.pop();
        }
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
     * @return either {@link java.nio.ByteOrder#BIG_ENDIAN} or
     *         {@link java.nio.ByteOrder#LITTLE_ENDIAN}
     */
    public ByteOrder getByteOrder()
    {
        return byteOrder;
    }

    /**
     * Records the current position in the stream by pushing it onto an internal stack. A subsequent
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

        raf.seek(positionStack.pop());
    }

    /**
     * Reads a single byte at the specified absolute offset within the stream without advancing the
     * stream's position.
     * 
     * <p>
     * This method interprets the {@code offset} parameter as the position from the start of the
     * file (index 0), suitable for absolute file mapping used in formats like TIFF. The file
     * pointer is restored to its original position before this method returns, even if a read error
     * occurs.
     * </p>
     * 
     * @param offset
     *        the absolute position from the start of the file
     * @return the byte value at the target position
     * @throws IOException
     *         if an I/O error occurs or the target position is invalid
     */
    public byte peek(long offset) throws IOException
    {
        long currentPosition = getCurrentPosition();

        try
        {
            raf.seek(offset);

            return raf.readByte();
        }

        finally
        {
            raf.seek(currentPosition);
        }
    }

    /**
     * Reads a sequence of bytes at the specified absolute offset within the stream without
     * advancing the stream's position.
     * 
     * <p>
     * This method interprets the {@code offset} parameter as the position from the start of the
     * file (index 0), suitable for absolute file mapping used in formats like TIFF. The file
     * pointer is restored to its original position before this method returns, even if a read error
     * occurs.
     * </p>
     * 
     * @param offset
     *        the absolute position from the start of the file
     * @param length
     *        the total number of bytes to include in the sub-array
     * @return the sub-array of bytes
     * @throws IOException
     *         if an I/O error occurs
     */
    public byte[] peek(long offset, int length) throws IOException
    {
        if (length < 0)
        {
            throw new IllegalArgumentException("Length cannot be negative");
        }

        if (offset + length > length())
        {
            throw new EOFException("Peek request exceeds file bounds");
        }

        long originalPos = getCurrentPosition();

        try
        {
            byte[] data = new byte[length];

            raf.seek(offset);
            raf.readFully(data);

            return data;
        }

        finally
        {
            raf.seek(originalPos);
        }
    }

    /**
     * Reads the entire contents of the file from the beginning (position 0) to the end of the file.
     * 
     * <p>
     * This operation is non-advancing: the file pointer is guaranteed to be restored to its
     * original position (the position it held before this method was called) upon successful
     * completion or after an exception is thrown.
     * </p>
     *
     * @return a new byte array containing the entire file content or an empty array if the file is
     *         empty
     * 
     * @throws IOException
     *         if an I/O error occurs during file access or if the file size exceeds the maximum
     *         Java array size (2GB)
     */
    public byte[] readAllBytes() throws IOException
    {
        mark();

        try
        {
            long fileSize = raf.length();

            if (fileSize <= 0)
            {
                return new byte[0];
            }

            // Make sure the length fits within a Java array (max 2GB)
            if (fileSize > Integer.MAX_VALUE)
            {
                throw new IOException("File size [" + fileSize + "] exceeds the maximum Java array size.");
            }

            byte[] bytes = new byte[(int) fileSize];

            raf.seek(0L);
            raf.readFully(bytes);

            return bytes;
        }

        finally
        {
            reset();
        }
    }

    /**
     * Checks whether the current reader is configured as a read-only operation.
     * 
     * <p>
     * A reader is considered read-only if the access mode used during construction does not contain
     * the {@code w} (write) character.
     * </p>
     *
     * @return {@code true} if the access mode is read-only ({@code r}), {@code false} if it allows
     *         writing ({@code rw}, {@code rws}, {@code rwd})
     */
    public boolean isReadOnly()
    {
        return !mode.contains("w");
    }
    /**
     * Writes a sequence of bytes at the current position.
     *
     * @param bytes
     *        the byte array to be written to the file
     * 
     * @throws IOException
     *         if the file is read-only or a write error occurs
     */
    public void write(byte[] bytes) throws IOException
    {
        if (isReadOnly())
        {
            throw new IOException("Cannot write to a file opened in read-only mode ['r']");
        }

        else if (bytes != null)
        {
            checkBounds(bytes.length);
            raf.write(bytes);
        }
    }

    /**
     * Writes a 32-bit integer at the current position, respecting the configured byte order.
     *
     * @param value
     *        the integer value to write
     * @throws IOException
     *         if the file is read-only or a write error occurs
     */
    public void writeInt(int value) throws IOException
    {
        if (isReadOnly())
        {
            throw new IOException("Cannot write to a file opened in read-only mode");
        }

        if (byteOrder == ByteOrder.LITTLE_ENDIAN)
        {
            value = Integer.reverseBytes(value);
        }

        raf.writeInt(value);
    }

    /**
     * Writes a 16-bit short at the current position, respecting the configured byte order.
     *
     * @param value
     *        the short value to write
     * @throws IOException
     *         if the file is read-only or a write error occurs
     */
    public void writeShort(short value) throws IOException
    {
        if (isReadOnly())
        {
            throw new IOException("Cannot write to a file opened in read-only mode");
        }

        if (byteOrder == ByteOrder.LITTLE_ENDIAN)
        {
            value = Short.reverseBytes(value);
        }

        raf.writeShort(value);
    }

    /**
     * Ensures that the requested number of bytes is available within the physical file limits.
     * 
     * @param byteLen
     *        the number of bytes requested
     * 
     * @throws EOFException
     *         if the read exceeds the file length
     */
    private void checkBounds(int byteLen) throws IOException
    {
        if (getCurrentPosition() + byteLen > realFileSize)
        {
            throw new java.io.EOFException(String.format("Requested %d bytes, but only %d bytes remain.", byteLen, realFileSize - getCurrentPosition()));
        }
    }
}