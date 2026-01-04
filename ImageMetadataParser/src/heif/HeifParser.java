package heif;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import com.adobe.internal.xmp.XMPException;
import batch.BatchMetadataUtils;
import common.AbstractImageParser;
import common.DigitalSignature;
import common.MetadataConstants;
import common.MetadataStrategy;
import heif.boxes.Box;
import logger.LogFactory;
import tif.DirectoryIFD;
import tif.TifMetadata;
import tif.TifParser;
import xmp.XmpHandler;

/**
 * Parses HEIF/HEIC image files and extracts embedded metadata.
 *
 * HEIF files are based on the ISO Base Media File Format (ISOBMFF). This parser extracts Exif
 * metadata by navigating the box structure defined in {@code ISO/IEC 14496-12} and
 * {@code ISO/IEC 23008-12} documents.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class HeifParser extends AbstractImageParser
{
    private static final LogFactory LOGGER = LogFactory.getLogger(HeifParser.class);
    private TifMetadata metadata;

    /**
     * Constructs an instance to parse a HEIC/HEIF file.
     *
     * @param file
     *        the image file path as a string
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    public HeifParser(String file) throws IOException
    {
        this(Paths.get(file));
    }

    /**
     * Constructs an instance to parse a HEIC/HEIF file.
     *
     * @param fpath
     *        the image file path
     *
     * @throws IOException
     *         if the file is not a regular type or does not exist
     */
    public HeifParser(Path fpath) throws IOException
    {
        super(fpath);

        LOGGER.info("Image file [" + getImageFile() + "] loaded");

        String ext = BatchMetadataUtils.getFileExtension(getImageFile());

        if (!ext.equalsIgnoreCase("heic"))
        {
            LOGGER.warn("Incorrect extension name detected in file [" + getImageFile().getFileName() + "]. Should be [heic], but found [" + ext + "]");
        }
    }

    /**
     * Logs the hierarchy of boxes at the debug level for diagnostic purposes.
     *
     * <p>
     * Each contained {@link Box} is traversed and its basic information (such as type and name) is
     * output using {@link Box#logBoxInfo()}. This provides a structured view of the box tree that
     * can assist with debugging or inspection of HEIF/ISO-BMFF files.
     * </p>
     *
     * @param handler
     *        an active IFDHandler object
     */
    public void logDebugBoxHierarchy(BoxHandler handler)
    {
        LOGGER.debug("Box hierarchy:");

        for (Box box : handler)
        {
            box.logBoxInfo();
        }
    }

    /**
     * Reads the HEIC/HEIF image file to extract all supported raw metadata segments (specifically
     * EXIF and XMP, if present), and uses the extracted data to initialise the necessary metadata
     * objects for later data retrieval.
     *
     * <p>
     * This method extracts only the Exif and/or XMP segment from the file. While other HEIF boxes
     * are parsed internally, they are not returned or exposed.
     * </p>
     *
     * @return true once at least one metadata segment has been successfully parsed, otherwise false
     *
     * @throws IOException
     *         if a file reading error occurs during the parsing
     */
    @Override
    public boolean readMetadata() throws IOException
    {
        try (BoxHandler handler = new BoxHandler(getImageFile()))
        {
            if (handler.parseMetadata())
            {
                Optional<byte[]> exif = handler.getExifData();

                if (exif.isPresent())
                {
                    metadata = TifParser.parseTiffMetadataFromBytes(exif.get());
                }

                else
                {
                    LOGGER.info("No EXIF metadata present in file [" + getImageFile() + "]");
                }

                Optional<byte[]> xmp = handler.getXmpData();

                if (xmp.isPresent())
                {
                    try
                    {
                        metadata.addXmpDirectory(XmpHandler.addXmpDirectory(xmp.get()));
                    }

                    catch (XMPException exc)
                    {
                        LOGGER.error("Unable to parse XMP payload in file [" + getImageFile() + "] due to an error", exc);
                    }
                }

                else
                {
                    LOGGER.info("No XMP metadata present in file [" + getImageFile() + "]");
                }

                logDebugBoxHierarchy(handler);
                handler.displayHierarchy();
            }

            else
            {
                metadata = new TifMetadata();
            }
        }

        return metadata.hasMetadata();
    }

    /**
     * Retrieves the extracted metadata from the HEIF image file, or a fallback if unavailable.
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

        /* metadata is already guaranteed non-null */
        return metadata;
    }

    /**
     * Returns the detected {@code HEIF} format.
     *
     * @return a {@link DigitalSignature} enum constant representing this image format
     */
    @Override
    public DigitalSignature getImageFormat()
    {
        return DigitalSignature.HEIF;
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