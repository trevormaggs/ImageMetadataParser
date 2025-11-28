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
import common.MetadataConstants;
import common.MetadataStrategy;
import common.SequentialByteReader;
import logger.LogFactory;
import xmp.XmpDirectory;

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
    public TifParser(String file) throws IOException
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
    public TifParser(Path fpath) throws IOException
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
     * Reads the TIFF image file to extract all supported raw metadata segments structured in a
     * series of Image File Directories (IFD), and uses the extracted data to create the metadata
     * object, making its metadata information available for retrieval.
     *
     * @return true once the metadata information is available, otherwise false
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

        catch (IOException exc)
        {
            throw new ImageReadErrorException("I/O error reading TIF file [" + getImageFile() + "]", exc);
        }

        catch (RuntimeException exc)
        {
            throw new ImageReadErrorException("Error reading TIF file [" + getImageFile() + "]", exc);
        }

        /* metadata is already guaranteed non-null */
        return metadata.hasMetadata();
    }

    /**
     * Retrieves the extracted metadata, or a safe fallback if unavailable.
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
            sb.append(super.formatDiagnosticString()).append(System.lineSeparator());

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
                    XmpDirectory cd = tif.getXmpDirectory();

                    sb.append("XMP Metadata").append(System.lineSeparator());
                    sb.append(MetadataConstants.DIVIDER).append(System.lineSeparator());
                    sb.append(cd);
                }

                else
                {
                    sb.append("No XMP metadata found").append(System.lineSeparator());
                }

                sb.append(MetadataConstants.DIVIDER);
            }
        }

        catch (RuntimeException exc)
        {
            LOGGER.error("Diagnostics failed for file [" + getImageFile() + "]", exc);
            sb.append("Error generating diagnostics: ").append(exc.getMessage()).append(System.lineSeparator());
        }

        return sb.toString();
    }
}