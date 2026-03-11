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
import common.Utils;
import logger.LogFactory;
import tif.tagspecs.TagIFD_Extension;
import xmp.XmpDirectory;
import xmp.XmpHandler;

/**
 * A concrete implementation of {@link AbstractImageParser} for extracting metadata from TIFF files.
 *
 * <p>
 * This parser interprets the 8-byte TIFF header, including byte order (either big-endian {@code MM}
 * or little-endian {@code II}), magic number 0x002A, and initial IFD offset) and traverses the
 * linked list of Image File Directories (IFDs). It supports standard tags, custom extensions, and
 * nested sub-directories such as EXIF.
 * </p>
 * *
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

        String ext = Utils.getFileExtension(getImageFile());

        if (!ext.equalsIgnoreCase("tif") && !ext.equalsIgnoreCase("tiff"))
        {
            String filename = getImageFile().getFileName().toString();
            LOGGER.warn(String.format("Mismatched magic numbers detected in file [%s]. Should be [%s] (TIF extension)", filename, filename.replaceAll("(.*\\.)\\w+", "$1tif")));
        }
    }

    /**
     * Parses TIFF metadata from a byte array, assuming it is a valid TIFF or EXIF payload,
     * including the 8-byte header length.
     *
     * <p>
     * Optimised for cases where TIFF data, such as an embedded EXIF segment, is already available
     * in memory. This method avoids redundant disk I/O.
     * </p>
     *
     * @param payload
     *        the byte array containing TIFF-formatted data
     * @return a {@link TifMetadata} object, it guarantees non-null even if parsing fails
     */
    public static TifMetadata parseTiffMetadataFromBytes(byte[] payload)
    {
        try (IFDHandler handler = new IFDHandler(payload))
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
     * Parses TIFF and XMP metadata from the image file.
     *
     * <p>
     * Performs a reverse traversal of IFDs to honour the "last-one-wins" XMP strategy,
     * ensuring the most recent {@code IFD_XML_PACKET} takes precedence.
     * </p>
     *
     * @return {@code true} if metadata was successfully populated
     * 
     * @throws IOException
     *         if data corruption or I/O errors occur during traversal
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

                    if (!metadata.hasXmpData() && dir.hasTag(TagIFD_Extension.IFD_XML_PACKET))
                    {
                        byte[] rawXmp = dir.getRawByteArray(TagIFD_Extension.IFD_XML_PACKET);

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