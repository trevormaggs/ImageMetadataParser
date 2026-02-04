package png;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.CRC32;
import common.ByteValueConverter;
import common.MetadataConstants;

/**
 * Represents an individual chunk in a PNG file.
 *
 * <p>
 * PNG chunks consist of a length (4 bytes), type (4 bytes), data (0-n bytes), and a CRC (4 bytes).
 * This class decodes the "ancillary bits" from the type casing to determine how encoders should
 * handle unknown chunks.
 * </p>
 *
 * <p>
 * Refer to the PNG Specification for information on chunk layout and bit-flag meanings.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class PngChunk
{
    private final long length;
    private final byte[] typeBytes;
    private final int crc;
    protected final byte[] payload;
    private final boolean ancillaryBit;
    private final boolean privateBit;
    private final boolean reservedBit;
    private final boolean safeToCopyBit;
    private final long fileOffset;

    /**
     * Constructs a new {@code PngChunk}, including an optional Exif parser.
     *
     * @param length
     *        the length of the chunk's data field (excluding type and CRC)
     * @param typeBytes
     *        the raw 4-byte chunk type
     * @param crc32
     *        the CRC value read from the file
     * @param data
     *        raw chunk data
     * @param fileOffset
     *        the absolute physical position in the file where the chunk begins
     */
    public PngChunk(long length, byte[] typeBytes, int crc32, byte[] data, long fileOffset)
    {
        this.length = length;
        this.typeBytes = Arrays.copyOf(typeBytes, typeBytes.length);
        this.crc = crc32;
        this.payload = data;
        this.fileOffset = fileOffset;

        boolean[] flags = extractPropertyBits(ByteValueConverter.toInteger(typeBytes, ByteOrder.BIG_ENDIAN));
        this.ancillaryBit = flags[0];
        this.privateBit = flags[1];
        this.reservedBit = flags[2];
        this.safeToCopyBit = flags[3];
    }

    /**
     * Retrieves the length of data bytes held by this chunk.
     *
     * @return the length
     */
    public long getLength()
    {
        return length;
    }

    /**
     * Returns the raw 4-byte chunk type identifier. Useful when CRC recalculation is required,
     * where CRC is calculated over type and data.
     *
     * @return the 4-byte array containing the identifier type
     */
    public byte[] getTypeBytes()
    {
        return typeBytes;
    }

    /**
     * Returns the chunk type.
     *
     * @return the type defined as s {@link ChunkType}
     */
    public ChunkType getType()
    {
        return ChunkType.fromBytes(typeBytes);
    }

    /**
     * Returns a four-byte CRC computed on the preceding bytes in the chunk, excluding the
     * length field.
     *
     * @return a CRC value defined as an integer
     */
    public int getCrc()
    {
        return crc;
    }

    /**
     * Calculates the CRC-32 checksum for this chunk (type code + data). Note, PNG CRC-32 is
     * calculated over the Type field and the Data field. It does not include the Length field.
     *
     * @return The calculated CRC-32 value.
     */
    public int calculateCrc()
    {
        CRC32 crc32 = new CRC32();

        crc32.update(typeBytes);
        crc32.update(payload);

        return (int) crc32.getValue();
    }

    /**
     * Returns the raw payload bytes.
     *
     * @return the raw data
     */
    public byte[] getPayloadArray()
    {
        return payload;
    }

    /**
     * Returns the offset positioned at the beginning of the whole chunk segment.
     *
     * @return the offset
     */
    public long getFileOffset()
    {
        return fileOffset;
    }

    /**
     * Returns the offset positioned at the beginning of the data segment. It moves 8 bytes from the
     * file offset position (Length - 4 bytes and Type - 4 bytes).
     *
     * @return the offset pointing to the start of the Data segment
     */
    public long getDataOffset()
    {
        return fileOffset + 8;
    }

    /**
     * Validates the chunk is ancillary.
     *
     * @return true if the chunk is ancillary, otherwise, it is false
     */
    public boolean isAncillary()
    {
        return ancillaryBit;
    }

    /**
     * Validates the chunk is private.
     *
     * @return true if the chunk is private, otherwise, it is false
     */
    public boolean isPrivate()
    {
        return privateBit;
    }

    /**
     * Validates the chunk is reserved.
     *
     * @return true if the chunk is reserved, otherwise, it is false
     */
    public boolean isReserved()
    {
        return reservedBit;
    }

    /**
     * Validates the chunk is safe to copy.
     *
     * @return true to indicate the chunk is safe to copy
     */
    public boolean isSafeToCopy()
    {
        return safeToCopyBit;
    }

    /**
     * Compares this chunk with another for full equality.
     *
     * @param obj
     *        the object to compare
     *
     * @return true if equal in all fields
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (!(obj instanceof PngChunk))
        {
            return false;
        }

        PngChunk other = (PngChunk) obj;

        return (length == other.length &&
                Arrays.equals(typeBytes, other.typeBytes) &&
                crc == other.crc &&
                Arrays.equals(payload, other.payload));
    }

    /**
     * Computes a hash code consistent with {@link #equals}.
     *
     * @return hash code for this chunk
     */
    @Override
    public int hashCode()
    {
        int result = Objects.hash(length, crc);

        result = 31 * result + Arrays.hashCode(typeBytes);
        result = 31 * result + Arrays.hashCode(payload);

        return result;
    }

    /**
     * Returns a string representation of the chunk's properties and contents. If the chunk only has
     * binary data and it is non-textual, it will indicate the size for context only.
     *
     * @return a formatted string describing this chunk
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format(MetadataConstants.FORMATTER, "Chunk Type", getType()));
        sb.append(String.format(MetadataConstants.FORMATTER, "Chunk Byte Length", getLength()));
        sb.append(String.format(MetadataConstants.FORMATTER, "CRC Value", getCrc()));

        if (!getType().isTextual())
        {
            sb.append(String.format(MetadataConstants.FORMATTER, "Binary Data Size", payload.length));
        }

        return sb.toString();
    }

    /**
     * Extracts the 5th-bit flags from each byte of the chunk type name. Used to determine
     * ancillary/private/reserved/safe-to-copy properties.
     *
     * In a nutshell, it examines Bit 5 to determine whether the corresponding bit is upper-case or
     * lower-case. If Bit 5 is 0, it indicates an upper-case letter. If this bit is a one, it is
     * lower-case.
     *
     * @param value
     *        the integer representation of the 4-byte chunk type
     *
     * @return boolean array of flags, including ancillary, private, reserved and safeToCopy bits
     */
    private static boolean[] extractPropertyBits(int value)
    {
        boolean[] flags = new boolean[4];
        int shift = 24;
        int mask = 1 << 5; // equals to 0x20

        for (int i = 0; i < flags.length; i++)
        {
            // Ensures no sign extension
            int b = (value >>> shift) & 0xFF;

            flags[i] = (b & mask) != 0;
            shift -= 8;
        }

        return flags;
    }
}