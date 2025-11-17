package tif;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import batch.BatchMetadataUtils;
import common.AbstractImageParser;
import common.ByteValueConverter;
import common.DigitalSignature;
import common.ImageReadErrorException;
import common.MetadataStrategy;
import common.SequentialByteReader;
import logger.LogFactory;
import tif.DirectoryIFD.EntryIFD;

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
 * big-endian "II" or little-endian "MM"), a fixed identifier (0x002A), and the offset to the first
 * IFD. The file is composed of one or more IFDs, each containing a series of tags that define the
 * image's characteristics and metadata.
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
public class TifParser3 extends AbstractImageParser
{
    private static final LogFactory LOGGER = LogFactory.getLogger(TifParser3.class);
    private MetadataStrategy<DirectoryIFD> metadata;

    /**
     * Creates an instance for parsing the specified TIFF image file.
     *
     * @param file
     *        the path to the TIFF file
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    public TifParser3(String file) throws IOException
    {
        this(Paths.get(file));
    }

    /**
     * Creates an instance intended for parsing the specified TIFF image file.
     *
     * @param fpath
     *        specifies the TIFF file path, encapsulated as a Path object
     *
     * @throws IOException
     *         if the file is not a regular type or does not exist
     */
    public TifParser3(Path fpath) throws IOException
    {
        super(fpath);

        LOGGER.info("Image file [" + getImageFile() + "] loaded");

        String ext = BatchMetadataUtils.getFileExtension(getImageFile());

        if (!ext.equalsIgnoreCase("tif") && !ext.equalsIgnoreCase("tiff"))
        {
            LOGGER.warn("Incorrect extension name detected in file [" + getImageFile().getFileName() + "]. Should be [tif], but found [" + ext + "]");
        }
    }

    /**
     * Constructs an instance used for parsing an in-memory byte array containing an entire Exif
     * segment, which is structured using Image File Directories (IFDs).
     *
     * <p>
     * <b>Usage Note:</b> This constructor is intended for parsing Exif data that has already been
     * extracted into a byte array, such as an Exif segment embedded within a JPG image file, which
     * also uses IFD structures. Also, the specified path is used for logging purposes only. The
     * source of truth is the {@code payload} array.
     * </p>
     *
     * @param fpath
     *        specifies the path to the original source file, for example: TIFF or JPG file.
     * @param payload
     *        a byte array containing the raw Exif data stream
     *
     * @throws IOException
     *         if the file is not a regular type or does not exist
     */
    public TifParser3(Path fpath, byte[] payload) throws IOException
    {
        super(fpath);

        metadata = parseFromIfdSegment(payload);
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
     * Note: This method assumes the provided byte array is a valid TIFF or EXIF payload. No
     * external validation is performed.
     * </p>
     *
     * @param payload
     *        byte array containing TIFF-formatted data
     * @return parsed metadata. If parsing fails, it guarantees the returned value is non-null
     */
    public static TifMetadata parseFromIfdSegment(byte[] payload)
    {
        TifMetadata exif = new TifMetadata();
        IFDHandler handler = new IFDHandler(new SequentialByteReader(payload));
        handler.parseMetadata();

        Optional<List<DirectoryIFD>> optionalData = handler.getDirectories();

        if (optionalData.isPresent())
        {
            for (DirectoryIFD dir : optionalData.get())
            {
                exif.addDirectory(dir);
            }
        }

        return exif;
    }

    /**
     * Reads the TIFF image file to extract all supported raw metadata segments (specifically EXIF
     * and XMP, if present), and uses the extracted data to initialise the necessary metadata
     * objects for later data retrieval.
     *
     * @return true once at least one metadata segment has been successfully parsed, otherwise false
     *
     * @throws ImageReadErrorException
     *         if a parsing or file reading error occurs
     */
    @Override
    public boolean readMetadata() throws ImageReadErrorException
    {

        try
        {
            metadata = parseFromIfdSegment(ByteValueConverter.readAllBytes(getImageFile()));
        }

        catch (Exception exc)
        {
            throw new ImageReadErrorException("Error reading TIF file [" + getImageFile() + "]", exc);
        }

        /* metadata is already guaranteed non-null */
        return metadata.hasMetadata();
    }

    /**
     * Retrieves the extracted metadata, or a fallback if unavailable.
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
            sb.append(System.lineSeparator());

            if (meta instanceof TifMetadataStrategy)
            {
                TifMetadataStrategy tif = (TifMetadataStrategy) meta;

                if (tif.hasMetadata())
                {
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

                if (tif.hasXmpData())
                {
                    // **ACTION REQUIRED: Insert code to format and append XMP data here**
                    // Example: sb.append(tif.formatXmpDiagnosticString());
                    sb.append("XMP data found (requires separate formatting method)").append(System.lineSeparator());
                }

                else
                {
                    sb.append("No XMP metadata found").append(System.lineSeparator());
                }

                sb.append(DIVIDER);
            }
        }

        catch (Exception exc)
        {
            sb.append("Error generating diagnostics: ").append(exc.getMessage()).append(System.lineSeparator());
            LOGGER.error("Diagnostics failed for file [" + getImageFile() + "]", exc);
        }

        return sb.toString();
    }
}