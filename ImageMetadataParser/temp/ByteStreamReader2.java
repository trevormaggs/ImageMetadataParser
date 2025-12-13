package common;

import java.io.IOException;
import java.nio.ByteOrder;

/**
 * A generic interface for positional, stream-based binary reading.
 * 
 * <p>
 * Supports seeking, skipping, and mark/reset operations alongside various primitive read operations
 * with configurable byte order.
 * </p>
 */
public interface ByteStreamReader2
{
    /**
     * Sets the byte order for interpreting multi-byte primitive values.
     * 
     * @param order
     *        the {@link ByteOrder} to use
     */
    void setByteOrder(ByteOrder order);

    /**
     * Gets the current byte order being used for data interpretation.
     * 
     * @return the current {@link ByteOrder}
     */
    ByteOrder getByteOrder();

    /**
     * Returns the current absolute byte position in the stream.
     * 
     * @return the current position
     * @throws IOException
     *         if an I/O error occurs
     */
    long getCurrentPosition() throws IOException;

    /**
     * Skips {@code n} bytes relative to the current position.
     * 
     * @param n
     *        number of bytes to skip (can be negative)
     * @throws IOException
     *         if an I/O error occurs
     */
    void skip(long n) throws IOException;

    /**
     * Sets the stream pointer to a specific absolute position.
     * 
     * @param n
     *        the absolute byte offset
     * @throws IOException
     *         if an I/O error occurs or position is invalid
     */
    void seek(long n) throws IOException;

    /**
     * Records the current position. Implementations should support nested calls via a stack.
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    void mark() throws IOException;

    /**
     * Returns to the last marked position.
     * 
     * @throws IOException
     *         if an I/O error occurs or no mark exists
     */
    void reset() throws IOException;

    /**
     * Reads a signed 8-bit byte.
     * 
     * @throws IOException
     *         if EOF is reached or an I/O error occurs
     */
    byte readByte() throws IOException;

    /**
     * Reads a block of bytes into a new array.
     * 
     * @param length
     *        number of bytes to read
     * @return the resulting byte array
     * 
     * @throws IOException
     *         if EOF is reached or an I/O error occurs
     */
    byte[] readBytes(int length) throws IOException;

    /**
     * Reads an 8-bit byte and returns it as an unsigned integer (0-255).
     * 
     * @throws IOException
     *         if EOF is reached or an I/O error occurs
     */
    int readUnsignedByte() throws IOException;

    /**
     * Reads a 16-bit short value using the current byte order.
     * 
     * @throws IOException
     *         if EOF is reached or an I/O error occurs
     */
    short readShort() throws IOException;

    /**
     * Reads an unsigned 16-bit short value (0-65535).
     * 
     * @throws IOException
     *         if EOF is reached or an I/O error occurs
     */
    int readUnsignedShort() throws IOException;

    /**
     * Reads a 32-bit signed integer using the current byte order.
     * 
     * @throws IOException
     *         if EOF is reached or an I/O error occurs
     */
    int readInteger() throws IOException;

    /**
     * Reads a 32-bit unsigned integer and returns it as a long.
     * 
     * @throws IOException
     *         if EOF is reached or an I/O error occurs
     */
    long readUnsignedInteger() throws IOException;

    /**
     * Reads a 64-bit signed long using the current byte order.
     * 
     * @throws IOException
     *         if EOF is reached or an I/O error occurs
     */
    long readLong() throws IOException;

    /**
     * Reads a 32-bit floating point value.
     * 
     * @throws IOException
     *         if EOF is reached or an I/O error occurs
     */
    float readFloat() throws IOException;

    /**
     * Reads a 64-bit floating point value.
     * 
     * @throws IOException
     *         if EOF is reached or an I/O error occurs
     */
    double readDouble() throws IOException;

    /**
     * Reads a null-terminated string.
     * 
     * @return the string (excluding the null terminator).
     * @throws IOException
     *         if EOF is reached before a null terminator is found
     */
    String readString() throws IOException;
}