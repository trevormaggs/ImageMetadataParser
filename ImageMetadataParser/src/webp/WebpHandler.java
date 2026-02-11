package webp;

import static webp.WebPChunkType.*;
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
import common.ByteValueConverter;
import common.ImageHandler;
import common.ImageRandomAccessReader;
import common.SequentialByteArrayReader;
import logger.LogFactory;

/**
 * Handles the sequential parsing of WebP RIFF containers.
 * 
 * <p>
 * This handler manages the top-level RIFF header, the WEBP signature, and subsequent data chunks
 * such as VP8, EXIF, and XMP. It ensures data integrity by validating signatures and enforcing RIFF
 * padding rules.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.1
 * @since 13 August 2025
 */
public class WebpHandler implements ImageHandler, AutoCloseable
{
    /*
     * Note, CHUNK_HEADER_SIZE represents the size of a RIFF chunk header, encompassing 4 bytes for
     * FourCC and 4 bytes for payload length, counting the WEBP identifier.
     */
    private static final LogFactory LOGGER = LogFactory.getLogger(WebpHandler.class);
    private static final EnumSet<WebPChunkType> FIRST_CHUNK_TYPES = EnumSet.of(VP8, VP8L, VP8X);
    public static final ByteOrder WEBP_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
    private static final int CHUNK_HEADER_SIZE = 8;
    private final ByteStreamReader reader;
    private final List<WebpChunk> chunks = new ArrayList<>();
    private final Set<WebPChunkType> requiredChunks;
    private int extendedFormat;

    /**
     * Constructs a handler to parse selected chunks from a WebP image file.
     *
     * @param reader
     *        the {@link ByteStreamReader} for the WebP stream
     * @param requiredChunks
     *        optional set of chunk types to extract. If {@code null}, all encountered chunks are
     *        processed
     */
    public WebpHandler(ByteStreamReader reader, EnumSet<WebPChunkType> requiredChunks)
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
     * <p>
     * <strong>Note:</strong> Since this constructor opens the file, please use a try-with-resources
     * block or call {@link #close()} to release the file lock.
     * </p>
     *
     * @param fpath
     *        the filesystem path to the WebP file
     * @param requiredChunks
     *        optional set of chunk types to extract. If {@code null}, all encountered chunks are
     *        processed
     * 
     * @throws IOException
     *         if the file is inaccessible or cannot be opened
     */
    public WebpHandler(Path fpath, EnumSet<WebPChunkType> requiredChunks) throws IOException
    {
        this(new ImageRandomAccessReader(fpath, WEBP_BYTE_ORDER), requiredChunks);
    }

    /**
     * Closes the underlying reader and releases any file locks and memory resources.
     * 
     * <p>
     * This is called automatically when using a {@code try-with-resources} block.
     * </p>
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
     * 
     * @throws IOException
     *         if reading fails
     * @throws IllegalStateException
     *         if the header is corrupt or file is truncated
     */
    @Override
    public boolean parseMetadata() throws IOException
    {
        long totalChunkSize = readFileHeader(reader);

        if (getRealFileSize() > 0 && getRealFileSize() < totalChunkSize)
        {
            throw new IllegalStateException("WebP header size exceeds physical file length");
        }

        parseChunks(reader, totalChunkSize);

        return !chunks.isEmpty();
    }

    /**
     * Returns the length of the physical image file.
     *
     * @return the length in bytes, or 0L if the size cannot be determined
     */
    public long getRealFileSize()
    {
        return reader.getFilename().toFile().length();
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
     * Queries whether the Extended File Format (VP8X) chunk indicates the presence of XMP metadata.
     * 
     * @return {@code true} if VP8X flags and XMP chunk exist
     */
    public boolean existsXmpMetadata()
    {
        return (existsChunk(WebPChunkType.XMP) && (extendedFormat & 0x04) != 0);
    }

    /**
     * Queries whether the Extended File Format (VP8X) chunk indicates the presence of EXIF
     * metadata.
     * 
     * @return {@code true} if VP8X flags and EXIF chunk exist
     */
    public boolean existsExifMetadata()
    {
        return (existsChunk(WebPChunkType.EXIF) && (extendedFormat & 0x08) != 0);
    }

    /**
     * Retrieves a list of active chunks.
     * 
     * @return an unmodifiable view of all parsed {@link WebpChunk} objects
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
     * @return an {@link Optional} containing the first matching {@link WebpChunk}, or empty if not
     *         found
     */
    public Optional<WebpChunk> getFirstChunk(WebPChunkType type)
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
     * Finds the last occurrence of a chunk by type.
     * 
     * <p>
     * This is useful for metadata chunks, such as {@code 'XMP '} where, in some implementation
     * scenarios, a secondary chunk might be appended to override or update previous data.
     * </p>
     * 
     * @param type
     *        the {@link WebPChunkType} to find
     * @return an {@link Optional} containing the last matching {@link WebpChunk}, or empty if not
     *         found
     */
    public Optional<WebpChunk> getLastChunk(WebPChunkType type)
    {
        for (int i = chunks.size() - 1; i >= 0; i--)
        {
            WebpChunk chunk = chunks.get(i);

            if (chunk.getType() == type)
            {
                return Optional.of(chunk);
            }
        }

        return Optional.empty();
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

    /**
     * Reads and validates the RIFF/WEBP header and determines the exact container size.
     *
     * @param reader
     *        the stream reader
     * @return the total file size reported by the RIFF header
     * 
     * @throws IOException
     *         if an I/O reading error occurs
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

        // The RIFF size field is the size of the data following the first 8 bytes
        long riffDataSize = reader.readUnsignedInteger();
        long totalChunkSize = riffDataSize + CHUNK_HEADER_SIZE;

        // Minimum valid WebP is 12 bytes (RIFF 8 bytes + WEBP 4 bytes)
        if (totalChunkSize < 12)
        {
            throw new IllegalStateException("Invalid WebP header. Size [" + totalChunkSize + "] is too small");
        }

        type = reader.readBytes(4);

        if (!Arrays.equals(WEBP.getChunkName().getBytes(StandardCharsets.US_ASCII), type))
        {
            throw new IllegalStateException("Signature [WEBP] not found. Found [" + ByteValueConverter.toHex(type) + "]. Not a valid WebP file");
        }

        return totalChunkSize;
    }

    /**
     * Iterates through RIFF chunks sequentially and parses bitstream headers.
     *
     * @param reader
     *        the reader positioned after the WEBP signature
     * @param totalChunkSize
     *        the total expected size of the container in bytes
     * @throws IOException
     *         if an I/O error occurs during parsing
     */
    private void parseChunks(ByteStreamReader reader, long totalChunkSize) throws IOException
    {
        chunks.clear();
        boolean firstChunk = true;

        while (reader.getCurrentPosition() + CHUNK_HEADER_SIZE <= totalChunkSize)
        {
            int fourCC = reader.readInteger();
            long payloadLength = reader.readUnsignedInteger();
            WebPChunkType chunkType = WebPChunkType.findType(fourCC);

            if (payloadLength < 0 || (reader.getCurrentPosition() + payloadLength > totalChunkSize))
            {
                LOGGER.error("Malformed chunk [" + WebPChunkType.getChunkName(fourCC) + "]");
                break;
            }

            if (firstChunk && !FIRST_CHUNK_TYPES.contains(chunkType))
            {
                throw new IllegalStateException("Invalid first chunk found [" + WebPChunkType.getChunkName(fourCC) + "]. It must be either VP8, VP8L or VP8X");
            }

            if (requiredChunks == null || requiredChunks.contains(chunkType))
            {
                long currentDataOffset = reader.getCurrentPosition();
                byte[] data = reader.readBytes((int) payloadLength);

                if (chunkType == WebPChunkType.VP8X)
                {
                    parseVP8X(data);
                }

                else if (chunkType == WebPChunkType.VP8)
                {
                    parseVP8(data);
                }

                else if (chunkType == WebPChunkType.VP8L)
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
            if (payloadLength % 2 != 0 && reader.getCurrentPosition() < totalChunkSize)
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
     *        the resolved {@link WebPChunkType} value
     * @param fourCC
     *        the 32-bit FourCC identifier
     * @param length
     *        payload length in bytes
     * @param data
     *        the raw payload bytes
     * @param dataOffset
     *        the absolute physical position where the payload starts
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
     * Parses the VP8X extended header to determine image features and dimensions. At this stage, it
     * is used for logging purposes only.
     *
     * @param payload
     *        the 10-byte payload from the VP8X chunk
     * 
     * @see <a href=
     *      "https://developers.google.com/speed/webp/docs/riff_container#extended_file_format">More
     *      VP8X details</a>
     */
    private void parseVP8X(byte[] payload)
    {
        try (SequentialByteArrayReader subReader = new SequentialByteArrayReader(payload, WEBP_BYTE_ORDER))
        {
            extendedFormat = subReader.readUnsignedByte();

            boolean hasAnimation = (extendedFormat & 0x02) != 0;
            boolean hasXMP = (extendedFormat & 0x04) != 0;
            boolean hasEXIF = (extendedFormat & 0x08) != 0;
            boolean hasAlpha = (extendedFormat & 0x10) != 0;
            boolean hasICC = (extendedFormat & 0x20) != 0;

            // Skip 24-bit reserved block
            subReader.skip(3);

            /*
             * Width and Height are 24-bit integers stored as (value - 1)
             * See https://developers.google.com/speed/webp/docs/riff_container#extended_file_format
             * for explanations.
             */
            int canvasWidth = subReader.readUnsignedInt24() + 1;
            int canvasHeight = subReader.readUnsignedInt24() + 1;

            StringBuilder sb = new StringBuilder();

            sb.append(String.format("Chunk VP8X detected. CanvasWidth x CanvasHeight: %dx%d, ", canvasWidth, canvasHeight));
            sb.append(String.format("EXIF: %b, XMP: %b, Alpha: %b, Anim %b, ICCP: %b", hasEXIF, hasXMP, hasAlpha, hasAnimation, hasICC));

            LOGGER.debug(sb.toString());
        }
    }

    /**
     * Parses the VP8 Lossy bitstream header to extract image dimensions.
     * 
     * <p>
     * The dimensions are stored as 16-bit values where the 2 most significant bits are reserved for
     * scaling, requiring a bitmask of {@code 0x3FFF}.
     * </p>
     * 
     * @param payload
     *        the raw bytes from the {@code VP8} chunk
     */
    private void parseVP8(byte[] payload)
    {
        if (payload.length < 10)
        {
            return;
        }

        try (SequentialByteArrayReader subReader = new SequentialByteArrayReader(payload, WEBP_BYTE_ORDER))
        {
            // Skip the 3-byte Frame Tag
            subReader.skip(3);

            // Verify Sync Code: 9D 01 2A
            byte[] syncCode = subReader.readBytes(3);

            if (syncCode[0] == (byte) 0x9D && syncCode[1] == (byte) 0x01 && syncCode[2] == (byte) 0x2A)
            {
                // Dimensions are 16-bit (14 bits used for size)
                int width = subReader.readUnsignedShort() & 0x3FFF;
                int height = subReader.readUnsignedShort() & 0x3FFF;

                LOGGER.debug(String.format("VP8 Lossy Bitstream [%dx%d]", width, height));
            }
        }
    }

    /**
     * Parses the VP8L Lossless bitstream header to extract image dimensions.
     * 
     * <p>
     * Dimensions are stored as 14-bit integers. The bit-stream layout is: 1 bit (signature), 14
     * bits (width-1), 14 bits (height-1).
     * </p>
     * 
     * @param payload
     *        the raw bytes from the 'VP8L' chunk
     */
    private void parseVP8L(byte[] payload)
    {
        if (payload.length < 5)
        {
            return;
        }

        try (SequentialByteArrayReader subReader = new SequentialByteArrayReader(payload, WEBP_BYTE_ORDER))
        {
            int signature = subReader.readUnsignedByte();

            if (signature == 0x2F)
            {
                // Read 4 bytes to extract the packed 14-bit values
                int data = subReader.readInteger();

                // Width: bits 0-13
                int width = (data & 0x3FFF) + 1;
                // Height: bits 14-27
                int height = ((data >> 14) & 0x3FFF) + 1;

                LOGGER.debug(String.format("VP8L Lossless Bitstream: [%dx%d]", width, height));
            }
        }
    }
}