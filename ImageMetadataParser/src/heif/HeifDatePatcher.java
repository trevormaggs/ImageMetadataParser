package heif;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import common.ImageRandomAccessWriter;
import common.Utils;
import heif.BoxHandler.MetadataType;
import logger.LogFactory;
import tif.DirectoryIFD;
import tif.DirectoryIFD.EntryIFD;
import tif.TifMetadata;
import tif.TifParser;
import tif.tagspecs.TagIFD_Baseline;
import tif.tagspecs.TagIFD_Exif;
import tif.tagspecs.TagIFD_GPS;
import tif.tagspecs.Taggable;

/**
 * Provides utility methods for performing "in-place" binary patching of date-related metadata
 * within HEIF/HEIC files.
 *
 * <p>
 * This class enables surgical modification of Exif and XMP date tags without rewriting the entire
 * file or altering the HEIF box structure. It relies on {@link BoxHandler#getPhysicalAddress} to
 * resolve logical offsets into absolute file positions.
 * </p>
 * *
 * <p>
 * <strong>Warning:</strong> This utility modifies the target file directly. It is highly
 * recommended to back up files before processing.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.2
 */
public final class HeifDatePatcher
{
    private static final LogFactory LOGGER = LogFactory.getLogger(HeifDatePatcher.class);
    private static final Map<Taggable, DateTimeFormatter> EXIF_TAG_FORMATS;
    private static final DateTimeFormatter EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter GPS_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ENGLISH);

    static
    {
        EXIF_TAG_FORMATS = new HashMap<>();
        EXIF_TAG_FORMATS.put(TagIFD_Baseline.IFD_DATE_TIME, EXIF_FORMATTER);
        EXIF_TAG_FORMATS.put(TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL, EXIF_FORMATTER);
        EXIF_TAG_FORMATS.put(TagIFD_Exif.EXIF_DATE_TIME_DIGITIZED, EXIF_FORMATTER);
        EXIF_TAG_FORMATS.put(TagIFD_GPS.GPS_DATE_STAMP, GPS_FORMATTER);
    }

    /**
     * Default constructor is unsupported and will always throw an exception.
     *
     * @throws UnsupportedOperationException
     *         to indicate that instantiation is not supported
     */
    private HeifDatePatcher()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

    /**
     * Updates all identified date tags in both Exif and XMP metadata segments to a specified date.
     * 
     * <p>
     * <b>Note:</b> This operation is size-constrained. If the existing metadata slots are shorter
     * than the formatted new date, the value will be truncated to fit.
     * </p>
     * 
     * @param imagePath
     *        the {@link Path} to the HEIF/HEIC file to be modified
     * @param newDate
     *        the new timestamp to apply to all metadata fields
     * @param xmpDump
     *        indicates whether to dump XMP data into an XML-formatted file for debugging. If true,
     *        a file is created based on the image name
     *
     * @throws IOException
     *         if the file cannot be read, parsed, or written to
     */
    public static void patchAllDates(Path imagePath, FileTime newDate, boolean xmpDump) throws IOException
    {
        ZonedDateTime zdt = newDate.toInstant().atZone(ZoneId.systemDefault());

        try (BoxHandler handler = new BoxHandler(imagePath))
        {
            if (handler.parseMetadata())
            {
                try (ImageRandomAccessWriter writer = new ImageRandomAccessWriter(imagePath, BoxHandler.HEIF_BYTE_ORDER))
                {
                    processExifSegment(handler, writer, zdt);
                    processXmpSegment(handler, writer, zdt, imagePath, xmpDump);
                }
            }
        }
    }

    /**
     * Iterates through the Exif TIFF structure and patches date tags.
     *
     * <p>
     * Maintains null-terminator integrity and ensures that the new data does not exceed the
     * original tag's byte count.
     * </p>
     *
     * @param handler
     *        the parsed {@link BoxHandler} providing item locations
     * @param raf
     *        the open {@link RandomAccessFile}
     * @param zdt
     *        the timestamp to be formatted
     *
     * @throws IOException
     *         if a write error occurs
     */
    private static void processExifSegment(BoxHandler handler, ImageRandomAccessWriter raf, ZonedDateTime zdt) throws IOException
    {
        Optional<byte[]> exifData = handler.getExifData();
        int exifId = handler.findMetadataID(MetadataType.EXIF);

        if (exifId != -1 && exifData.isPresent())
        {
            TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(exifData.get());

            for (DirectoryIFD dir : metadata)
            {
                for (Map.Entry<Taggable, DateTimeFormatter> formatEntry : EXIF_TAG_FORMATS.entrySet())
                {
                    Taggable tag = formatEntry.getKey();

                    if (dir.hasTag(tag))
                    {
                        EntryIFD entry = dir.getTagEntry(tag);
                        long physicalPos = handler.getPhysicalAddress(exifId, entry.getOffset(), MetadataType.EXIF);

                        if (physicalPos != -1)
                        {
                            String value = zdt.format(formatEntry.getValue());
                            byte[] dateBytes = (value + "\0").getBytes(StandardCharsets.US_ASCII);

                            int limit = (int) entry.getCount();
                            byte[] output = new byte[limit];

                            System.arraycopy(dateBytes, 0, output, 0, Math.min(dateBytes.length, limit));
                            output[limit - 1] = 0; // Force null termination

                            raf.seek(physicalPos);
                            raf.writeBytes(output);
                        }
                    }
                }
            }
        }
    }

    /**
     * Searches for XMP date tags and surgically patches with the specified date in-place embedded
     * in a HEIF file using the existing {@code iloc} extent as a fixed-width binary slot.
     * 
     * <p>
     * This method utilises atomic span discovery to distinguish between XML elements and
     * attributes. It includes a safety check to ignore closing tags and enforces a minimum width
     * threshold to prevent corrupting short or partial matches.
     * </p>
     *
     * @param handler
     *        the parsed {@link BoxHandler} providing item locations
     * @param writer
     *        the open {@link ImageRandomAccessWriter} resource
     * @param zdt
     *        the new date and time to apply
     * @param xmpDump
     *        enables the dumping of the XML content into a simple file for inspection purposes
     *
     * @throws IOException
     *         if the file is inaccessible or the patch exceeds slot width
     */
    private static void processXmpSegment(BoxHandler handler, ImageRandomAccessWriter writer, ZonedDateTime zdt, Path fpath, boolean xmpDump) throws IOException
    {
        String[] xmpTags = {
                "xmp:CreateDate", "xap:CreateDate", "xmp:ModifyDate", "xap:ModifyDate",
                "xmp:MetadataDate", "xap:MetadataDate", "photoshop:DateCreated",
                "exif:DateTimeOriginal", "exif:DateTimeDigitized", "tiff:DateTime"
        };

        Optional<byte[]> xmpData = handler.getXmpData();
        int xmpId = handler.findMetadataID(MetadataType.XMP);

        if (xmpId != -1 && xmpData.isPresent())
        {
            String content = new String(xmpData.get(), StandardCharsets.UTF_8);

            for (String tag : xmpTags)
            {
                int tagIdx = content.indexOf(tag);

                while (tagIdx != -1)
                {
                    // Filter out closing tags (</tag>)
                    if (!(tagIdx > 0 && content.charAt(tagIdx - 1) == '/'))
                    {
                        int[] span = Utils.findValueSpan(content, tagIdx);

                        if (span != null)
                        {
                            int start = span[0];
                            int width = span[1];

                            String alignedPatch = Utils.alignXmpValueSlot(zdt, width);

                            if (alignedPatch != null)
                            {
                                // Calculate physical address from logical byte offset

                                /*
                                 * Locates the exact character index after taking multi-byte
                                 * characters into account, i.e. emojis or non-Latin text. This
                                 * prevents positional drift in the XML. Byte Order Mark is one good
                                 * example.
                                 */
                                int byteOffset = content.substring(0, start).getBytes(StandardCharsets.UTF_8).length;
                                long physicalPos = handler.getPhysicalAddress(xmpId, byteOffset, MetadataType.XMP);

                                if (physicalPos != -1)
                                {
                                    writer.seek(physicalPos);
                                    writer.writeBytes(alignedPatch.getBytes(StandardCharsets.UTF_8));

                                    LOGGER.debug("Patched XMP tag [" + tag + "] at: " + physicalPos);
                                }
                            }

                            else
                            {
                                LOGGER.error(String.format("Skipped XMP tag [%s] due to insufficient slot width [%d]", tag, width));
                            }
                        }
                    }

                    tagIdx = content.indexOf(tag, tagIdx + tag.length());
                }
            }

            if (xmpDump)
            {
                Utils.printFastDumpXML(fpath, xmpData.get());
            }
        }
    }
}