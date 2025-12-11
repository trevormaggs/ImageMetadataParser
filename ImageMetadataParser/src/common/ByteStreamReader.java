package common;

import java.io.IOException;

interface ByteStreamReader
{
    public long getCurrentPosition();
    public void skip(long n) throws IOException;
    public void seek(long n) throws IOException;
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
