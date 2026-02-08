package common;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * A seekable binary stream writer backed by {@link java.io.RandomAccessFile}.
 *
 * <p>
 * Extends the reader to provide in-place file modification capabilities. Supports writing primitive
 * types with configurable byte order, concerning the endian-ness. This is particularly useful for
 * surgical
 * metadata patching where only specific segments of an image file need to be updated.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.1
 * @since 3 February 2026
 */
public class ImageRandomAccessWriter extends ImageRandomAccessReader
{
    /**
     * Instantiates a writer with a specific byte order.
     *
     * @param fpath
     *        the path to the file
     * @param order
     *        the {@link ByteOrder} for multi-byte interpretation
     *
     * @throws IOException
     *         if the file cannot be opened in {@code rw} mode
     */
    public ImageRandomAccessWriter(Path fpath, ByteOrder order) throws IOException
    {
        super(fpath, order, "rw");
    }

    /**
     * Instantiates a writer using {@link ByteOrder#BIG_ENDIAN}.
     *
     * @param fpath
     *        the path to the file
     *
     * @throws IOException
     *         if the file cannot be opened in {@code rw} mode
     */
    public ImageRandomAccessWriter(Path fpath) throws IOException
    {
        this(fpath, ByteOrder.BIG_ENDIAN);
    }

    /**
     * Checks if the current access mode prevents writing.
     *
     * @return {@code true} if the stream is read-only
     */
    public boolean isReadOnly()
    {
        return mode == null || !mode.contains("w");
    }

    /**
     * Writes a single signed byte at the current position.
     *
     * @param v
     *        the byte value to write
     *
     * @throws IOException
     *         if the stream is read-only or an I/O error occurs
     */
    public void writeByte(byte v) throws IOException
    {
        if (isReadOnly())
        {
            throw new IOException("Read-only mode");
        }

        raf.writeByte(v);
    }

    /**
     * Writes a single unsigned byte (0-255) at the current position.
     *
     * @param v
     *        the value to write
     *
     * @throws IOException
     *         if the stream is read-only or an I/O error occurs
     */
    public void writeUnsignedByte(int v) throws IOException
    {
        if (isReadOnly())
        {
            throw new IOException("Read-only mode");
        }

        raf.write(v & 0xFF);
    }

    /**
     * Writes an array of bytes at the current position and advances the pointer.
     *
     * @param bytes
     *        the data to write
     *
     * @throws IOException
     *         if the stream is read-only or an I/O error occurs
     */
    public void writeBytes(byte[] bytes) throws IOException
    {
        if (isReadOnly())
        {
            throw new IOException("Read-only mode");
        }

        if (bytes != null)
        {
            raf.write(bytes);
        }
    }

    /**
     * Writes an array of bytes, ensuring the file pointer advances by exactly the specified length.
     * 
     * <p>
     * If the input array is shorter than {@code length}, it is padded with trailing zeros (0x00).
     * If the input array is longer than {@code length}, only the first {@code length} bytes are
     * written.
     * </p>
     *
     * @param bytes
     *        the data to write (may be null, treated as all zeros)
     * @param length
     *        the exact number of bytes to occupy in the file
     * @throws IOException
     *         if the stream is read-only or an I/O error occurs
     */
    public void writeBytes(byte[] bytes, int length) throws IOException
    {
        if (isReadOnly())
        {
            throw new IOException("Read-only mode");
        }

        int bytesToWrite = (bytes == null) ? 0 : bytes.length;

        if (bytesToWrite > 0)
        {
            raf.write(bytes, 0, Math.min(bytesToWrite, length));
        }

        if (bytesToWrite < length)
        {
            for (int i = 0; i < (length - bytesToWrite); i++)
            {
                raf.write(0x00);
            }
        }
    }

    /**
     * Writes a 32-bit integer respecting the current byte order.
     *
     * @param value
     *        the integer value to write
     *
     * @throws IOException
     *         if the stream is read-only or an I/O error occurs
     */
    public void writeInteger(int value) throws IOException
    {
        if (isReadOnly())
        {
            throw new IOException("Read-only mode");
        }

        if (byteOrder == ByteOrder.LITTLE_ENDIAN)
        {
            value = Integer.reverseBytes(value);
        }

        raf.writeInt(value);
    }

    /**
     * Writes a 16-bit short respecting the current byte order.
     *
     * @param value
     *        the short value to write
     *
     * @throws IOException
     *         if the stream is read-only or an I/O error occurs
     */
    public void writeShort(short value) throws IOException
    {
        if (isReadOnly())
        {
            throw new IOException("Read-only mode");
        }

        if (byteOrder == ByteOrder.LITTLE_ENDIAN)
        {
            value = Short.reverseBytes(value);
        }

        raf.writeShort(value);
    }

    /**
     * Writes a 24-bit integer respecting the current byte order.
     *
     * @param value
     *        the integer to write (only the lower 24 bits are used)
     *
     * @throws IOException
     *         if the stream is read-only or an I/O error occurs
     */
    public void writeUnsignedInteger24(int value) throws IOException
    {
        if (isReadOnly())
        {
            throw new IOException("Read-only mode");
        }

        if (byteOrder == ByteOrder.LITTLE_ENDIAN)
        {
            raf.write(value & 0xFF);
            raf.write((value >> 8) & 0xFF);
            raf.write((value >> 16) & 0xFF);
        }

        else
        {
            raf.write((value >> 16) & 0xFF);
            raf.write((value >> 8) & 0xFF);
            raf.write(value & 0xFF);
        }
    }

    /**
     * Writes a string followed by a null terminator (0x00).
     *
     * @param s
     *        the string to write
     * @param charset
     *        the character set to use for encoding
     *
     * @throws IOException
     *         if the stream is read-only or an I/O error occurs
     */
    public void writeNullTerminatedString(String s, Charset charset) throws IOException
    {
        if (s != null)
        {
            writeBytes(s.getBytes(charset));
        }

        raf.write(0x00);
    }
}