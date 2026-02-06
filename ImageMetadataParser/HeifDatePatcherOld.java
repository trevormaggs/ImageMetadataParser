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
    private static final DateTimeFormatter XMP_LONG = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter XMP_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

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
     * @param path
     *        the {@link Path} to the HEIF/HEIC file to be modified
     * @param newDate
     *        the new timestamp to apply to all metadata fields
     * 
     * @throws IOException
     *         if the file cannot be read, parsed, or written to
     */
    public static void patchAllDates(Path path, FileTime newDate) throws IOException
    {
        ZonedDateTime zdt = newDate.toInstant().atZone(ZoneId.systemDefault());

        try (BoxHandler handler = new BoxHandler(path))
        {
            if (handler.parseMetadata())
            {
                try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw"))
                {
                    patchExif(handler, raf, zdt);
                    patchXmp(handler, raf, zdt);
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
    private static void patchExif(BoxHandler handler, RandomAccessFile raf, ZonedDateTime zdt) throws IOException
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
                            raf.write(output);
                        }
                    }
                }
            }
        }
    }

    /**
     * Searches for XMP date tags and performs an inline string replacement.
     *
     * <p>
     * This method utilises atomic span discovery to distinguish between XML elements and
     * attributes. It includes a safety check to ignore closing tags and enforces a minimum width
     * threshold to prevent corrupting short or partial matches.
     * </p>
     * 
     * @param handler
     *        the parsed {@link BoxHandler} providing item locations
     * @param raf
     *        the open {@link RandomAccessFile} resource
     * @param zdt
     *        the timestamp to be formatted
     * 
     * @throws IOException
     *         if a write error occurs
     */
    private static void patchXmp(BoxHandler handler, RandomAccessFile raf, ZonedDateTime zdt) throws IOException
    {
        Optional<byte[]> xmpData = handler.getXmpData();
        int xmpId = handler.findMetadataID(MetadataType.XMP);
        String[] tags = {"xmp:CreateDate", "xmp:ModifyDate", "xmp:MetadataDate"};

        if (xmpId != -1 && xmpData.isPresent())
        {
            String content = new String(xmpData.get(), StandardCharsets.UTF_8);

            for (String tag : tags)
            {
                int tagIdx = content.indexOf(tag);

                while (tagIdx != -1)
                {
                    // Filter out closing tags (e.g., </xmp:CreateDate>) to avoid invalid matches
                    boolean isClosingTag = (tagIdx > 0 && content.charAt(tagIdx - 1) == '/');

                    if (!isClosingTag)
                    {
                        int[] span = findValueSpan(content, tagIdx);

                        if (span != null)
                        {
                            int start = span[0];
                            int width = span[1];

                            /*
                             * Threshold: ISO 8601 strings (YYYY-MM-DD)
                             * require at least 10 characters.
                             */
                            if (width >= 10)
                            {
                                String patch = (width >= 25) ? zdt.format(XMP_LONG) : zdt.format(XMP_SHORT);
                                byte[] finalPatch = String.format("%-" + width + "s", patch).substring(0, width).getBytes(StandardCharsets.UTF_8);

                                long physicalPos = handler.getPhysicalAddress(xmpId, start, MetadataType.XMP);

                                if (physicalPos != -1)
                                {
                                    raf.seek(physicalPos);
                                    raf.write(finalPatch);
                                    LOGGER.debug("Patched XMP tag [" + tag + "] at physical offset: " + physicalPos);
                                }
                            }
                        }
                    }

                    tagIdx = content.indexOf(tag, tagIdx + tag.length());
                }
            }
        }
    }

    /**
     * Consolidates XMP value discovery into a single atomic operation by identifying value
     * boundaries for both XML elements and attributes.
     * *
     * <p>
     * If a quote (") appears before a bracket (>), the value is treated as an attribute. Otherwise,
     * it is treated as an element value (stopping at the start of a closing tag).
     * </p>
     * 
     * @param content
     *        the raw XML/XMP string
     * @param tagIdx
     *        The starting index of the property name
     * @return an int array where [0] is the logical start index and [1] is the byte width, or
     *         {@code null} if valid boundaries cannot be resolved
     */
    private static int[] findValueSpan(String content, int tagIdx)
    {
        int bracket = content.indexOf(">", tagIdx);
        int quote = content.indexOf("\"", tagIdx);
        boolean isAttr = (quote != -1 && (bracket == -1 || quote < bracket));

        int start = isAttr ? quote + 1 : bracket + 1;
        int end = content.indexOf(isAttr ? "\"" : "<", start);

        return (start > 0 && end > start) ? new int[]{start, end - start} : null;
    }
}