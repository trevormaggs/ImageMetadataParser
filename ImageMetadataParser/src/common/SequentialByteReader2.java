package common;

import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Deque;

public class SequentialByteReader2 extends AbstractByteReader2
{
    private int bufferIndex;
    private final Deque<Integer> markPositionStack;

    public SequentialByteReader2(byte[] buf)
    {
        this(buf, ByteOrder.BIG_ENDIAN);
    }

    public SequentialByteReader2(byte[] buf, ByteOrder order)
    {
        this(buf, 0, order);
    }

    public SequentialByteReader2(byte[] buf, int offset)
    {
        this(buf, offset, ByteOrder.BIG_ENDIAN);
    }

    public SequentialByteReader2(byte[] buf, int offset, ByteOrder order)
    {
        super(buf, offset, order);

        this.bufferIndex = 0;
        this.markPositionStack = new ArrayDeque<>();
    }

    public int getCurrentPosition()
    {
        return bufferIndex;
    }

    public int remaining()
    {
        return length() - bufferIndex;
    }

    public boolean hasRemaining(int n)
    {
        return remaining() >= n;
    }

    public byte readByte()
    {
        if (!hasRemaining(1))
        {
            throw new IndexOutOfBoundsException("End of buffer reached. Cannot read beyond position [" + length() + "]");
        }

        return getByte(bufferIndex++);
    }

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

    public short readUnsignedByte()
    {
        return (short) (readByte() & 0xFF);
    }

    public short readShort()
    {
        return (short) readValue(2);
    }

    public int readUnsignedShort()
    {
        return readShort() & 0xFFFF;
    }

    public int readInteger()
    {
        return (int) readValue(4);
    }

    public long readUnsignedInteger()
    {
        return readInteger() & 0xFFFFFFFFL;
    }

    public long readLong()
    {
        return readValue(8);
    }

    public float readFloat()
    {
        return Float.intBitsToFloat(readInteger());
    }

    public double readDouble()
    {
        return Double.longBitsToDouble(readLong());
    }

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

    public void seek(int pos)
    {
        if (pos < 0 || pos > length())
        {
            throw new IndexOutOfBoundsException("Position [" + pos + "] out of bounds. Valid range is [0.." + length() + "].");
        }

        bufferIndex = pos;
    }

    public void mark()
    {
        markPositionStack.push(bufferIndex);
    }

    public void reset()
    {
        if (markPositionStack.isEmpty())
        {
            throw new IllegalStateException("Cannot reset position: mark stack is empty");
        }

        bufferIndex = markPositionStack.pop();
    }

    private long readValue(int numBytes)
    {
        if (!hasRemaining(numBytes))
        {
            throw new IndexOutOfBoundsException("Cannot read [" + numBytes + "] bytes. Only [" + remaining() + "] remaining.");
        }

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
}