package heif;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import batch.BatchMetadataUtils;
import common.AbstractImageParser;
import common.DigitalSignature;
import common.ImageReadErrorException;
import common.MetadataStrategy;
import common.SequentialByteReader;
import heif.boxes.Box;
import logger.LogFactory;
import tif.DirectoryIFD;
import tif.DirectoryIFD.EntryIFD;
import tif.ExifMetadata;
import tif.ExifStrategy;
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
     * @param fpath
     *        the image file path
     *
     * @throws IOException
     *         if an I/O error occurs
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
     * Reads and processes Exif metadata from the HEIC/HEIF file.
     *
     * <p>
     * This method extracts only the Exif segment from the file. While other HEIF boxes are parsed
     * internally, they are not returned or exposed.
     * </p>
     *
     * @return the extracted Exif metadata wrapped in a {@link MetadataStrategy} object, if no Exif
     *         data is found, returns an empty metadata instance
     * @throws ImageReadErrorException
     *         in case of processing errors
     */
    @Override
    // public MetadataStrategy<?> readMetadata() throws ImageReadErrorException
    public boolean readMetadata() throws ImageReadErrorException
    {
        Optional<byte[]> exif;

        try
        {
            byte[] bytes = Objects.requireNonNull(readAllBytes(), "Input bytes are null");

            // Use big-endian byte order as per ISO/IEC 14496-12
            SequentialByteReader heifReader = new SequentialByteReader(bytes, HEIF_BYTE_ORDER);

            handler = new BoxHandler(getImageFile(), heifReader);
            handler.parseMetadata();

            exif = handler.getExifData();

            if (exif.isPresent())
            {
                metadata = TifParser.parseFromExifSegment(exif.get());
            }

            else
            {
                LOGGER.info("No EXIF metadata present in file [" + getImageFile() + "]");
            }
        }

        catch (IOException exc)
        {
            throw new ImageReadErrorException("Failed to read HEIF file [" + getImageFile() + "]", exc);
        }

        // handler.displayHierarchy();
        // logDebugBoxHierarchy();
        // return getExifInfo();

        return exif.isPresent();
    }

    /**
     * Retrieves the extracted metadata from the HEIF image file, or a fallback if unavailable.
     *
     * @return a {@link MetadataStrategy} object
     */
    @Override
    public MetadataStrategy<DirectoryIFD> getExifInfo()
    {
        if (metadata == null)
        {
            LOGGER.warn("No metadata information has been parsed yet");

            /* Fallback to empty metadata */
            return new ExifMetadata();
        }

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
        MetadataStrategy<?> meta = getExifInfo();
        StringBuilder sb = new StringBuilder();

        try
        {
            sb.append("\t\t\tHEIF Metadata Summary").append(System.lineSeparator()).append(System.lineSeparator());
            sb.append(super.formatDiagnosticString());

            if (meta instanceof ExifStrategy && ((ExifStrategy) meta).hasExifData())
            {
                ExifStrategy tif = (ExifStrategy) meta;

                for (DirectoryIFD ifd : tif)
                {
                    sb.append("Directory Type - ")
                            .append(ifd.getDirectoryType().getDescription())
                            .append(String.format(" (%d entries)%n", ifd.size()))
                            .append(DIVIDER)
                            .append(System.lineSeparator());

                    for (EntryIFD entry : ifd)
                    {
                        String value = ifd.getString(entry.getTag());

                        sb.append(String.format(FMT, "Tag Name", entry.getTag() + " (Tag ID: " + String.format("0x%04X", entry.getTagID()) + ")"));
                        sb.append(String.format(FMT, "Field Type", entry.getFieldType() + " (count: " + entry.getCount() + ")"));
                        sb.append(String.format(FMT, "Value", (value == null || value.isEmpty() ? "Empty" : value)));
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

    @Override
    public MetadataStrategy<?> getXmpInfo()
    {
        return null;
    }
}