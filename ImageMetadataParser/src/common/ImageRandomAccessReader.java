package common;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * A seekable binary stream reader backed by {@link RandomAccessFile}.
 *
 * <p>
 * Provides refined methods for reading primitive types with configurable byte order to determine
 * endian-ness. Unlike standard streams, this class maintains an internal <b>LIFO stack</b> for
 * marked positions, allowing for nested navigation into sub-structures (i.e. TIFF IFDs or PNG
 * chunks) and returning to previous offsets via {@link #reset()}.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.3
 * @since 25 January 2026
 */
public class ImageRandomAccessReader implements ByteStreamReader
{
    private final Deque<Long> positionStack = new ArrayDeque<>();
    private final Path pfile;
    protected final RandomAccessFile raf;
    protected final long realFileSize;
    protected ByteOrder byteOrder;
    protected final String mode;

    /**
     * Initialises a read-only reader with the specified byte order.
     *
     * @param fpath
     *        the path to the file
     * @param order
     *        the {@link ByteOrder} for multi-byte interpretation
     */
    public ImageRandomAccessReader(Path fpath, ByteOrder order) throws IOException
    {
        this(fpath, order, "r");
    }

    /**
     * Initialises a read-only reader using {@link ByteOrder#BIG_ENDIAN}.
     *
     * @param fpath
     *        the path to the file
     */
    public ImageRandomAccessReader(Path fpath) throws IOException
    {
        this(fpath, ByteOrder.BIG_ENDIAN, "r");
    }

    /**
     * Primary constructor allowing specific file access modes.
     *
     * @param fpath
     *        the path to the file
     * @param order
     *        the {@link ByteOrder} for multi-byte interpretation
     * @param mode
     *        the access mode. It can only recognise {@code r}, {@code rw}, {@code rws}, or
     *        {@code rwd}
     * @throws IOException
     *         if an I/O error occurs
     */
    protected ImageRandomAccessReader(Path fpath, ByteOrder order, String mode) throws IOException
    {
        this.pfile = fpath;
        this.raf = new RandomAccessFile(fpath.toFile(), mode);
        this.mode = mode;
        this.byteOrder = Objects.requireNonNull(order, "Byte order cannot be null");
        this.realFileSize = raf.length();
    }

    /**
     * Closes the underlying RandomAccessFile resource.
     */
    @Override
    public void close() throws IOException
    {
        raf.close();
    }

    /**
     * Updates the byte order for subsequent multi-byte read operations.
     *
     * @param order
     *        the new byte order
     */
    @Override
    public void setByteOrder(ByteOrder order)
    {
        this.byteOrder = Objects.requireNonNull(order, "Byte order cannot be null");
    }

    /**
     * Returns the current byte order used for interpreting data.
     *
     * @return the active {@link ByteOrder}, either {@link ByteOrder#BIG_ENDIAN} or
     *         {@link ByteOrder#LITTLE_ENDIAN}
     */
    @Override
    public ByteOrder getByteOrder()
    {
        return byteOrder;
    }

    /**
     * Returns the fixed file length recorded at the time of construction.
     *
     * @return the file size in bytes
     */
    @Override
    public long length()
    {
        return realFileSize;
    }

    /**
     * Returns the current absolute byte offset of the file pointer.
     *
     * @return the current position
     */
    @Override
    public long getCurrentPosition() throws IOException
    {
        return raf.getFilePointer();
    }

    /**
     * Moves the file pointer by a relative offset.
     *
     * @param n
     *        the number of bytes to skip (positive to move forward, negative for backward)
     * @throws EOFException
     *         if the resulting position is out of file bounds
     * @throws IOException
     *         if an I/O error occurs or the stream ends prematurely
     */
    @Override
    public void skip(long n) throws IOException
    {
        long offset = getCurrentPosition() + n;

        if (offset < 0 || offset > realFileSize)
        {
            throw new EOFException("Skip target [" + offset + "] out of bounds [0-" + realFileSize + "]");
        }

        raf.seek(offset);
    }

    /**
     * Moves the file pointer to an absolute offset.
     *
     * @param n
     *        the target position (index 0)
     * @throws IllegalArgumentException
     *         if the position is negative
     * @throws IOException
     *         if an I/O error occurs or the stream ends prematurely
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
     * Pushes the current file pointer onto the internal mark stack. A subsequent call to
     * {@link #reset()} will pop this position and return the reader to it.
     *
     * @throws IOException
     *         if an I/O error occurs while retrieving the file pointer
     */
    @Override
    public void mark() throws IOException
    {
        positionStack.push(getCurrentPosition());
    }

    /**
     * Returns to the position recorded by the most recent {@link #mark()}.
     * This operation pops the position from the stack.
     *
     * @throws IllegalStateException
     *         if the mark stack is empty
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
    public void reset() throws IOException
    {
        if (positionStack.isEmpty())
        {
            throw new IllegalStateException("Mark stack is empty");
        }

        raf.seek(positionStack.pop());
    }

    /**
     * Reads a single byte at an absolute offset without advancing the current file pointer.
     *
     * @param offset
     *        the absolute position to read from
     * @return the signed byte value
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
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
     * Reads a sequence of bytes at an absolute offset without moving the current file pointer.
     *
     * @param offset
     *        the absolute position to start reading from
     * @param length
     *        the number of bytes to read
     * @return a new sub-array containing the read data
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
    public byte[] peek(long offset, int length) throws IOException
    {
        if (length < 0)
        {
            throw new IllegalArgumentException("Length cannot be negative");
        }

        if (offset + length > realFileSize)
        {
            throw new EOFException("Peek request exceeds file length");
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
     * Reads a signed byte and advances the pointer by 1 step forward.
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
     * Reads a sequence of bytes and advances the pointer by the specified length.
     *
     * @param length
     *        the number of bytes to read
     * @return a new array containing the read bytes
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

        else if (length == 0)
        {
            return new byte[0];
        }

        checkBounds(length);
        byte[] bytes = new byte[length];
        raf.readFully(bytes);

        return bytes;
    }

    /**
     * Reads an unsigned byte (0-255).
     *
     * @return the unsigned 8-bit value as an integer
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
    public int readUnsignedByte() throws IOException
    {
        checkBounds(1);

        return raf.readUnsignedByte();
    }

    /**
     * Reads a 16-bit short value respecting the current byte order.
     *
     * @return the signed short value
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
     * Reads an unsigned 16-bit short value (0-65535).
     *
     * @return the unsigned short value as an integer
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
     * Reads a 32-bit integer respecting the current byte order.
     *
     * @return the signed integer value
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
     * Reads an unsigned 32-bit integer as a long.
     *
     * @return the unsigned integer value
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
     * Reads a 24-bit integer respecting the current byte order.
     *
     * @return the 24-bit value as a signed 32-bit integer
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
     * Reads a 64-bit long respecting the current byte order.
     *
     * @return the signed long value
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
     * Reads a 32-bit float respecting the current byte order.
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
     * Reads a 64-bit double respecting the current byte order.
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
     * Reads a null-terminated string using ISO-8859-1 encoding.
     *
     * @see #readString(Charset)
     */
    @Override
    public String readString() throws IOException
    {
        return readString(StandardCharsets.ISO_8859_1);
    }

    /**
     * Gets the file name, which this stream is based on.
     * 
     * @return the file encapsulated in a Path resource
     */
    public Path getFilename()
    {
        return pfile;
    }

    /**
     * Reads a null-terminated string (C-style) using the specified charset. The file pointer is
     * advanced past both the string data and the null terminator.
     *
     * @param charset
     *        the character encoding for decoding the string
     * @return the decoded string without the null terminator
     *
     * @throws UnsupportedOperationException
     *         if the detected string length exceeds {@link Integer#MAX_VALUE}
     * @throws IOException
     *         if a null terminator is not found or EOF is reached
     */
    public String readString(Charset charset) throws IOException
    {
        long startPosition = getCurrentPosition();

        try
        {
            /*
             * Just advances the pointer to reach
             * the null terminator if present
             */
            while (raf.readByte() != 0x00)
            {
                ;
            }
        }

        catch (EOFException exc)
        {
            throw new IOException("Null terminator not found starting at [" + startPosition + "]", exc);
        }

        long endPosition = getCurrentPosition();
        long length = (endPosition - startPosition - 1);

        if (length > Integer.MAX_VALUE)
        {
            throw new UnsupportedOperationException("String length exceeds maximum supported size: " + length);
        }

        positionStack.push(startPosition);

        try
        {
            seek(startPosition);
            byte[] stringBytes = readBytes((int) length);

            return new String(stringBytes, charset);
        }

        finally
        {
            positionStack.pop();
            seek(endPosition);
        }
    }

    /**
     * Reads the entire file into a byte array. This is a non-advancing operation, the file pointer
     * is restored to its original position.
     *
     * @return a new byte array containing the entire file binary data or an empty array if the file
     *         is empty
     *
     * @throws UnsupportedOperationException
     *         if the detected string length exceeds {@link Integer#MAX_VALUE}
     * @throws IOException
     *         if file size exceeds 2GB
     */
    public byte[] readAllBytes() throws IOException
    {
        mark();

        try
        {
            if (realFileSize <= 0)
            {
                return new byte[0];
            }

            // Make sure the length fits within a Java array (max 2GB)
            if (realFileSize > Integer.MAX_VALUE)
            {
                throw new UnsupportedOperationException("File size [" + realFileSize + "] exceeds maximum supported size");
            }

            byte[] bytes = new byte[(int) realFileSize];
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
     * Validates that enough bytes remain in the file for the subsequent operation.
     *
     * @param byteLen
     *        the number of bytes required
     *
     * @throws EOFException
     *         if insufficient bytes remain
     */
    protected void checkBounds(int byteLen) throws IOException
    {
        if (getCurrentPosition() + byteLen > realFileSize)
        {
            throw new EOFException(String.format("Requested %d bytes, but only %d remain.", byteLen, realFileSize - getCurrentPosition()));
        }
    }
}