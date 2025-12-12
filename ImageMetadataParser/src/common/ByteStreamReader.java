package common;

import java.io.IOException;
import java.nio.ByteOrder;

public interface ByteStreamReader extends AutoCloseable
{
    public void setByteOrder(ByteOrder order);
    public ByteOrder getByteOrder();
    public long getCurrentPosition() throws IOException;
    public void skip(long n) throws IOException;
    public void seek(long n) throws IOException;
    public void mark() throws IOException;
    public void reset() throws IOException;
    public byte readByte() throws IOException;
    public byte[] readBytes(int length) throws IOException;
    public int readUnsignedByte() throws IOException;
    public short readShort() throws IOException;
    public int readUnsignedShort() throws IOException;
    public int readInteger() throws IOException;
    public long readUnsignedInteger() throws IOException;
    public long readLong() throws IOException;
    public float readFloat() throws IOException;
    public double readDouble() throws IOException;
    public String readString() throws IOException;
}
