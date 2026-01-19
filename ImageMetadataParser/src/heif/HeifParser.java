package heif;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import com.adobe.internal.xmp.XMPException;
import common.AbstractImageParser;
import common.DigitalSignature;
import common.MetadataConstants;
import common.Metadata;
import common.Utils;
import heif.boxes.Box;
import logger.LogFactory;
import tif.DirectoryIFD;
import tif.TifMetadata;
import tif.TifParser;
import tif.tagspecs.TagIFD_Baseline;
import tif.tagspecs.TagIFD_Exif;
import tif.tagspecs.Taggable;
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

        String ext = Utils.getFileExtension(getImageFile());

        if (!ext.equalsIgnoreCase("heic") && !ext.equalsIgnoreCase("heif") && !ext.equalsIgnoreCase("hif"))
        {
            LOGGER.warn("Ambiguous HEIF extension [" + ext + "]");
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
        metadata = new TifMetadata();

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

                // logDebugBoxHierarchy(handler);
                // handler.displayHierarchy();
                // updateExifDate(getImageFile(), Paths.get("IMG_0830_TestDate.heic"), "");
            }
        }

        return metadata.hasMetadata();
    }

    /**
     * Retrieves the extracted metadata from the HEIF image file, or a fallback if unavailable.
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

    public static void updateExifDate(Path sourcePath, Path destinationPath, FileTime newDate) throws IOException
    {
        Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

        Taggable[] targetTags = {
                TagIFD_Baseline.IFD_DATE_TIME,
                TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_DATE_TIME_DIGITIZED};

        String formattedDate = newDate.toInstant().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH));

        try (BoxHandler handler = new BoxHandler(destinationPath))
        {
            if (handler.parseMetadata())
            {
                int exifId = handler.findMetadataID(BoxHandler.MetadataType.EXIF);

                if (exifId != -1)
                {
                    Optional<byte[]> opt = handler.getExifData();

                    if (opt.isPresent())
                    {
                        try (RandomAccessFile raf = new RandomAccessFile(destinationPath.toFile(), "rw"))
                        {
                            TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(opt.get());

                            byte[] dateBytes = (formattedDate + "\0").getBytes(StandardCharsets.US_ASCII);

                            for (DirectoryIFD dir : metadata)
                            {
                                for (Taggable tag : targetTags)
                                {
                                    if (dir.hasTag(tag))
                                    {
                                        long physicalPos = handler.getPhysicalAddress(exifId, dir.getTagEntry(tag).getOffset());

                                        if (physicalPos != -1)
                                        {
                                            if (isSafeToOverwrite(raf, physicalPos))
                                            {
                                                try
                                                {
                                                    raf.seek(physicalPos);
                                                    raf.write(dateBytes);
                                                    System.out.println("Successfully patched " + tag + " at " + physicalPos);
                                                }

                                                catch (IOException exc)
                                                {
                                                    LOGGER.error("Failed to patch tag [" + tag + "] at offset [" + physicalPos + "]", exc);
                                                }
                                            }

                                            else
                                            {
                                                System.err.println("Safety check failed for " + tag + ". Offset " + physicalPos + " does not look like a date.");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Verifies that the target physical offset in the file looks like an existing TIFF date string
     * (e.g., starts with '20' and has a ':' at index 4).
     */
    private static boolean isSafeToOverwrite(RandomAccessFile raf, long physicalPos) throws IOException
    {
        if (physicalPos < 0)
        {
            return false;
        }

        byte[] buffer = new byte[5];

        try
        {
            raf.seek(physicalPos);
            raf.readFully(buffer);
        }

        catch (EOFException exc)
        {
            return false;
        }

        // Check for common date patterns: "20xx:" or "19xx:"
        boolean isYearPrefix = (buffer[0] == '2' || buffer[0] == '1') && Character.isDigit(buffer[1]);
        boolean hasColon = (buffer[4] == ':');

        return isYearPrefix && hasColon;
    }
}