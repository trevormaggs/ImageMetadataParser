package heif;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import batch.BatchMetadataUtils;
import common.AbstractImageParser;
import common.ByteStreamReader;
import common.ByteValueConverter;
import common.DigitalSignature;
import common.MetadataConstants;
import common.MetadataStrategy;
import common.SequentialByteArrayReader;
import heif.boxes.Box;
import logger.LogFactory;
import tif.DirectoryIFD;
import tif.DirectoryIFD.EntryIFD;
import tif.TifMetadata;
import tif.TifMetadataStrategy;
import tif.TifParser;

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
    public static final ByteOrder HEIF_BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private MetadataStrategy<DirectoryIFD> metadata;
    private BoxHandler handler;

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
        Optional<byte[]> exif;
        byte[] bytes = Objects.requireNonNull(ByteValueConverter.readAllBytes(getImageFile()), "Input bytes are null");

        // Use big-endian byte order as per ISO/IEC 14496-12
        ByteStreamReader heifReader = new SequentialByteArrayReader(bytes, HEIF_BYTE_ORDER);

        handler = new BoxHandler(getImageFile(), heifReader);
        handler.parseMetadata();

        exif = handler.getExifData();

        if (exif.isPresent())
        {
            metadata = TifParser.parseTiffMetadataFromBytes(exif.get());
        }

        else
        {
            LOGGER.info("No EXIF metadata present in file [" + getImageFile() + "]");
        }

        // handler.displayHierarchy();
        // logDebugBoxHierarchy();

        return exif.isPresent();
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
        MetadataStrategy<?> meta = getMetadata();
        StringBuilder sb = new StringBuilder();

        try
        {
            sb.append("\t\t\tHEIF Metadata Summary").append(System.lineSeparator()).append(System.lineSeparator());
            sb.append(super.formatDiagnosticString());

            if (meta instanceof TifMetadataStrategy && ((TifMetadataStrategy) meta).hasExifData())
            {
                TifMetadataStrategy tif = (TifMetadataStrategy) meta;

                for (DirectoryIFD ifd : tif)
                {
                    sb.append("Directory Type - ")
                            .append(ifd.getDirectoryType().getDescription())
                            .append(String.format(" (%d entries)%n", ifd.size()))
                            .append(MetadataConstants.DIVIDER)
                            .append(System.lineSeparator());

                    for (EntryIFD entry : ifd)
                    {
                        String value = ifd.getString(entry.getTag());

                        sb.append(String.format(MetadataConstants.FORMATTER, "Tag Name", entry.getTag() + " (Tag ID: " + String.format("0x%04X", entry.getTagID()) + ")"));
                        sb.append(String.format(MetadataConstants.FORMATTER, "Field Type", entry.getFieldType() + " (count: " + entry.getCount() + ")"));
                        sb.append(String.format(MetadataConstants.FORMATTER, "Value", (value == null || value.isEmpty() ? "Empty" : value)));
                        sb.append(System.lineSeparator());
                    }
                }
            }

            else
            {
                sb.append("No EXIF metadata found").append(System.lineSeparator());
            }
        }

        catch (Exception exc)
        {
            sb.append("Error generating diagnostics: ").append(exc.getMessage()).append(System.lineSeparator());
            LOGGER.error("Diagnostics failed for file [" + getImageFile() + "]", exc);
        }

        return sb.toString();
    }

    /**
     * Logs the hierarchy of boxes at the debug level for diagnostic purposes.
     *
     * <p>
     * Each contained {@link Box} is traversed and its basic information (such as type and name) is
     * output using {@link Box#logBoxInfo()}. This provides a structured view of the box tree that
     * can assist with debugging or inspection of HEIF/ISO-BMFF files.
     * </p>
     */
    public void logDebugBoxHierarchy()
    {
        LOGGER.debug("Box hierarchy:");

        for (Box box : handler)
        {
            box.logBoxInfo();
        }
    }
}