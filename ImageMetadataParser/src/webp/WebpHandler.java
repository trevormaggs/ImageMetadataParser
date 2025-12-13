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
import jpg.JpgParser;
import logger.LogFactory;

/**
 * WebP files are based on the Resource Interchange File Format (RIFF) container. This handler
 * manages the sequential parsing of the top-level RIFF header, the {@code WEBP} sub-header, and the
 * subsequent data 'Chunks' like VP8, EXIF, and XMP.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class WebpHandler implements ImageHandler
{
    /*
     * Note, CHUNK_HEADER_SIZE represents the size of a RIFF chunk header
     * (4 bytes for FourCC + 4 bytes for payload length, counting the WEBP identifier).
     */
    private static final LogFactory LOGGER = LogFactory.getLogger(WebpHandler.class);
    private static final EnumSet<WebPChunkType> FIRST_CHUNK_TYPES = EnumSet.of(VP8, VP8L, VP8X);
    private static final ByteOrder WEBP_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
    private static final int CHUNK_HEADER_SIZE = 8;
    private final Path imageFile;
    private final List<WebpChunk> chunks;
    private final Set<WebPChunkType> requiredChunks;

    /**
     * Constructs a handler to parse selected chunks from a WebP image file.
     *
     * @param fpath
     *        the path to the WebP file for logging purposes
     * @param requiredChunks
     *        an optional set of chunk types to be extracted (null means all chunks are selected)
     */

    public WebpHandler(Path fpath, EnumSet<WebPChunkType> requiredChunks)
    {
        this.imageFile = fpath;
        this.chunks = new ArrayList<>();

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
     * Retrieves a list of chunks that have been extracted.
     *
     * @return an unmodified list of chunks
     */
    public List<WebpChunk> getChunks()
    {
        return Collections.unmodifiableList(chunks);
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
     * Retrieves the embedded EXIF data from the WebP file, if present.
     *
     * <p>
     * The EXIF metadata is stored in the {@code EXIF} chunk as raw TIFF-formatted data. If the WebP
     * file contains an {@code EXIF} chunk, its byte array is returned wrapped in {@link Optional}.
     * If no {@code EXIF} chunk exists, {@link Optional#empty()} is returned.
     * </p>
     *
     * @return an {@link Optional} containing the EXIF data as a byte array if found, or
     *         {@link Optional#empty()} if absent
     */
    public Optional<byte[]> getRawExifPayload()
    {
        for (WebpChunk chunk : chunks)
        {
            if (chunk.getType() == WebPChunkType.EXIF)
            {
                byte[] data = chunk.getPayloadArray();

                /*
                 * According to research from other sources, it seems sometimes the WebP files
                 * happen to contain the JPG premable within the TIFF header block for some strange
                 * reasons, the snippet below makes sure the JPEG segment is skipped.
                 */
                data = JpgParser.stripExifPreamble(data);

                return Optional.of(data);
            }
        }

        return Optional.empty();
    }

    /**
     * If a packet of XMP properties embedded within the WebPChunkType.XMP chunk is present, it is
     * read into an array of raw bytes.
     *
     * Note, it iterates in reverse direction, applying the <b>last-one-wins</b> strategy, which is
     * common for metadata.
     *
     * @return an {@link Optional} containing the XMP payload as an array of raw bytes, or
     *         {@link Optional#empty()} if no such data is found
     */
    public Optional<byte[]> getRawXmpPayload()
    {
        for (int i = chunks.size() - 1; i >= 0; i--)
        {
            WebpChunk chunk = chunks.get(i);

            if (chunk.getType() == WebPChunkType.XMP)
            {
                return Optional.of(chunk.getPayloadArray());
            }
        }

        return Optional.empty();
    }

    /**
     * Returns the length of the physical image file. This is safe since it only returns zero if the
     * size is missing or inaccessible.
     *
     * @return the length of the file in bytes, potentially or 0L if the size cannot be determined
     */
    public long getRealFileSize()
    {
        return imageFile.toFile().length();
    }

    /**
     * Begins metadata processing by parsing the WebP file and extracting chunk data.
     *
     * @return true if at least one chunk element was successfully extracted, or false if no
     *         relevant data was processed
     *
     * @throws IOException
     *         if an I/O error occurs
     * @throws IllegalStateException
     *         if the file structure is malformed (i.e. header corruption, size mismatch) or
     *         contains an invalid first chunk
     */
    @Override
    public boolean parseMetadata() throws IOException
    {
        try (ByteStreamReader reader = new ImageRandomAccessReader(imageFile, WEBP_BYTE_ORDER))
        {
            long fileSize = readFileHeader(reader);

            if (fileSize == 0)
            {
                LOGGER.warn("No chunks extracted from WebP file [" + imageFile + "]");
            }

            else if (getRealFileSize() < fileSize)
            {
                throw new IllegalStateException("Discovered file size exceeds actual file length");
            }

            parseChunks(reader, fileSize);
        }

        return (!chunks.isEmpty());
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

        long riffContentSize = reader.readUnsignedInteger();
        long fileSize = riffContentSize + CHUNK_HEADER_SIZE;

        if (getRealFileSize() > 0 && fileSize > getRealFileSize())
        {
            throw new IllegalStateException("WebP header states [" + fileSize + "] bytes, but is too large to fit in file [" + getRealFileSize() + "]");
        }

        /*
         * The RIFF file size field is a 32-bit integer, and the maximum file size
         * supported by this logic is 2^{32} - 1 bytes, although the check fileSize < 0 only
         * handles values greater than or equal to 2^{31}.
         */
        if (fileSize < 0)
        {
            throw new IllegalStateException("WebP header contains a negative size. Found [" + fileSize + "] bytes");
        }

        type = reader.readBytes(4);

        if (!Arrays.equals(WEBP.getChunkName().getBytes(StandardCharsets.US_ASCII), type))
        {
            throw new IllegalStateException("Chunk type [WEBP] not found. Found [" + ByteValueConverter.toHex(type) + "]. Not a valid WEBP format");
        }

        return fileSize;
    }

    /**
     * Processes the WebP data stream and extracts matching chunk types into memory.
     *
     * @param riffFileSize
     *        the size of the WebP file, including the RIFF header
     * @param reader
     *        a ByteStreamReader object to read binary information in the stream
     *
     * @throws IOException
     *         if an I/O error occurs
     * @throws IllegalStateException
     *         if there is a malformed file
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
                byte[] data = reader.readBytes((int) payloadLength);

                if (chunkType == WebPChunkType.VP8X)
                {
                    parseVP8X(data);
                }

                /*
                 * To be incorporated later.
                 * 
                 * else if (chunkType == WebPChunkType.VP8) parseVP8(data);
                 * else if (chunkType == WebPChunkType.VP8L) parseVP8L(data);
                 */
                addChunk(chunkType, fourCC, (int) payloadLength, data);
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
     * Parses the VP8X extended header to determine image features and dimensions. At this stage, it
     * is used for logging purposes only.
     *
     * @param payload
     *        the 10-byte payload from the VP8X chunk
     *
     * @throws IOException
     *         if an I/O error occurs
     * @see <a href=
     *      "https://developers.google.com/speed/webp/docs/riff_container#extended_file_format">More
     *      VP8X details</a>
     */
    private void parseVP8X(byte[] payload) throws IOException
    {
        try (SequentialByteArrayReader subReader = new SequentialByteArrayReader(payload, WEBP_BYTE_ORDER))
        {
            int flags = subReader.readUnsignedByte();

            boolean hasAnimation = (flags & 0x02) != 0;
            boolean hasXMP = (flags & 0x04) != 0;
            boolean hasEXIF = (flags & 0x08) != 0;
            boolean hasAlpha = (flags & 0x10) != 0;
            boolean hasICC = (flags & 0x20) != 0;

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
     */
    private void addChunk(WebPChunkType type, int fourCC, int length, byte[] data)
    {
        if (!type.isMultipleAllowed() && existsChunk(type))
        {
            // throw new IllegalStateException("Duplicate [" + chunkType + "] found in file [" +
            // imageFile + "]. This is disallowed");

            LOGGER.warn("Duplicate chunk detected [" + type + "]");
            return;
        }

        chunks.add(new WebpChunk(fourCC, length, data));
    }

    // TEST FIRST
    // VP8 (Lossy) Dimension Extraction
    @Deprecated
    protected void parseVP8(byte[] payload) throws IOException
    {
        if (payload.length < 10)
        {
            return;
        }

        try (SequentialByteArrayReader subReader = new SequentialByteArrayReader(payload, WEBP_BYTE_ORDER))
        {
            subReader.skip(3); // Skip Frame Tag (3 bytes)

            // Check for Sync Code: 9D 01 2A
            byte[] syncCode = subReader.readBytes(3);

            if (syncCode[0] == (byte) 0x9D && syncCode[1] == (byte) 0x01 && syncCode[2] == (byte) 0x2A)
            {
                // Width/Height are 16-bit, but only 14 bits are used
                int width = subReader.readUnsignedShort() & 0x3FFF;
                int height = subReader.readUnsignedShort() & 0x3FFF;
                LOGGER.debug("VP8 Lossy: " + width + "x" + height);
            }
        }
    }

    // VP8L (Lossless) Dimension Extraction
    @Deprecated
    protected void parseVP8L(byte[] payload) throws IOException
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
                // Read 4 bytes and use bit manipulation to extract 14-bit values
                int data = subReader.readInteger();
                int width = (data & 0x3FFF) + 1;
                int height = ((data >> 14) & 0x3FFF) + 1;

                LOGGER.debug("VP8L Lossless: " + width + "x" + height);
            }
        }
    }
}