package tif;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import com.adobe.internal.xmp.XMPException;
import common.AbstractImageParser;
import common.DigitalSignature;
import common.Metadata;
import common.MetadataConstants;
import common.SequentialByteArrayReader;
import common.Utils;
import logger.LogFactory;
import tif.tagspecs.TagIFD_Baseline;
import xmp.XmpDirectory;
import xmp.XmpHandler;

/**
 * This program aims to read TIF image files and retrieve data structured in a series of Image File
 * Directories (IFDs).
 *
 * <p>
 * <b>TIF Data Stream</b>
 * </p>
 *
 * <p>
 * The TIF data stream begins with an 8-byte header, which specifies the byte order (either
 * big-endian {@code II} or little-endian {@code MM}), a fixed identifier (0x002A), and the offset
 * to the first IFD. The file is composed of one or more IFDs, each containing a series of tags that
 * define the image's characteristics and metadata.
 * </p>
 *
 * <p>
 * The TIFF specification is extensible, allowing for custom tags and nested sub-directories, such
 * as the {@code EXIF (Exchangeable Image File Format)}, which is a common sub-directory used in
 * both TIFF and JPEG files.
 * </p>
 *
 * @see <a href="https://www.itu.int/itudoc/itu-t/com16/tiff-fx/docs/tiff6.pdf">TIFF 6.0
 *      Specification</a>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class TifParser extends AbstractImageParser
{
    private static final LogFactory LOGGER = LogFactory.getLogger(TifParser.class);
    private TifMetadata metadata;

    /**
     * Creates an instance for parsing the specified TIFF image file.
     *
     * @param file
     *        the path to the TIFF file
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    public TifParser(String file) throws IOException
    {
        this(Paths.get(file));
    }

    /**
     * Creates an instance intended for parsing the specified TIFF image file.
     *
     * @param fpath
     *        the path to the TIFF file
     *
     * @throws IOException
     *         if the file is not a regular type or does not exist
     */
    public TifParser(Path fpath) throws IOException
    {
        super(fpath);

        LOGGER.info("Image file [" + getImageFile() + "] loaded");

        String ext = Utils.getFileExtension(getImageFile());

        if (!ext.equalsIgnoreCase("tif") && !ext.equalsIgnoreCase("tiff"))
        {
            LOGGER.warn("Incorrect extension name detected in file [" + getImageFile().getFileName() + "]. Should be [tif], but found [" + ext + "]");
        }
    }

    /**
     * Parses TIFF metadata from a byte array, providing information on extracted IFD directories.
     *
     * <p>
     * For efficiency, use this static utility method where TIFF-formatted data, such as an embedded
     * EXIF segment, is already available in memory. It directly processes the byte array to extract
     * and structure the metadata directories without having to read a file from disk again.
     * </p>
     *
     * <p>
     * Note: This method assumes the provided byte array is a valid TIFF or EXIF payload, including
     * the 8-byte header length. No external validation is performed.
     * </p>
     *
     * @param payload
     *        byte array containing TIFF-formatted data
     * @return parsed metadata. If parsing fails, it guarantees an empty (but non-null)
     *         {@link TifMetadata} object is returned
     */
    public static TifMetadata parseTiffMetadataFromBytes(byte[] payload)
    {
        try (IFDHandler handler = new IFDHandler(new SequentialByteArrayReader(payload));)
        {
            if (handler.parseMetadata())
            {
                TifMetadata tif = new TifMetadata(handler.getTifByteOrder());

                for (DirectoryIFD ifd : handler.getDirectories())
                {
                    tif.addDirectory(ifd);
                }

                return tif;
            }

            LOGGER.warn("IFD segment parsing failed. Fallback to an empty TifMetadata");
        }

        catch (IOException exc)
        {
            LOGGER.error("Data corruption detected in byte array. [" + exc.getMessage() + "]");
        }

        return new TifMetadata();
    }

    /**
     * Orchestrates the extraction of TIFF metadata by parsing all Image File Directories (IFDs)
     * within the file.
     * *
     * <p>
     * This method converts raw IFD entries into a structured {@link TifMetadata} object. Per
     * metadata standards, if multiple XMP blocks exist, the final instance is <strong>given
     * precedence</strong>. To implement this <strong>last-one-wins</strong> strategy efficiently,
     * the parser searches directories in reverse order and stops at the first
     * {@code IFD_XML_PACKET} (Tag 0x02BC) it finds.
     * </p>
     *
     * @return {@code true} if at least one Image File Directory was successfully parsed and the
     *         metadata object is populated; {@code false} otherwise.
     *
     * @throws IOException
     *         if a low-level I/O error or data corruption is detected during random-access reading.
     */
    @Override
    public boolean readMetadata() throws IOException
    {
        try (IFDHandler handler = new IFDHandler(getImageFile()))
        {
            if (handler.parseMetadata())
            {
                List<DirectoryIFD> dirList = handler.getDirectories();
                metadata = new TifMetadata(handler.getTifByteOrder());

                // Traverse in reverse to honour the "last-one-wins" XMP strategy
                for (int i = dirList.size() - 1; i >= 0; i--)
                {
                    DirectoryIFD dir = dirList.get(i);

                    metadata.addDirectory(dir);

                    if (!metadata.hasXmpData() && dir.hasTag(TagIFD_Baseline.IFD_XML_PACKET))
                    {
                        byte[] rawXmp = dir.getRawByteArray(TagIFD_Baseline.IFD_XML_PACKET);

                        try
                        {
                            XmpDirectory xmpDir = XmpHandler.addXmpDirectory(rawXmp);
                            metadata.addXmpDirectory(xmpDir);
                        }

                        catch (XMPException exc)
                        {
                            LOGGER.error("Unable to parse XMP directory payload", exc);
                        }
                    }
                }

                if (!metadata.hasXmpData())
                {
                    LOGGER.debug("No XMP payload found");
                }
            }

            else
            {
                metadata = new TifMetadata();
                LOGGER.warn("IFD segment parsing failed. Fallback to an empty TifMetadata");
            }
        }

        catch (IOException exc)
        {
            LOGGER.error("Data corruption or I/O error detected", exc);
            throw exc;
        }

        /* metadata is already guaranteed non-null */
        return metadata.hasMetadata();
    }
    /**
     * Retrieves the extracted metadata, or a safe fallback if unavailable.
     *
     * @return a {@link TifMetadata} object containing the extracted IFDs, or a new empty
     *         {@link TifMetadata} object if parsing has not occurred
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
     * Returns the detected TIFF format.
     *
     * @return a {@link DigitalSignature} enum class
     */
    @Override
    public DigitalSignature getImageFormat()
    {
        return DigitalSignature.TIF;
    }

    /**
     * Generates a human-readable diagnostic string containing metadata segment details.
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
                    sb.append("No TIFF metadata found").append(System.lineSeparator());
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