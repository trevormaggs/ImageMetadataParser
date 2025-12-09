package png;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import common.DigitalSignature;
import common.ImageFileInputStream;
import common.ImageHandler;
import common.ImageReadErrorException;
import logger.LogFactory;
import png.ChunkType.Category;

/**
 * Handles the processing and collection of metadata-related chunks from a PNG image file.
 *
 * <p>
 * This handler processes PNG chunks such as:
 * </p>
 *
 * <ul>
 * <li>{@code tEXt}, {@code iTXt}, {@code zTXt} – textual metadata chunks</li>
 * <li>{@code eXIf} – embedded EXIF metadata (in TIFF format)</li>
 * </ul>
 *
 * <p>
 * This class is typically used during PNG metadata parsing and delegates specific parsing
 * responsibilities to appropriate chunk or Exif handlers.
 * </p>
 *
 * <p>
 * <b>Note:</b> In Windows Explorer, the {@code Date Taken} attribute is often resolved from the
 * {@code Creation Time} textual keyword rather than the embedded EXIF block. This behaviour can
 * affect the chronological ordering of PNG files when viewed or processed on Windows systems.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class ChunkHandler implements ImageHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(ChunkHandler.class);
    private static final byte[] PNG_SIGNATURE_BYTES = DigitalSignature.PNG.getMagicNumbers(0);
    private final Path imageFile;
    private final boolean strictMode;
    private final List<PngChunk> chunks;
    private final ImageFileInputStream reader;
    private final EnumSet<ChunkType> requiredChunks;

    /**
     * Constructs a handler to parse selected chunks from a PNG image file, assuming the read mode
     * is not strict.
     *
     * @param fpath
     *        the path to the PNG file for logging purposes
     * @param reader
     *        byte reader for raw PNG stream
     * @param requiredChunks
     *        an optional set of chunk types to be extracted (null means all chunks are selected)
     */
    public ChunkHandler(Path fpath, ImageFileInputStream reader, EnumSet<ChunkType> requiredChunks)
    {
        this(fpath, reader, requiredChunks, false);
    }

    /**
     * Constructs a handler to parse selected chunks from a PNG image file.
     *
     * @param fpath
     *        the path to the PNG file for logging purposes
     * @param reader
     *        byte reader for raw PNG stream
     * @param requiredChunks
     *        an optional set of chunk types to be extracted (null means all chunks are selected)
     * @param strict
     *        true to make it strict, otherwise false for a lenient reading process
     */
    public ChunkHandler(Path fpath, ImageFileInputStream reader, EnumSet<ChunkType> requiredChunks, boolean strict)
    {
        this.imageFile = fpath;
        this.reader = reader;
        this.requiredChunks = requiredChunks;
        this.strictMode = strict;
        this.chunks = new ArrayList<>();
    }

    /**
     * Checks if a chunk with the specified type has already been set.
     *
     * @param type
     *        the type of the chunk
     * @return true if the chunk is present
     */
    public boolean existsChunkType(ChunkType type)
    {
        return chunks.stream().anyMatch(chunk -> chunk.getType() == type);
    }

    /**
     * Checks if a chunk is classified in the specified category, for example: Textual, Header or
     * Time etc.
     *
     * @param cat
     *        the type of ChunkType.Category enumeration
     * @return true if the chunk is present
     */
    public boolean existsChunkCategory(Category cat)
    {
        return chunks.stream().anyMatch(chunk -> chunk.getType().getCategory() == cat);
    }

    /**
     * Retrieves a list of chunks that have been extracted.
     *
     * @return an {@link Optional} containing an unmodified list of {@link PngChunk} objects if
     *         found, or {@link Optional#empty()} if no specific chunks are present
     */
    public Optional<List<PngChunk>> getChunks()
    {
        return chunks.isEmpty() ? Optional.empty() : Optional.of(Collections.unmodifiableList(chunks));
    }

    /**
     * Retrieves a list of specific chunks based on the specified category.
     *
     * <p>
     * For example, for textual chunks, a list of {@code tEXt}, {@code iTXt}, and {@code zTXt}
     * chunks, will be collected and returned.
     * </p>
     *
     * @param cat
     *        the type of ChunkType.Category enumeration
     * @return an {@link Optional} containing a list of {@link PngChunk} objects if found, or
     *         {@link Optional#empty()} if no specific chunks are present
     */
    public Optional<List<PngChunk>> getChunks(Category cat)
    {
        if (cat == null || cat == Category.UNDEFINED)
        {
            LOGGER.warn("Category [" + cat + "] is undefined");
            return Optional.empty();
        }

        List<PngChunk> chunkList = new ArrayList<>();

        for (PngChunk chunk : chunks)
        {
            if (chunk.getType().getCategory() == cat)
            {
                chunkList.add(chunk);
            }
        }

        return chunkList.isEmpty() ? Optional.empty() : Optional.of(Collections.unmodifiableList(chunkList));
    }

    /**
     * Retrieves a list of specific chunks based on the specified type, for example: {@code tEXt},
     * {@code iTXt}, or {@code eXIf} etc.
     *
     * @param type
     *        the type of the chunk
     * @return an {@link Optional} containing a list of {@link PngChunk} objects if found, or
     *         {@link Optional#empty()} if the specified chunk type cannot be found
     */
    public Optional<List<PngChunk>> getChunks(ChunkType type)
    {
        if (type == null || type == ChunkType.UNKNOWN)
        {
            LOGGER.warn("Chunk Type [" + type + "] is undefined");
            return Optional.empty();
        }

        List<PngChunk> chunkList = new ArrayList<>();

        for (PngChunk chunk : chunks)
        {
            if (chunk.getType() == type)
            {
                chunkList.add(chunk);
            }
        }

        return chunkList.isEmpty() ? Optional.empty() : Optional.of(Collections.unmodifiableList(chunkList));
    }

    /**
     * Retrieves the first occurrence of the chunk matching the specified type. Any subsequent
     * chunks will be skipped.
     *
     * @param type
     *        the type of the chunk
     * @return an {@link Optional} containing the discovered {@link PngChunk} object if found, or
     *         {@link Optional#empty()} if the specified chunk type cannot be found
     */
    public Optional<PngChunk> getFirstChunk(ChunkType type)
    {
        if (type == null || type == ChunkType.UNKNOWN)
        {
            LOGGER.warn("Chunk Type [" + type + "] is undefined");
            return Optional.empty();
        }

        for (PngChunk chunk : chunks)
        {
            if (chunk.getType() == type)
            {
                return Optional.of(chunk);
            }
        }

        return Optional.empty();
    }

    /**
     * Retrieves the last instance of the chunk matching the specified type. Any previous chunks
     * will be overwritten.
     *
     * @param type
     *        the type of the chunk
     * @return an {@link Optional} containing the last {@link PngChunk} object if found, or
     *         {@link Optional#empty()} if the specified chunk type cannot be found
     */
    public Optional<PngChunk> getLastChunk(ChunkType type)
    {
        if (type == null || type == ChunkType.UNKNOWN)
        {
            LOGGER.warn("Chunk Type [" + type + "] is undefined");
            return Optional.empty();
        }

        for (int i = chunks.size() - 1; i >= 0; i--)
        {
            PngChunk chunk = chunks.get(i);

            if (chunk.getType() == type)
            {
                return Optional.of(chunk);
            }
        }

        return Optional.empty();
    }

    /**
     * Returns the size of the image file being processed, in bytes.
     *
     * <p>
     * Any {@link IOException} that occurs while determining the size will be handled internally,
     * and the method will return {@code 0} if the size cannot be determined.
     * </p>
     *
     * @return the file size in bytes, or -1 if it cannot be determined
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
            return -1L;
        }
    }

    /**
     * Begins metadata processing by parsing the PNG file and extracting chunk data.
     *
     * It also checks if the PNG file contains the expected magic numbers in the first few bytes in
     * the file stream. If these numbers actually exist, they will then be skipped.
     *
     * @return true if at least one chunk element was successfully extracted, or false if no
     *         relevant data was found
     *
     * @throws ImageReadErrorException
     *         if an error occurs while parsing the PNG file
     * @throws IOException
     *         if there is an I/O stream error
     */
    @Override
    public boolean parseMetadata() throws IOException, ImageReadErrorException
    {
        byte[] signature = reader.readBytes(PNG_SIGNATURE_BYTES.length);

        if (signature.length < PNG_SIGNATURE_BYTES.length)
        {
            LOGGER.warn("Data is too short in file [" + imageFile + "]");
            return false;
        }

        /*
         * Note: PNG_SIGNATURE_BYTES (magic numbers) are mapped to
         * {0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}
         */
        if (Arrays.equals(signature, PNG_SIGNATURE_BYTES))
        {
            parseChunks();

            if (chunks.isEmpty())
            {
                LOGGER.info("No chunks extracted from PNG file [" + imageFile + "]");
                return false;
            }
        }

        else
        {
            throw new ImageReadErrorException("PNG file [" + imageFile + "] has an invalid signature. File may be corrupted.");
        }

        return true;
    }

    /**
     * Returns a textual representation of all parsed PNG chunks in this file.
     *
     * @return formatted string of all parsed chunk entries
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        for (PngChunk chunk : chunks)
        {
            sb.append(chunk).append(System.lineSeparator());
        }

        return sb.toString();
    }

    /**
     * Processes the PNG data stream and extracts matching chunk types into memory.
     *
     * @throws ImageReadErrorException
     *         if invalid structure or duplicate chunks are found, including a CRC calculation
     *         mismatch error
     * @throws IOException
     *         if there is an I/O stream error
     */
    private void parseChunks() throws ImageReadErrorException, IOException
    {
        int position = 0;
        byte[] typeBytes;
        ChunkType chunkType;
        boolean foundIEND = false;
        long fileSize = getSafeFileSize();

        while (!foundIEND)
        {
            /*
             * 12 bytes = minimum chunk size: (Length (4) + Type (4) + CRC (4)
             */
            if (fileSize == 0 || reader.getCurrentPosition() + 12 > fileSize)
            {
                throw new ImageReadErrorException("Unexpected end of PNG file before IEND chunk detected");
            }

            // Read LENGTH (4 bytes)
            long length = reader.readUnsignedInteger();

            if (length > Integer.MAX_VALUE)
            {
                throw new ImageReadErrorException("Out of bounds chunk length [" + length + "] detected");
            }

            // Read TYPE (4 bytes)
            typeBytes = reader.readBytes(4);
            chunkType = ChunkType.getChunkType(typeBytes);

            if (chunkType != ChunkType.UNKNOWN)
            {
                if (position == 0 && chunkType != ChunkType.IHDR)
                {
                    throw new ImageReadErrorException("First chunk in file [" + imageFile + "] must be [" + ChunkType.IHDR + "], but found [" + chunkType + "]");
                }

                if (!chunkType.isMultipleAllowed() && existsChunkType(chunkType))
                {
                    throw new ImageReadErrorException("Duplicate [" + chunkType + "] found in file [" + imageFile + "]. This is disallowed");
                }

                if (chunkType == ChunkType.IEND)
                {
                    foundIEND = true;
                }

                byte[] chunkData = null;
                boolean isRequired = requiredChunks == null || requiredChunks.contains(chunkType);

                if (isRequired)
                {
                    chunkData = reader.readBytes((int) length);
                }

                else
                {
                    reader.skip(length);
                    LOGGER.debug("Chunk type [" + chunkType + "] was not required and data was skipped");
                }

                // Read CRC (4 bytes) - always the next 4 bytes after the data
                int crc32 = (int) reader.readUnsignedInteger();

                // Only proceed with chunk creation and CRC validation if the data was read
                if (chunkData != null)
                {
                    PngChunk newChunk = addChunk(chunkType, length, typeBytes, crc32, chunkData);
                    int expectedCrc = newChunk.calculateCrc();

                    if (expectedCrc != crc32)
                    {
                        String msg = String.format("CRC mismatch for chunk [%s] in file [%s]. Calculated: 0x%08X, Expected: 0x%08X. File may be corrupt", chunkType, imageFile, expectedCrc, crc32);

                        if (strictMode)
                        {
                            throw new ImageReadErrorException(msg);
                        }

                        else
                        {
                            LOGGER.warn(msg);
                        }
                    }

                    LOGGER.debug("Chunk type [" + chunkType + "] added for file [" + imageFile + "]");
                }
            }

            else
            {
                /*
                 * Handle UNKNOWN chunk type by skipping the full length
                 * of data plus 4 bytes for the CRC
                 */
                reader.skip(length + 4);
                LOGGER.warn("Unknown chunk type [" + new String(typeBytes, StandardCharsets.US_ASCII) + "] skipped");
                LOGGER.debug("Data skipped by length [" + (length + 4) + "] in file [" + imageFile + "] due to an unknown chunk");
            }

            position++;
        }
    }

    /**
     * Adds a parsed chunk to the internal chunk collection. Special types such as {@code iTXt},
     * {@code zTXt}, and {@code tEXt} are instantiated into specific subclasses.
     *
     * @param chunkType
     *        the type of PNG chunk
     * @param length
     *        the data length of the chunk
     * @param typeBytes
     *        the raw 4-byte chunk type for CRC calculation
     * @param crc32
     *        the CRC value read from the file
     * @param data
     *        raw chunk data
     * @return a populated {@link PngChunk} instance
     */
    private PngChunk addChunk(ChunkType chunkType, long length, byte[] typeBytes, int crc32, byte[] data)
    {
        PngChunk newChunk;

        switch (chunkType)
        {
            case tEXt:
                newChunk = new PngChunkTEXT(length, typeBytes, crc32, data);
            break;

            case iTXt:
                newChunk = new PngChunkITXT(length, typeBytes, crc32, data);
            break;

            case zTXt:
                newChunk = new PngChunkZTXT(length, typeBytes, crc32, data);
            break;

            default:
                newChunk = new PngChunk(length, typeBytes, crc32, data);
        }

        chunks.add(newChunk);

        return newChunk;
    }
}