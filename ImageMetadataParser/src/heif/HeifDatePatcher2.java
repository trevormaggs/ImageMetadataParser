package heif;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * 
 * <p>
 * <strong>Warning:</strong> This utility modifies the target file directly. It is highly
 * recommended to back up files before processing.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.1
 */
public final class HeifDatePatcher2
{
    private static final LogFactory LOGGER = LogFactory.getLogger(HeifDatePatcher2.class);
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
    private HeifDatePatcher2()
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
    public static void updateAllMetadataDates(Path path, FileTime newDate) throws IOException
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
     * To maintain the exact file size and box structure, this method uses space-padding or
     * truncation to ensure the new date string matches the original byte-width.
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
        int xmpId = handler.findMetadataID(BoxHandler.MetadataType.XMP);
        String[] tags = {"xmp:CreateDate", "xmp:ModifyDate", "xmp:MetadataDate"};

        if (xmpId != -1 && xmpData.isPresent())
        {
            String content = new String(xmpData.get(), StandardCharsets.UTF_8);

            for (String tag : tags)
            {
                int tagIdx = content.indexOf(tag);

                while (tagIdx != -1)
                {
                    int vStart = findValueBoundary(content, tagIdx, true);
                    int vEnd = findValueBoundary(content, vStart, false);

                    if (vStart > 0 && vEnd > vStart)
                    {
                        long physicalPos = handler.getPhysicalAddress(xmpId, vStart, MetadataType.XMP);

                        if (physicalPos != -1)
                        {
                            int width = vEnd - vStart;
                            String patch = (width >= 25) ? zdt.format(XMP_LONG) : zdt.format(XMP_SHORT);

                            if (patch.length() > width)
                            {
                                LOGGER.warn("XMP field too narrow [" + width + "]. Date may be truncated");
                            }

                            // Ensure the patch fits the original width exactly
                            byte[] finalPatch = String.format("%-" + width + "s", patch).substring(0, width).getBytes(StandardCharsets.UTF_8);

                            raf.seek(physicalPos);
                            raf.write(finalPatch);
                        }
                    }

                    tagIdx = content.indexOf(tag, vEnd);
                }
            }
        }
    }

    /**
     * Needed for parsing XMP data, it finds the start or end of an XML value, accounting for both
     * attribute quotes and element brackets.
     *
     * @param content
     *        the raw XMP string
     * @param startIdx
     *        the index of the tag name
     * @param isStart
     *        true to find the start of the value, false for the end
     * @return the index of the value boundary
     */
    private static int findValueBoundary(String content, int startIdx, boolean isStart)
    {
        int bracket = content.indexOf(isStart ? ">" : "<", startIdx);
        int quote = content.indexOf("\"", startIdx);

        if (quote != -1 && (bracket == -1 || quote < bracket))
        {
            return isStart ? quote + 1 : quote;
        }

        return isStart ? bracket + 1 : bracket;
    }

    /**
     * Extracts and saves the XMP block to a standalone file for structural validation.
     *
     * @param heicPath
     *        the source HEIF/HEIC file
     * @throws IOException
     *         if file access fails
     */
    public static void exportXmpForDebug(Path heicPath) throws IOException
    {
        try (RandomAccessFile raf = new RandomAccessFile(heicPath.toFile(), "r"))
        {
            byte[] buffer = new byte[(int) raf.length()];

            raf.readFully(buffer);

            String content = new String(buffer, StandardCharsets.UTF_8);
            int start = content.indexOf("<?xpacket");
            int xmpMetaEnd = content.lastIndexOf("</x:xmpmeta>");

            if (start != -1 && xmpMetaEnd != -1)
            {
                int end = content.indexOf("?>", xmpMetaEnd);

                if (end != -1)
                {
                    String xmpData = content.substring(start, end + 2);
                    Path outputPath = Paths.get("debug_xmp.xml");
                    Files.write(outputPath, xmpData.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }
}