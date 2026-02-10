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
import common.ByteValueConverter;
import common.ImageHandler;
import common.ImageRandomAccessReader;
import common.SequentialByteArrayReader;
import logger.LogFactory;

/**
 * WebP files are based on the Resource Interchange File Format (RIFF) container. This handler
 * manages the sequential parsing of the top-level RIFF header, the WEBP sub-header, and the
 * subsequent data chunks such as VP8, EXIF, and XMP.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class WebpHandler implements ImageHandler, AutoCloseable
{
    /*
     * Note, CHUNK_HEADER_SIZE represents the size of a RIFF chunk header
     * (4 bytes for FourCC + 4 bytes for payload length, counting the WEBP identifier).
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
     *        byte reader for raw WebP stream
     * @param requiredChunks
     *        an optional set of chunk types to be extracted (null means all chunks are selected)
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

            // Ensure core dimension chunks are always processed
            chunkset.add(WebPChunkType.VP8X);
            chunkset.add(WebPChunkType.VP8);
            chunkset.add(WebPChunkType.VP8L);

            this.requiredChunks = Collections.unmodifiableSet(chunkset);
        }
    }

    /**
     * Constructs a handler for the specified WebP file.
     * 
     * <p>
     * <strong>Note:</strong> Since this constructor opens the file, please use a try-with-resources
     * block or call {@link #close()} to release the file lock.
     * </p>
     * 
     * @param fpath
     *        path to the WebP file
     * @param requiredChunks
     *        the set of {@link WebPChunkType}s to load into memory. If {@code null}, all
     *        encountered chunks are extracted
     * 
     * @throws IOException
     *         if the file cannot be accessed
     */
    public WebpHandler(Path fpath, EnumSet<WebPChunkType> requiredChunks) throws IOException
    {
        this(new ImageRandomAccessReader(fpath, WEBP_BYTE_ORDER), requiredChunks);
    }

    /**
     * Releases the file handle and closes the underlying {@code ByteStreamReader} resource.
     *
     * <p>
     * This is called automatically when using a {@code try-with-resources} block. Closing this
     * handler ensures that any system locks on the file are released and memory resources are
     * freed.
     * </p>
     *
     * @throws IOException
     *         if an I/O error occurs while closing the reader
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
     * Parses the WebP file and extracts selected chunk data.
     * 
     * @return true if chunks were successfully extracted
     * 
     * @throws IOException
     *         if reading fails
     * @throws IllegalStateException
     *         if the header is corrupt or file is truncated
     */
    @Override
    public boolean parseMetadata() throws IOException
    {
        long totalReportedSize = readFileHeader(reader);

        if (totalReportedSize <= 0)
        {
            throw new IllegalStateException("Invalid WebP header: reported size is 0");
        }

        if (getRealFileSize() > 0 && getRealFileSize() < totalReportedSize)
        {
            throw new IllegalStateException("WebP header size exceeds physical file length");
        }

        parseChunks(reader, totalReportedSize);

        return !chunks.isEmpty();
    }

    /**
     * Returns a textual representation of all parsed WebP chunks in this file.
     *
     * @return formatted string of all parsed chunk entries
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        for (WebpChunk chunk : chunks)
        {
            sb.append(chunk).append(System.lineSeparator());
        }

        return sb.toString();
    }

    /**
     * Returns the length of the physical image file.
     *
     * @return the length of the file in bytes, or 0L if the size cannot be determined
     */
    public long getRealFileSize()
    {
        return reader.getFilename().toFile().length();
    }

    /**
     * Retrieves a list of active chunks.
     *
     * @return an unmodified list of chunks
     */
    public List<WebpChunk> getChunks()
    {
        return Collections.unmodifiableList(chunks);
    }

    /**
     * Finds and returns the first occurrence of a chunk matching the specified type.
     * 
     * <p>
     * This is the standard method for retrieving non-repeatable chunks such as {@code VP8X},
     * {@code VP8}, or {@code ICCP}.
     * </p>
     *
     * @param type
     *        the {@link WebPChunkType} to search for
     * @return an {@link Optional} containing the first matching {@link WebpChunk} otherwise
     *         {@link Optional#empty()}
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
     * Finds and returns the last occurrence of a chunk matching the specified type.
     * 
     * <p>
     * This is useful for metadata chunks (like {@code XMP }) where, in some implementation
     * scenarios, a secondary chunk might be appended to override or update previous data.
     * </p>
     *
     * @param type
     *        the {@link WebPChunkType} to search for
     * @return an {@link Optional} containing the last matching {@link WebpChunk} otherwise
     *         {@link Optional#empty()}
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
     * Checks if a chunk with the specified type has already been set.
     *
     * @param type
     *        the type of the chunk
     *
     * @return true if the chunk is already present
     */
    public boolean existsChunk(WebPChunkType type)
    {
        return chunks.stream().anyMatch(chunk -> chunk.getType() == type);
    }

    /**
     * Identifies if the Extended File Format (VP8X) chunk indicates the presence of XMP metadata.
     * 
     * @return true if the 3rd bit (0x04) of the VP8X flags is set
     */
    public boolean existsXmpMetadata()
    {
        return (existsChunk(WebPChunkType.XMP) && (extendedFormat & 0x04) != 0);
    }

    /**
     * Identifies if the Extended File Format (VP8X) chunk indicates the presence of EXIF metadata.
     * 
     * @return true if the 4th bit (0x08) of the VP8X flags is set
     */
    public boolean existsExifMetadata()
    {
        return (existsChunk(WebPChunkType.EXIF) && (extendedFormat & 0x08) != 0);
    }

    /**
     * Read the file header of the given WebP file. Basically, it checks for correct RIFF and WEBP
     * signature entries within the first few stream bytes. It also determines the full size of this
     * file.
     *
     * @param reader
     *        byte reader for raw WebP stream
     * @return the size of the WebP file
     *
     * @throws IOException
     *         if an I/O error occurs
     * @throws IllegalStateException
     *         if the WebP header information is corrupted
     */
    private long readFileHeader(ByteStreamReader reader) throws IOException
    {
        byte[] type = reader.readBytes(4);

        if (!Arrays.equals(RIFF.getChunkName().getBytes(StandardCharsets.US_ASCII), type))
        {
            throw new IllegalStateException("Header [RIFF] not found. Found [" + ByteValueConverter.toHex(type) + "]. Not a valid WEBP format");
        }

        // The RIFF size field is the size of the data following the first 8 bytes
        long riffDataSize = reader.readUnsignedInteger();
        long totalReportedSize = riffDataSize + CHUNK_HEADER_SIZE;

        /*
         * The RIFF file size field is a 32-bit integer, and the maximum file size
         * supported by this logic is 2^{32} - 1 bytes.
         */
        if (totalReportedSize < 0)
        {
            throw new IllegalStateException("WebP header contains an invalid negative size");
        }

        type = reader.readBytes(4);

        if (!Arrays.equals(WEBP.getChunkName().getBytes(StandardCharsets.US_ASCII), type))
        {
            throw new IllegalStateException("Signature [WEBP] not found. Found [" + ByteValueConverter.toHex(type) + "]. Not a valid WebP file");
        }

        return totalReportedSize;
    }

    /**
     * Parses RIFF chunks sequentially from the stream.
     * 
     * <p>
     * Enforces the requirement that a bitstream chunk (VP8, VP8L, or VP8X) appears first and
     * handles 1-byte alignment padding for odd-length payloads.
     * </p>
     * 
     * @param reader
     *        the reader positioned at the first chunk
     * @param riffFileSize
     *        the total expected RIFF container size
     * 
     * @throws IOException
     *         if reading fails
     * @throws IllegalStateException
     *         if the first chunk is invalid or structure is malformed
     */
    private void parseChunks(ByteStreamReader reader, long riffFileSize) throws IOException
    {
        chunks.clear();
        boolean firstChunk = true;

        while (reader.getCurrentPosition() + CHUNK_HEADER_SIZE <= riffFileSize)
        {
            int fourCC = reader.readInteger();
            long payloadLength = reader.readUnsignedInteger();
            WebPChunkType chunkType = WebPChunkType.findType(fourCC);

            if (payloadLength < 0 || (reader.getCurrentPosition() + payloadLength > riffFileSize))
            {
                LOGGER.error("Malformed chunk [" + WebPChunkType.getChunkName(fourCC) + "] at " + reader.getCurrentPosition());
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

            // Handle RIFF 1-byte alignment if odd length
            if (payloadLength % 2 != 0 && reader.getCurrentPosition() < riffFileSize)
            {
                reader.skip(1);
            }

            firstChunk = false;
        }
    }

    /**
     * Adds a parsed chunk to the internal chunk collection.
     *
     * @param type
     *        the WebP chunk type in enum constant
     * @param fourCC
     *        the 32-bit FourCC chunk identifier (in little-endian integer form)
     * @param length
     *        the length of the chunk's payload
     * @param data
     *        raw chunk data
     * @param dataOffset
     *        the absolute physical position in the file where the chunk begins
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