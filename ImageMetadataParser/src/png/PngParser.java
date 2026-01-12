package png;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import com.adobe.internal.xmp.XMPException;
import common.AbstractImageParser;
import common.DigitalSignature;
import common.ImageFileInputStream;
import common.MetadataConstants;
import common.MetadataStrategy;
import common.Utils;
import logger.LogFactory;
import png.ChunkType.Category;
import tif.DirectoryIFD;
import tif.TifMetadata;
import tif.TifParser;
import xmp.XmpDirectory;
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
 * <li>IHDR - image header, always the first chunk in the data stream</li>
 * <li>PLTE - palette table, relevant for indexed PNG images</li>
 * <li>IDAT - image data chunk, multiple occurrences likely</li>
 * <li>IEND - image trailer, always the last chunk in the data stream</li>
 * </ul>
 *
 * <p>
 * <b>Ancillary Chunk Types</b>
 * </p>
 *
 * <ul>
 * <li>Transparency info: tRNS</li>
 * <li>Colour space info: cHRM, gAMA, iCCP, sBIT, sRGB, cICP, mDCV</li>
 * <li>Textual info: iTXt, tEXt, zTXt</li>
 * <li>Miscellaneous info: bKGD, hIST, pHYs, sPLT, eXIf</li>
 * <li>Time info: tIME</li>
 * <li>Animation information: acTL, fcTL, fdAT</li>
 * </ul>
 *
 * {@literal
 *  -- For developmental testing --
 *
 * <u>Some examples of exiftool usages</u>
 *
 * exiftool -time:all -a -G0:1 -s testPNGimage.png
 * exiftool.exe -overwrite_original -alldates="2012:10:07 11:15:45" testPNGimage.png
 * exiftool.exe "-FileModifyDate<PNG:CreationTime" testPNGimage.png
 *
 * exiftool "-PNG:CreationTime=2015:07:14 01:15:27" testPNGimage.png
 * exiftool -filemodifydate="2024:08:10 00:00:00" -createdate="2024:08:10 00:00:00"
 * "-PNG:CreationTime<FileModifyDate" testPNGimage.png
 * }
 *
 * @see <a href="https://www.w3.org/TR/png">See this link for more technical PNG background
 *      information.</a>
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class PngParser extends AbstractImageParser
{
    private static final LogFactory LOGGER = LogFactory.getLogger(PngParser.class);
    private static final ByteOrder PNG_BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private PngMetadata metadata;

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

        String ext = Utils.getFileExtension(getImageFile());

        if (!ext.equalsIgnoreCase("png"))
        {
            LOGGER.warn(String.format("Incorrect extension name detected in file [%s]. Should be [png], but found [%s]", getImageFile().getFileName(), ext));
        }
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
     * Reads the PNG image file to extract all supported raw metadata segments (specifically EXIF
     * and XMP, if present), and uses the extracted data to initialise the necessary metadata
     * objects for later data retrieval.
     *
     * It is important to note that PNG files usually do not have an EXIF segment block structured
     * inside.
     *
     * However, it will attempt to find information from 4 possible chunks:
     * {@code ChunkType.eXIf, ChunkType.tEXt, ChunkType.iTXt or ChunkType.zTXt}. The last 3 textual
     * chunks are processed for both native text metadata (like Creation Time) and embedded XMP
     * metadata.
     *
     * If any of these 3 textual chunks does contain data, it will be quite rudimentary, such as
     * obtaining the Creation Time, Last Modification Date, etc.
     *
     * @see <a href="https://www.w3.org/TR/png/#11keywords">www.w3.org/TR/png/#11keywords - for more information.</a>
     *
     * @return true once at least one metadata segment has been successfully parsed, otherwise false
     *
     * @throws IOException
     *         if the file reading error occurs during the parsing
     */
    @Override
    public boolean readMetadata() throws IOException
    {
        EnumSet<ChunkType> chunkSet = EnumSet.of(ChunkType.tEXt, ChunkType.zTXt, ChunkType.iTXt, ChunkType.eXIf);

        try (ImageFileInputStream pngStream = new ImageFileInputStream(getImageFile(), PNG_BYTE_ORDER))
        {
            ChunkHandler handler = new ChunkHandler(getImageFile(), pngStream, chunkSet);

            metadata = new PngMetadata(PNG_BYTE_ORDER);

            if (handler.parseMetadata())
            {
                Optional<List<PngChunk>> optList = handler.getChunks(Category.TEXTUAL);

                if (optList.isPresent())
                {
                    PngDirectory textualDir = new PngDirectory(Category.TEXTUAL);

                    textualDir.addChunkList(optList.get());
                    metadata.addDirectory(textualDir);

                    // Handle XMP data if available
                    Optional<byte[]> optXmp = handler.getRawXmpPayload();

                    if (optXmp.isPresent())
                    {
                        XmpDirectory xmpDir = XmpHandler.addXmpDirectory(optXmp.get());
                        metadata.addXmpDirectory(xmpDir);
                    }

                    else
                    {
                        LOGGER.debug("No iTXt chunk containing XMP payload found in file [" + getImageFile() + "]");
                    }
                }

                else
                {
                    LOGGER.debug("No textual information found in file [" + getImageFile() + "]");
                }

                Optional<PngChunk> optExif = handler.getFirstChunk(ChunkType.eXIf);

                if (optExif.isPresent())
                {
                    PngDirectory exifDir = new PngDirectory(ChunkType.eXIf.getCategory());

                    exifDir.add(optExif.get());
                    metadata.addDirectory(exifDir);
                }

                else
                {
                    LOGGER.debug("No Exif segment found in file [" + getImageFile() + "]");
                }
            }

            else
            {
                LOGGER.warn("Unable to find metadata in file [" + getImageFile() + "] due to an error");
                return false;
            }
        }

        catch (XMPException exc)
        {
            LOGGER.error("Unable to parse XMP directory payload [" + exc.getMessage() + "]", exc);
        }

        return metadata.hasMetadata();
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
     * Generates a human-readable diagnostic string for PNG metadata.
     *
     * <p>
     * This includes textual chunks (tEXt, iTXt, zTXt) and optional EXIF data if the eXIf chunk is
     * present and and also XMP data, which is typically embedded within an iTXt chunk.
     * </p>
     *
     * @return a formatted string suitable for diagnostics, logging, or inspection
     */
    @Override
    public String formatDiagnosticString()
    {
        StringBuilder sb = new StringBuilder();
        MetadataStrategy<PngDirectory> meta = getMetadata();

        try
        {
            sb.append("\t\t\tPNG Metadata Summary").append(System.lineSeparator()).append(System.lineSeparator());
            sb.append(super.formatDiagnosticString());

            if (meta instanceof PngMetadataStrategy)
            {
                PngMetadataStrategy png = (PngMetadataStrategy) meta;

                if (png.hasTextualData())
                {
                    for (PngDirectory cd : png)
                    {
                        if (cd.getCategory() == Category.TEXTUAL)
                        {
                            sb.append("Textual Chunks").append(System.lineSeparator());
                            sb.append(MetadataConstants.DIVIDER).append(System.lineSeparator());
                            sb.append(cd);
                            break;
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
                    PngDirectory cd = png.getDirectory(Category.MISC);

                    if (cd != null)
                    {
                        PngChunk chunk = cd.getFirstChunk(ChunkType.eXIf);

                        if (chunk != null)
                        {
                            TifMetadata exif = TifParser.parseTiffMetadataFromBytes(chunk.getPayloadArray());

                            sb.append("EXIF Metadata").append(System.lineSeparator());
                            sb.append(MetadataConstants.DIVIDER).append(System.lineSeparator());

                            for (DirectoryIFD ifd : exif)
                            {
                                sb.append(ifd);
                            }
                        }

                        else
                        {
                            sb.append("No EXIF metadata found. eXIf chunk missing from MISC directory").append(System.lineSeparator());
                        }
                    }

                    else
                    {
                        sb.append("No EXIF metadata found. MISC directory missing").append(System.lineSeparator());
                    }
                }

                else
                {
                    sb.append("No EXIF metadata found").append(System.lineSeparator());
                }

                sb.append(System.lineSeparator());

                if (png.hasXmpData())
                {
                    sb.append(png.getXmpDirectory());
                }

                else
                {
                    sb.append("No XMP metadata found").append(System.lineSeparator());
                }

                sb.append(MetadataConstants.DIVIDER);
            }

            else
            {
                sb.append("No PNG metadata available").append(System.lineSeparator());
            }
        }

        catch (Exception exc)
        {
            LOGGER.error("Diagnostics failed for file [" + getImageFile() + "]", exc);

            sb.append("Error generating diagnostics [")
                    .append(exc.getClass().getSimpleName())
                    .append("]: ")
                    .append(exc.getMessage())
                    .append(System.lineSeparator());
        }

        return sb.toString();
    }
}