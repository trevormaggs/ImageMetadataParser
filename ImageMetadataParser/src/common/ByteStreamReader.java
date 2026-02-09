package common;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * A generic interface for positional, stream-based binary reading.
 * 
 * <p>
 * Supports seeking, skipping, and mark/reset operations alongside various primitive read operations
 * with configurable byte order.
 * </p>
 */
public interface ByteStreamReader extends AutoCloseable
{
    /**
     * Narrows the close method to throw IOException instead of Exception so it can be used in
     * try-with-resources blocks without broad catch requirements.
     */
    @Override
    void close() throws IOException;
    public Path getFilename();
    public void setByteOrder(ByteOrder order);
    public ByteOrder getByteOrder();
    public long length();
    public long getCurrentPosition() throws IOException;
    public void skip(long n) throws IOException;
    public void seek(long n) throws IOException;
    public void mark() throws IOException;
    public void reset() throws IOException;
    public byte peek(long offset) throws IOException;
    public byte[] peek(long offset, int length) throws IOException;
    public byte readByte() throws IOException;
    public byte[] readBytes(int length) throws IOException;
    public int readUnsignedByte() throws IOException;
    public short readShort() throws IOException;
    public int readUnsignedShort() throws IOException;
    public int readInteger() throws IOException;
    public long readUnsignedInteger() throws IOException;
    public int readUnsignedInt24() throws IOException;
    public long readLong() throws IOException;
    public float readFloat() throws IOException;
    public double readDouble() throws IOException;
    public String readString() throws IOException;
}
