package webp;

import static webp.WebPChunkType.RIFF;
import static webp.WebPChunkType.VP8;
import static webp.WebPChunkType.VP8L;
import static webp.WebPChunkType.VP8X;
import static webp.WebPChunkType.WEBP;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import common.ByteStreamReader;
import common.ImageHandler;
import common.ImageRandomAccessReader;
import common.SequentialByteArrayReader;
import logger.LogFactory;

/**
 * Handles the sequential parsing of WebP RIFF containers.
 * <p>
 * This handler manages the top-level RIFF header, the WEBP signature, and subsequent
 * data chunks such as VP8, EXIF, and XMP. It ensures data integrity by validating
 * signatures and enforcing RIFF padding rules.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.1
 * @since 13 August 2025
 */
public class WebpHandler2 implements ImageHandler, AutoCloseable
{
    private static final LogFactory LOGGER = LogFactory.getLogger(WebpHandler2.class);
    private static final EnumSet<WebPChunkType> FIRST_CHUNK_TYPES = EnumSet.of(VP8, VP8L, VP8X);

    /** The byte order used by the WebP format (Little Endian). */
    public static final ByteOrder WEBP_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    /** Size of a RIFF chunk header: 4 bytes for FourCC + 4 bytes for length. */
    private static final int CHUNK_HEADER_SIZE = 8;

    private final ByteStreamReader reader;
    private final List<WebpChunk> chunks = new ArrayList<>();
    private final Set<WebPChunkType> requiredChunks;
    private int extendedFormat;

    /**
     * Constructs a handler using a provided stream reader.
     *
     * @param reader
     *        the {@link ByteStreamReader} for the WebP stream
     * @param requiredChunks
     *        optional set of chunk types to extract. If {@code null},
     *        all encountered chunks are processed.
     */
    public WebpHandler2(ByteStreamReader reader, EnumSet<WebPChunkType> requiredChunks)
    {
        this.reader = reader;

        if (requiredChunks == null)
        {
            this.requiredChunks = null;
        }
        else
        {
            EnumSet<WebPChunkType> chunkset = requiredChunks.clone();
            // Core chunks required for dimension parsing are always included
            chunkset.add(VP8X);
            chunkset.add(VP8);
            chunkset.add(VP8L);
            this.requiredChunks = Collections.unmodifiableSet(chunkset);
        }
    }

    /**
     * Constructs a handler for the specified WebP file path.
     *
     * @param fpath
     *        the filesystem path to the WebP file
     * @param requiredChunks
     *        chunk types to extract (null for all)
     * @throws IOException
     *         if the file is inaccessible or cannot be opened
     */
    public WebpHandler2(Path fpath, EnumSet<WebPChunkType> requiredChunks) throws IOException
    {
        this(new ImageRandomAccessReader(fpath, WEBP_BYTE_ORDER), requiredChunks);
    }

    /**
     * Closes the underlying reader and releases any file locks.
     *
     * @throws IOException
     *         if an error occurs during closure
     */
    @Override
    public void close() throws IOException
    {
        if (reader != null)
        {
            reader.close();
        }
    }

    /**
     * Parses the WebP file and extracts selected chunks into memory.
     *
     * @return {@code true} if chunks were successfully extracted
     * @throws IOException
     *         if reading fails
     * @throws IllegalStateException
     *         if the header is corrupt or file is truncated
     */
    @Override
    public boolean parseMetadata() throws IOException
    {
        long totalReportedSize = readFileHeader(reader);

        if (getRealFileSize() > 0 && getRealFileSize() < totalReportedSize)
        {
            throw new IllegalStateException("WebP header size exceeds physical file length");
        }

        parseChunks(reader, totalReportedSize);

        return !chunks.isEmpty();
    }

    /**
     * Validates the RIFF/WEBP header and determines container size.
     *
     * @param reader
     *        the stream reader
     * @return the total file size reported by the RIFF header
     * @throws IOException
     *         if reading fails
     * @throws IllegalStateException
     *         if signatures are missing or size is mathematically invalid
     */
    private long readFileHeader(ByteStreamReader reader) throws IOException
    {
        byte[] type = reader.readBytes(4);

        if (!Arrays.equals(RIFF.getChunkName().getBytes(StandardCharsets.US_ASCII), type))
        {
            throw new IllegalStateException("Header [RIFF] not found. Not a valid WebP file");
        }

        long riffDataSize = reader.readUnsignedInteger();
        long totalReportedSize = riffDataSize + CHUNK_HEADER_SIZE;

        // Minimum valid WebP is 12 bytes (RIFF 8 bytes + WEBP 4 bytes)
        if (totalReportedSize < 12)
        {
            throw new IllegalStateException("Invalid WebP header: size [" + totalReportedSize + "] is too small");
        }

        type = reader.readBytes(4);

        if (!Arrays.equals(WEBP.getChunkName().getBytes(StandardCharsets.US_ASCII), type))
        {
            throw new IllegalStateException("Signature [WEBP] not found. Not a valid WebP file");
        }

        return totalReportedSize;
    }

    /**
     * Iterates through RIFF chunks sequentially and parses bitstream headers.
     *
     * @param reader
     *        the reader positioned after the WEBP signature
     * @param totalReportedSize
     *        the total expected size of the container in bytes
     * @throws IOException
     *         if an I/O error occurs during parsing
     */
    private void parseChunks(ByteStreamReader reader, long totalReportedSize) throws IOException
    {
        chunks.clear();
        boolean firstChunk = true;

        // Use <= to ensure we can read the final 8-byte header at the boundary
        while (reader.getCurrentPosition() + CHUNK_HEADER_SIZE <= totalReportedSize)
        {
            int fourCC = reader.readInteger();
            long payloadLength = reader.readUnsignedInteger();
            WebPChunkType chunkType = WebPChunkType.findType(fourCC);

            if (payloadLength < 0 || (reader.getCurrentPosition() + payloadLength > totalReportedSize))
            {
                LOGGER.error("Malformed chunk [" + WebPChunkType.getChunkName(fourCC) + "] at " + reader.getCurrentPosition());
                break;
            }

            if (firstChunk && !FIRST_CHUNK_TYPES.contains(chunkType))
            {
                throw new IllegalStateException("Invalid first chunk: must be VP8, VP8L or VP8X");
            }

            if (requiredChunks == null || requiredChunks.contains(chunkType))
            {
                long currentDataOffset = reader.getCurrentPosition();
                byte[] data = reader.readBytes((int) payloadLength);

                if (chunkType == VP8X)
                {
                    parseVP8X(data);
                }
                else if (chunkType == VP8)
                {
                    parseVP8(data);
                }
                else if (chunkType == VP8L)
                {
                    parseVP8L(data);
                }

                addChunk(chunkType, fourCC, (int) payloadLength, data, currentDataOffset);
            }
            else
            {
                reader.skip(payloadLength);
            }

            // RIFF 1-byte alignment padding for odd lengths
            if (payloadLength % 2 != 0 && reader.getCurrentPosition() < totalReportedSize)
            {
                reader.skip(1);
            }

            firstChunk = false;
        }
    }

    /**
     * Adds a chunk to the list, preventing duplicates for unique chunk types.
     *
     * @param type
     *        the resolved {@link WebPChunkType}
     * @param fourCC
     *        the 32-bit FourCC identifier
     * @param length
     *        payload length in bytes
     * @param data
     *        the raw payload bytes
     * @param dataOffset
     *        physical file offset for the start of the payload
     */
    private void addChunk(WebPChunkType type, int fourCC, int length, byte[] data, long dataOffset)
    {
        if (!type.isMultipleAllowed() && existsChunk(type))
        {
            LOGGER.warn("Duplicate chunk detected [" + type + "]");
            return;
        }
        chunks.add(new WebpChunk(fourCC, length, data, dataOffset));
    }

    /**
     * @return the physical length of the image file in bytes
     */
    public long getRealFileSize()
    {
        return reader.getFilename().toFile().length();
    }

    /**
     * @return an unmodifiable view of all parsed {@link WebpChunk}s
     */
    public List<WebpChunk> getChunks()
    {
        return Collections.unmodifiableList(chunks);
    }

    /**
     * Finds the first occurrence of a chunk by type.
     *
     * @param type
     *        the {@link WebPChunkType} to find
     * @return an {@link Optional} containing the chunk, or empty if not found
     */
    public Optional<WebpChunk> getChunk(WebPChunkType type)
    {
        for (WebpChunk chunk : chunks)
        {
            if (chunk.getType() == type)
            {
                return Optional.of(chunk);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks for the existence of a specific chunk type in the parsed list.
     *
     * @param type
     *        the type to check
     * @return {@code true} if present
     */
    public boolean existsChunk(WebPChunkType type)
    {
        for (WebpChunk chunk : chunks)
        {
            if (chunk.getType() == type)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code true} if VP8X flags and XMP chunk exist
     */
    public boolean existsXmpMetadata()
    {
        return (existsChunk(WebPChunkType.XMP) && (extendedFormat & 0x04) != 0);
    }

    /**
     * @return {@code true} if VP8X flags and EXIF chunk exist
     */
    public boolean existsExifMetadata()
    {
        return (existsChunk(WebPChunkType.EXIF) && (extendedFormat & 0x08) != 0);
    }

    /**
     * Parses the VP8X header for canvas dimensions and feature flags.
     *
     * @param payload
     *        the raw VP8X payload
     */
    private void parseVP8X(byte[] payload)
    {
        try (SequentialByteArrayReader subReader = new SequentialByteArrayReader(payload, WEBP_BYTE_ORDER))
        {
            extendedFormat = subReader.readUnsignedByte();
            subReader.skip(3);
            int width = subReader.readUnsignedInt24() + 1;
            int height = subReader.readUnsignedInt24() + 1;
            LOGGER.debug(String.format("VP8X Canvas: %dx%d", width, height));
        }
    }

    /**
     * Parses the VP8 lossy bitstream header for dimensions.
     *
     * @param payload
     *        the raw VP8 payload
     */
    private void parseVP8(byte[] payload)
    {
        if (payload.length < 10)
        {
            return;
        }
        try (SequentialByteArrayReader subReader = new SequentialByteArrayReader(payload, WEBP_BYTE_ORDER))
        {
            subReader.skip(3);
            byte[] syncCode = subReader.readBytes(3);
            if (syncCode[0] == (byte) 0x9D && syncCode[1] == (byte) 0x01 && syncCode[2] == (byte) 0x2A)
            {
                int width = subReader.readUnsignedShort() & 0x3FFF;
                int height = subReader.readUnsignedShort() & 0x3FFF;
                LOGGER.debug(String.format("VP8 Lossy: %dx%d", width, height));
            }
        }
    }

    /**
     * Parses the VP8L lossless bitstream header for dimensions.
     *
     * @param payload
     *        the raw VP8L payload
     */
    private void parseVP8L(byte[] payload)
    {
        if (payload.length < 5)
        {
            return;
        }
        
        try (SequentialByteArrayReader subReader = new SequentialByteArrayReader(payload, WEBP_BYTE_ORDER))
        {
            if (subReader.readUnsignedByte() == 0x2F)
            {
                int data = subReader.readInteger();
                int width = (data & 0x3FFF) + 1;
                int height = ((data >> 14) & 0x3FFF) + 1;
                LOGGER.debug(String.format("VP8L Lossless: %dx%d", width, height));
            }
        }
    }

    /**
     * Returns a string summary of all parsed chunks.
     *
     * @return a newline-delimited string of chunk descriptions
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (WebpChunk chunk : chunks)
        {
            sb.append(chunk.toString()).append(System.lineSeparator());
        }
        return sb.toString();
    }
}