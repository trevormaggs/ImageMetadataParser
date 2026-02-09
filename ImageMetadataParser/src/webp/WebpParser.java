package webp;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Optional;
import com.adobe.internal.xmp.XMPException;
import common.AbstractImageParser;
import common.DigitalSignature;
import common.MetadataConstants;
import common.Metadata;
import common.Utils;
import jpg.JpgParser;
import logger.LogFactory;
import tif.DirectoryIFD;
import tif.TifMetadata;
import tif.TifParser;
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

        String ext = Utils.getFileExtension(getImageFile());

        if (!ext.equalsIgnoreCase("webp"))
        {
            LOGGER.warn("Incorrect extension name detected in file [" + getImageFile().getFileName() + "]. Should be [webp], but found [" + ext + "]");
        }
    }

    /**
     * Reads the WebP image file to extract all supported raw metadata segments.
     * 
     * <p>
     * This implementation specifically looks for {@code EXIF} and {@code XMP} chunks. Because some
     * encoders incorrectly wrap the EXIF payload in a JPEG-style preamble (the "Exif\0\0" marker),
     * this method performs a stripping operation to ensure the underlying TIFF parser receives a
     * valid byte stream.
     * </p>
     *
     * @return true if at least one metadata segment (EXIF or XMP) was successfully parsed
     * 
     * @throws IOException
     *         if a low-level I/O error occurs during stream reading
     */
    @Override
    public boolean readMetadata() throws IOException
    {
        metadata = new TifMetadata();

        try (WebpHandler handler = new WebpHandler(getImageFile(), DEFAULT_METADATA_CHUNKS))
        {
            if (handler.parseMetadata())
            {
                if (handler.existsExifMetadata())
                {
                    Optional<WebpChunk> optExif = handler.getChunk(WebPChunkType.EXIF);

                    if (optExif.isPresent())
                    {
                        /*
                         * According to research from other sources, it seems sometimes the WebP
                         * files
                         * happen to contain the JPG premable within the TIFF header block for some
                         * strange reasons, the snippet below makes sure the JPEG segment is
                         * skipped.
                         */
                        byte[] strippedPayload = JpgParser.stripExifPreamble(optExif.get().getPayloadArray());

                        metadata = TifParser.parseTiffMetadataFromBytes(strippedPayload);
                    }

                    else
                    {
                        LOGGER.debug("No Exif segment found in file [" + getImageFile() + "]");
                    }
                }

                if (handler.existsXmpMetadata())
                {
                    Optional<WebpChunk> optXmp = handler.getLastChunk(WebPChunkType.XMP);

                    if (optXmp.isPresent())
                    {
                        try
                        {
                            metadata.addXmpDirectory(XmpHandler.addXmpDirectory(optXmp.get().getPayloadArray()));
                        }

                        catch (XMPException exc)
                        {
                            LOGGER.error("Unable to parse XMP payload", exc);
                        }
                    }

                    else
                    {
                        LOGGER.debug("No XMP payload found in file [" + getImageFile() + "]");
                    }
                }
            }
        }

        return metadata.hasMetadata();
    }

    /**
     * Retrieves the extracted metadata from the WebP image file, or a fallback if unavailable.
     *
     * @return a {@link Metadata} object
     */
    @Override
    public Metadata<DirectoryIFD> getMetadata()
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
        Metadata<DirectoryIFD> meta = getMetadata();

        try
        {
            sb.append("\t\t\tWebP Metadata Summary").append(System.lineSeparator()).append(System.lineSeparator());
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