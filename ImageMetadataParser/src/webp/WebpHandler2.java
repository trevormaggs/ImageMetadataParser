package webp;

import static webp.WebPChunkType.*;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import common.ByteStreamReader;
import common.ByteValueConverter;
import common.ImageHandler;
import common.ImageRandomAccessReader;
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
public class WebpHandler2 implements ImageHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(WebpHandler2.class);
    private static final EnumSet<WebPChunkType> FIRST_CHUNK_TYPES = EnumSet.of(VP8, VP8L, VP8X);
    private static final ByteOrder WEBP_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
    private final Path imageFile;
    private final List<WebpChunk> chunks;
    private final EnumSet<WebPChunkType> requiredChunks;

    /**
     * Constructs a handler to parse selected chunks from a WebP image file.
     *
     * @param fpath
     *        the path to the WebP file for logging purposes
     * @param reader
     *        byte reader for raw WebP stream
     * @param requiredChunks
     *        an optional set of chunk types to be extracted (null means all chunks are selected)
     */
    public WebpHandler2(Path fpath, EnumSet<WebPChunkType> requiredChunks)
    {
        this.imageFile = fpath;
        this.chunks = new ArrayList<>();
        this.requiredChunks = requiredChunks;
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
    public Optional<byte[]> getExifData()
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
     * Returns the length of the image file associated with the current InputStream resource.
     *
     * @return the length of the file in bytes, or 0 if the size cannot be determined
     */
    @Override
    public long getSafeFileSize()
    {
        try
        {
            return Files.size(imageFile);
        }

        catch (IOException exc)
        {
            return 0L;
        }
    }

    /**
     * Begins metadata processing by parsing the WebP file and extracting chunk data.
     *
     * @return true if at least one chunk element was successfully extracted, or false if no
     *         relevant data was processed
     *
     * @throws IllegalStateException
     *         if the file structure is malformed (i.e. header corruption, size mismatch) or
     *         contains an invalid first chunk
     */
    @Override
    public boolean parseMetadata()
    {
        try (ByteStreamReader reader = new ImageRandomAccessReader(imageFile, WEBP_BYTE_ORDER))
        {
            int fileSize = readFileHeader(reader);

            if (fileSize == 0)
            {
                LOGGER.warn("No chunks extracted from WebP file [" + imageFile + "]");
            }

            else if (getSafeFileSize() < fileSize)
            {
                throw new IllegalStateException("Discovered file size exceeds actual file length");
            }

            parseChunks(reader, fileSize);
        }

        catch (Exception exc)
        {
            exc.printStackTrace();
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
     * @throws IOException
     *
     * @throws IllegalStateException
     *         if the WebP header information is corrupted
     */
    private int readFileHeader(ByteStreamReader reader) throws IOException
    {
        byte[] type = reader.readBytes(4);

        if (!Arrays.equals(RIFF.getChunkName().getBytes(StandardCharsets.US_ASCII), type))
        {
            throw new IllegalStateException("Header [RIFF] not found. Found [" + ByteValueConverter.toHex(type) + "]. Not a valid WEBP format");
        }

        int fileSize = (int) reader.readUnsignedInteger() + 8;

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

        if (!Arrays.equals(WEBP.getChunkName().getBytes(), type))
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
     * @throws IOException
     *
     * @throws IndexOutOfBoundsException
     *         if the length of the payload is out of bounds, either negative or too large than the
     *         specified file size
     * @throws IllegalStateException
     *         if there is a malformed file
     */
    private void parseChunks(ByteStreamReader reader, long riffFileSize) throws IOException
    {
        chunks.clear();
        boolean firstChunk = true;

        // Use a loop that checks if we have at least 8 bytes for a header
        while (reader.getCurrentPosition() + 8 <= riffFileSize)
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

    @Deprecated
    private void parseChunksOld(ByteStreamReader reader, int riffFileSize) throws IOException
    {
        byte[] data;
        boolean firstChunk = true;

        chunks.clear();

        do
        {
            int fourCC = reader.readInteger();
            int payloadLength = reader.readInteger();

            WebPChunkType chunkType = WebPChunkType.findType(fourCC);

            if (payloadLength < 0 || payloadLength > riffFileSize)
            {
                throw new IndexOutOfBoundsException("Chunk Payload too large. Found [" + payloadLength + "]");
            }

            if (firstChunk && !FIRST_CHUNK_TYPES.contains(chunkType))
            {
                throw new IllegalStateException("First Chunk must be either VP8, VP8L, or VP8X. Found [" + WebPChunkType.getChunkName(fourCC) + "]");
            }

            if (requiredChunks == null || requiredChunks.contains(chunkType))
            {
                data = reader.readBytes(payloadLength);

                if (data.length != payloadLength)
                {
                    throw new IllegalStateException("Chunk payload truncated. Expected [" + payloadLength + "], got [" + data.length + "]");
                }

                addChunk(chunkType, fourCC, payloadLength, data);
                LOGGER.debug("Chunk type [" + chunkType + "] added for file [" + imageFile + "]");
            }

            else
            {
                reader.skip(payloadLength);
                LOGGER.debug("Chunk type [" + chunkType + "] skipped for file [" + imageFile + "]");
            }

            /*
             * According to the RIFF specification, any payload size having an
             * odd length must be added with one padding byte to make it even.
             */
            if (payloadLength % 2 != 0 && reader.getCurrentPosition() < riffFileSize)
            {
                reader.skip(1);
            }

            firstChunk = false;

        } while (reader.getCurrentPosition() < riffFileSize);
    }

}