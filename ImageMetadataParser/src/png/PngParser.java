package png;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import batch.BatchMetadataUtils;
import common.AbstractImageParser;
import common.DigitalSignature;
import common.ImageFileInputStream;
import common.ImageReadErrorException;
import common.MetadataStrategy;
import logger.LogFactory;
import png.ChunkType.Category;
import tif.DirectoryIFD;
import tif.DirectoryIFD.EntryIFD;
import tif.ExifStrategy;
import tif.tagspecs.TagPngChunk;
import xmp.XmpHandler;

/**
 * This program aims to read PNG image files and retrieve data structured in a series of chunks. For
 * accessing metadata, only any of the textual chunks or the EXIF chunk, if present, will be
 * processed.
 *
 * Normally, most PNG files do not contain the EXIF structure, however, it will attempt to search
 * for these 4 potential chunks: ChunkType.eXIf, ChunkType.tEXt, ChunkType.iTXt or ChunkType.zTXt.
 *
 * <p>
 * <b>PNG Data Stream</b>
 * </p>
 *
 * <p>
 * The PNG data stream begins with a PNG SIGNATURE (0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A)
 * followed by a series of chunks. Each chunk consists of:
 * </p>
 *
 * <ul>
 * <li>4 bytes for data field length (unsigned, usually &lt;= 31 bytes)</li>
 * <li>4 bytes for chunk type (only [65-90] and [97-122]) ASCII codes</li>
 * <li>Variable number of bytes for data field</li>
 * <li>4 bytes for CRC computed from chunk type and data only</li>
 * </ul>
 *
 * <p>
 * There are two categories of chunks: Critical and Ancillary.
 * </p>
 *
 * <p>
 * <b>Critical Chunk Types</b>
 * </p>
 *
 * <ul>
 * <li>IHDR - image header, always the initial chunk in the data stream</li>
 * <li>PLTE - palette table, relevant for indexed PNG images</li>
 * <li>IDAT - image data chunk, multiple occurrences likely</li>
 * <li>IEND - image trailer, always the final chunk in the data stream</li>
 * </ul>
 *
 * <p>
 * <b>Ancillary Chunk Types</b>
 * </p>
 *
 * <ul>
 * <li>Transparency info: tRNS</li>
 * <li>Colour space info: cHRM, gAMA, iCCP, sBIT, sRGB</li>
 * <li>Textual info: iTXt, tEXt, zTXt</li>
 * <li>Miscellaneous info: bKGD, hIST, pHYs, sPLT</li>
 * <li>Time info: tIME</li>
 * </ul>
 *
 * <p>
 * <b>Chunk Processing</b>
 * </p>
 *
 * <ul>
 * <li>Only chunks of specified types in the {@code requiredChunks} list are read</li>
 * <li>An empty {@code requiredChunks} list results in no data being extracted from the source
 * stream</li>
 * <li>A null list results in all data being copied from the source stream</li>
 * </ul>
 *
 * @see <a href="https://www.w3.org/TR/png">See this link for more technical background
 *      information.</a>
 *
 *      -- For developmental testing --
 *
 *      Some examples of exiftool usages
 *
 *      <code>
 * exiftool -time:all -a -G0:1 -s testPNGimage.png
 * exiftool.exe -overwrite_original -alldates="2012:10:07 11:15:45" testPNGimage.png
 * exiftool.exe "-FileModifyDate<PNG:CreationTime" testPNGimage.png
 *
 * exiftool "-PNG:CreationTime=2015:07:14 01:15:27" testPNGimage.png
 * exiftool -filemodifydate="2024:08:10 00:00:00" -createdate="2024:08:10 00:00:00"
 * "-PNG:CreationTime<FileModifyDate" testPNGimage.png
 *      </code>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class PngParser extends AbstractImageParser
{
    private static final LogFactory LOGGER = LogFactory.getLogger(PngParser.class);
    private static final ByteOrder PNG_BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private static final String XMP_KEYWORD = "XML:com.adobe.xmp";
    private MetadataStrategy<PngDirectory> metadata;

    /**
     * This constructor creates an instance for processing the specified image file.
     *
     * @param fpath
     *        specifies the PNG file path, encapsulated in a Path object
     *
     * @throws IOException
     *         if the file is not a regular type or does not exist
     */
    public PngParser(Path fpath) throws IOException
    {
        super(fpath);

        LOGGER.info("Image file [" + getImageFile() + "] loaded");

        String ext = BatchMetadataUtils.getFileExtension(getImageFile());

        if (!ext.equalsIgnoreCase("png"))
        {
            LOGGER.warn(String.format("Incorrect extension name detected in file [%s]. Should be [png], but found [%s]", getImageFile().getFileName(), ext));
        }
    }

    /**
     * This constructor creates an instance for processing the specified image file.
     *
     * @param file
     *        specifies the PNG image file to be read
     *
     * @throws IOException
     *         if an I/O problem has occurred
     */
    public PngParser(String file) throws IOException
    {
        this(Paths.get(file));
    }

    /**
     * Reads the PNG image file to extract all supported raw metadata segments (specifically EXIF
     * and XMP, if present), and uses the extracted data to initialise the necessary metadata
     * objects for later data retrieval.
     *
     * It is important to note that PNG files usually do not have an EXIF segment block structured
     * inside.
     *
     * However, it will attempt to find information from 4 possible chunks:
     * {@code ChunkType.eXIf, ChunkType.tEXt, ChunkType.iTXt or ChunkType.zTXt}. The last 3 chunks
     * are textual.
     *
     * If any of these 3 textual chunks does contain data, it will be quite rudimentary, such as
     * obtaining the Creation Time, Last Modification Date, etc.
     *
     * See https://www.w3.org/TR/png/#11keywords for more information.
     *
     * @return true once at least one metadata segment has been successfully parsed, otherwise false
     *
     * @throws ImageReadErrorException
     *         if a parsing or file reading error occurs
     */
    @Override
    public boolean readMetadata() throws ImageReadErrorException
    {
        Optional<PngChunk> exif;
        Optional<List<PngChunk>> textual;
        EnumSet<ChunkType> chunkSet = EnumSet.of(ChunkType.tEXt, ChunkType.zTXt, ChunkType.iTXt, ChunkType.eXIf);

        try (ImageFileInputStream pngStream = new ImageFileInputStream(getImageFile(), PNG_BYTE_ORDER))
        {
            metadata = new PngMetadata();
            ChunkHandler handler = new ChunkHandler(getImageFile(), pngStream, chunkSet);

            handler.parseMetadata();
            textual = handler.getChunks(Category.TEXTUAL);

            if (textual.isPresent())
            {
                PngDirectory textualDir = new PngDirectory(Category.TEXTUAL);

                textualDir.addChunkList(textual.get());
                metadata.addDirectory(textualDir);
            }

            else
            {
                LOGGER.info("No textual information found in file [" + getImageFile() + "]");
            }

            exif = handler.getFirstChunk(ChunkType.eXIf);

            if (exif.isPresent())
            {
                PngDirectory exifDir = new PngDirectory(ChunkType.eXIf.getCategory());

                exifDir.add(exif.get());
                metadata.addDirectory(exifDir);
            }

            else
            {
                LOGGER.info("No Exif segment found in file [" + getImageFile() + "]");
            }
        }

        catch (IOException exc)
        {
            throw new ImageReadErrorException("Problem reading data stream: [" + exc.getMessage() + "]", exc);
        }

        return (textual.isPresent() || exif.isPresent());
    }

    /**
     * Retrieves the extracted metadata from the PNG image file, or an empty fallback if
     * unavailable.
     *
     * @return a MetadataStrategy object
     */
    @Override
    public MetadataStrategy<PngDirectory> getMetadata()
    {
        if (metadata == null)
        {
            LOGGER.warn("No metadata information has been parsed yet");

            return new PngMetadata();
        }

        return metadata;
    }

    /**
     * Returns the detected {@code PNG} format.
     *
     * @return a {@link DigitalSignature} enum constant representing this image format
     */
    @Override
    public DigitalSignature getImageFormat()
    {
        return DigitalSignature.PNG;
    }

    /**
     * Generates a human-readable diagnostic string for PNG metadata.
     *
     * <p>
     * This includes textual chunks (tEXt, iTXt, zTXt) and optional EXIF data (from the eXIf chunk
     * if present).
     * </p>
     *
     * @return a formatted string suitable for diagnostics, logging, or inspection
     */
    @Override
    public String formatDiagnosticString()
    {
        MetadataStrategy<?> meta = getMetadata();
        StringBuilder sb = new StringBuilder();

        try
        {
            sb.append("\t\t\tPNG Metadata Summary").append(System.lineSeparator()).append(System.lineSeparator());
            sb.append(super.formatDiagnosticString());

            if (meta instanceof PngStrategy && ((PngStrategy) meta).hasExifData())
            {
                PngStrategy png = (PngStrategy) meta;

                if (png.hasTextualData())
                {
                    sb.append("Textual Chunks").append(System.lineSeparator());
                    sb.append(DIVIDER).append(System.lineSeparator());

                    for (Object obj : png)
                    {
                        if (obj instanceof PngDirectory)
                        {
                            PngDirectory cd = (PngDirectory) obj;

                            if (cd.getCategory() == Category.TEXTUAL)
                            {
                                for (PngChunk chunk : cd)
                                {
                                    String keywordValue = (chunk.getKeywordPair().isPresent() ? chunk.getKeywordPair().get().getKeyword() : "N/A");
                                    String textValue = (chunk.getKeywordPair().isPresent() ? chunk.getKeywordPair().get().getValue() : "N/A");

                                    sb.append(String.format(FMT, "Tag Type", chunk.getTag()));
                                    sb.append(String.format(FMT, "Chunk Type", chunk.getType()));
                                    sb.append(String.format(FMT, "Chunk Bytes", chunk.getLength()));
                                    sb.append(String.format(FMT, "Keyword", keywordValue));

                                    if (chunk instanceof PngChunkITXT && keywordValue.equals(XMP_KEYWORD))
                                    {
                                        byte[] xmpData = ((PngChunkITXT) chunk).getPayloadBytes();

                                        XmpHandler xmp = new XmpHandler(xmpData);
                                        xmp.parseMetadata();

                                        Map<String, String> map = xmp.readPropertyData();

                                        for (Map.Entry<String, String> entry : map.entrySet())
                                        {
                                            if (entry.getValue().isEmpty())
                                            {
                                                continue;
                                            }

                                            sb.append(String.format(FMT, entry.getKey(), entry.getValue()));
                                        }
                                    }

                                    else
                                    {
                                        sb.append(String.format(FMT, "Text", textValue));
                                    }

                                    sb.append(System.lineSeparator());
                                }
                            }
                        }
                    }
                }

                else
                {
                    sb.append("No textual metadata found").append(System.lineSeparator());
                }

                sb.append(System.lineSeparator());

                if (png.hasExifData())
                {
                    Object obj = png.getDirectory(TagPngChunk.CHUNK_TAG_EXIF_PROFILE);

                    if (obj instanceof ExifStrategy)
                    {
                        ExifStrategy exifDir = (ExifStrategy) obj;

                        sb.append("EXIF Metadata").append(System.lineSeparator());
                        sb.append(DIVIDER).append(System.lineSeparator());

                        for (DirectoryIFD ifd : exifDir)
                        {
                            sb.append("Directory Type - ")
                                    .append(ifd.getDirectoryType().getDescription())
                                    .append(System.lineSeparator()).append(System.lineSeparator());

                            for (EntryIFD entry : ifd)
                            {
                                String value = ifd.getString(entry.getTag());

                                sb.append(String.format(FMT, "Tag Type", entry.getTag()));
                                sb.append(String.format("%-20s:\t0x%04X%n", "Tag ID", entry.getTagID()));
                                sb.append(String.format(FMT, "Field Type", entry.getFieldType()));
                                sb.append(String.format(FMT, "Count", entry.getCount()));
                                sb.append(String.format(FMT, "Value", (value == null || value.isEmpty() ? "Empty" : value)));
                                sb.append(System.lineSeparator());
                            }
                        }
                    }
                }

                else
                {
                    sb.append("No EXIF metadata found").append(System.lineSeparator());
                }
            }

            else
            {
                sb.append("No PNG metadata available").append(System.lineSeparator());
            }
        }

        catch (Exception exc)
        {
            LOGGER.error("Diagnostics failed for file [" + getImageFile() + "]", exc);

            sb.append("Error generating diagnostics: ").append(exc.getMessage()).append(System.lineSeparator());

            exc.printStackTrace();
        }

        return sb.toString();
    }
}