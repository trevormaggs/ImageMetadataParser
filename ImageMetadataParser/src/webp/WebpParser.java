package webp;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Optional;
import com.adobe.internal.xmp.XMPException;
import batch.BatchMetadataUtils;
import common.AbstractImageParser;
import common.DigitalSignature;
import common.MetadataConstants;
import common.MetadataStrategy;
import logger.LogFactory;
import tif.DirectoryIFD;
import tif.TifMetadata;
import tif.TifParser;
import xmp.XmpDirectory;
import xmp.XmpHandler;

/**
 * This program aims to read WebP image files and retrieve data structured in a series of RIFF-based
 * chunks. For metadata access, only the EXIF chunk, if present, will be processed. At this stage,
 * XMP metadata is considered for inclusion in this class on a later date.
 *
 * <p>
 * <b>WebP Data Stream</b>
 * </p>
 *
 * <p>
 * The WebP file format is built on the RIFF container, beginning with a fixed signature:
 * </p>
 *
 * <pre>
 * <code>RIFF</code> + FileSize (4 bytes) + <code>WEBP</code>
 * </pre>
 *
 * <p>
 * This is followed by a sequence of chunks. Each chunk includes:
 * </p>
 *
 * <ul>
 * <li>4 bytes: Chunk FourCC (ASCII, for example: {@code VP8 }, {@code VP8X}, {@code EXIF})</li>
 * <li>4 bytes: Chunk payload size (unsigned, little-endian)</li>
 * <li>Payload: Variable-length data</li>
 * <li>Padding: If size is odd, 1 padding byte follows (not counted in the size field)</li>
 * </ul>
 *
 * <p>
 * There are both mandatory and optional chunk types.
 * </p>
 *
 * <p>
 * <b>Core Chunk Types</b>
 * </p>
 *
 * <ul>
 * <li>{@code VP8 } – Lossy bitstream (standard)</li>
 * <li>{@code VP8L} – Lossless bitstream</li>
 * <li>{@code VP8X} – Extended format chunk (required for metadata and animation)</li>
 * </ul>
 *
 * <p>
 * <b>Optional Chunk Types</b>
 * </p>
 *
 * <ul>
 * <li>{@code EXIF} – Embedded EXIF metadata</li>
 * <li>{@code ICCP} – Embedded ICC color profile</li>
 * <li>{@code XMP } – XMP metadata</li>
 * <li>{@code ANIM} / {@code ANMF} – Animation control and frames</li>
 * </ul>
 *
 * <p>
 * <b>Chunk Processing</b>
 * </p>
 *
 * <ul>
 * <li>Only chunks specified in the {@code requiredChunks} list are read</li>
 * <li>An empty {@code requiredChunks} list disables chunk extraction</li>
 * <li>A null list results in all chunks being extracted</li>
 * </ul>
 *
 * <p>
 * When the {@code EXIF} chunk is found, its payload is parsed as TIFF/EXIF-formatted metadata. This
 * is commonly used to extract orientation, date, and camera information.
 * </p>
 *
 * @see <a href="https://developers.google.com/speed/webp/docs/riff_container">WebP RIFF Container
 *      Specification</a>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class WebpParser extends AbstractImageParser
{
    private static final LogFactory LOGGER = LogFactory.getLogger(WebpParser.class);
    private static final EnumSet<WebPChunkType> DEFAULT_METADATA_CHUNKS = EnumSet.of(WebPChunkType.EXIF, WebPChunkType.XMP);
    private TifMetadata metadata;

    /**
     * This constructor creates an instance for processing the specified image file.
     *
     * @param file
     *        specifies the WebP image file to be read
     *
     * @throws IOException
     *         if an I/O problem has occurred
     */
    public WebpParser(String file) throws IOException
    {
        this(Paths.get(file));
    }

    /**
     * This constructor creates an instance for processing the specified image file.
     *
     * @param fpath
     *        specifies the WebP file path, encapsulated in a Path object
     *
     * @throws IOException
     *         if the file is not a regular type or does not exist
     */
    public WebpParser(Path fpath) throws IOException
    {
        super(fpath);

        LOGGER.info("Image file [" + getImageFile() + "] loaded");

        String ext = BatchMetadataUtils.getFileExtension(getImageFile());

        if (!ext.equalsIgnoreCase("webp"))
        {
            LOGGER.warn("Incorrect extension name detected in file [" + getImageFile().getFileName() + "]. Should be [webp], but found [" + ext + "]");
        }
    }

    /**
     * Reads the WebP image file to extract all supported raw metadata segments (specifically EXIF
     * and XMP, if present), and uses the extracted data to initialise the necessary metadata
     * object for later data retrieval.
     *
     * @return true when at least one metadata segment has been successfully parsed, otherwise false
     *
     * @throws IOException
     *         if the file reading error occurs during the parsing
     */
    @Override
    public boolean readMetadata() throws IOException
    {
        WebpHandler handler = new WebpHandler(getImageFile(), DEFAULT_METADATA_CHUNKS);
        metadata = new TifMetadata();

        if (handler.parseMetadata())
        {
            // System.out.printf("%s\n", handler);

            Optional<byte[]> exif = handler.getRawExifPayload();
            Optional<byte[]> optXmp = handler.getRawXmpPayload();

            if (exif.isPresent())
            {
                metadata = TifParser.parseTiffMetadataFromBytes(exif.get());
            }

            else
            {
                LOGGER.info("No EXIF metadata found in file [" + getImageFile() + "]");
            }

            if (optXmp.isPresent())
            {
                try
                {
                    XmpDirectory xmpDir = XmpHandler.addXmpDirectory(optXmp.get());
                    metadata.addXmpDirectory(xmpDir);
                }

                catch (XMPException exc)
                {
                    LOGGER.error("Unable to parse XMP directory payload [" + exc.getMessage() + "]", exc);
                }
            }

            else
            {
                LOGGER.debug("No XMP payload found in file [" + getImageFile() + "]");
            }
        }

        else
        {
            LOGGER.debug("Unable to find metadata in file [" + getImageFile() + "] due to an error");
        }

        return metadata.hasMetadata();
    }

    /**
     * Retrieves the extracted metadata from the WebP image file, or a fallback if unavailable.
     *
     * @return a {@link MetadataStrategy} object
     */
    @Override
    public MetadataStrategy<DirectoryIFD> getMetadata()
    {
        if (metadata == null)
        {
            LOGGER.warn("No metadata information has been parsed yet");

            /* Fallback to empty metadata */
            return new TifMetadata();
        }

        return metadata;
    }

    /**
     * Returns the detected {@code WebP} format.
     *
     * @return a {@link DigitalSignature} enum constant representing this image format
     */
    @Override
    public DigitalSignature getImageFormat()
    {
        return DigitalSignature.WEBP;
    }

    /**
     * Generates a human-readable diagnostic string containing metadata details.
     *
     * <p>
     * Currently this includes EXIF directory types, entry tags, field types, counts, and values.
     * </p>
     *
     * @return a formatted string suitable for diagnostics, logging, or inspection
     */
    @Override
    public String formatDiagnosticString()
    {
        StringBuilder sb = new StringBuilder();
        MetadataStrategy<DirectoryIFD> meta = getMetadata();

        try
        {
            sb.append("\t\t\tTIF Metadata Summary").append(System.lineSeparator()).append(System.lineSeparator());
            sb.append(super.formatDiagnosticString());

            if (meta instanceof TifMetadata)
            {
                TifMetadata tif = (TifMetadata) meta;

                if (tif.hasMetadata())
                {
                    for (DirectoryIFD ifd : tif)
                    {
                        sb.append(ifd);
                    }
                }

                else
                {
                    sb.append("No EXIF metadata found").append(System.lineSeparator());
                }

                if (tif.hasXmpData())
                {
                    sb.append(tif.getXmpDirectory());
                }

                else
                {
                    sb.append("No XMP metadata found").append(System.lineSeparator());
                }

                sb.append(MetadataConstants.DIVIDER);
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