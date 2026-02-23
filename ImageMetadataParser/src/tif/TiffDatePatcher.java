package tif;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import common.ByteValueConverter;
import common.ImageRandomAccessWriter;
import common.Utils;
import logger.LogFactory;
import tif.DirectoryIFD.EntryIFD;
import tif.tagspecs.TagIFD_Baseline;
import tif.tagspecs.TagIFD_Exif;
import tif.tagspecs.TagIFD_Extension;
import tif.tagspecs.TagIFD_GPS;
import tif.tagspecs.Taggable;

/**
 * Performs in-place "surgical patching" of specific timestamp entries. It allows for the
 * modification of EXIF ASCII dates, GPS timestamps (stored as binary rationals), and XMP XML
 * packets by overwriting the raw bytes at their existing file offsets.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 15 February 2026
 */
public final class TiffDatePatcher
{
    private static final LogFactory LOGGER = LogFactory.getLogger(TiffDatePatcher.class);
    private static final DateTimeFormatter EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter GPS_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ENGLISH);

    /**
     * Default constructor is unsupported and will always throw an exception.
     *
     * @throws UnsupportedOperationException
     *         to indicate that instantiation is not supported
     */
    private TiffDatePatcher()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

    /**
     * Patches all identified metadata dates within the TIF file.
     *
     * <p>
     * Iterates through the IFD chain in reverse order to identify and process entries containing
     * EXIF or XMP payloads. Input {@link FileTime} is interpreted using the
     * {@link ZoneId#systemDefault()}.
     * </p>
     *
     * @param imagePath
     *        the {@link Path} to the TIF to be patched
     * @param newDate
     *        the new timestamp to apply
     * @param xmpDump
     *        if {@code true}, exports the raw XMP buffer to a file for verification
     *
     * @throws IOException
     *         if the TIFF structure is corrupt or the file is read-only
     */
    public static void patchAllDates(Path imagePath, FileTime newDate, boolean xmpDump) throws IOException
    {
        Taggable[] asciiTags = {
                TagIFD_Baseline.IFD_DATE_TIME,
                TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_DATE_TIME_DIGITIZED,
                TagIFD_GPS.GPS_DATE_STAMP
        };

        ZonedDateTime zdt = newDate.toInstant().atZone(ZoneId.systemDefault());

        try (IFDHandler handler = new IFDHandler(imagePath))
        {
            if (handler.parseMetadata())
            {
                try (ImageRandomAccessWriter writer = new ImageRandomAccessWriter(imagePath, handler.getTifByteOrder()))
                {
                    boolean xmpProcessed = false;
                    List<DirectoryIFD> dirList = handler.getDirectories();

                    for (int i = dirList.size() - 1; i >= 0; i--)
                    {
                        DirectoryIFD dir = dirList.get(i);

                        for (Taggable tag : asciiTags)
                        {
                            if (dir.hasTag(tag))
                            {
                                EntryIFD entry = dir.getTagEntry(tag);
                                processExifSegment(writer, entry, zdt);
                            }
                        }

                        if (dir.hasTag(TagIFD_GPS.GPS_TIME_STAMP))
                        {
                            EntryIFD entry = dir.getTagEntry(TagIFD_GPS.GPS_TIME_STAMP);
                            processExifGpsTimeStamp(writer, entry, zdt);
                        }

                        /*
                         * As per metadata standards, if multiple XMP blocks exist, the final
                         * instance is given precedence. To implement this last-one-wins strategy
                         * efficiently, this iteration searches directories in reverse order and
                         * stops at the first IFD_XML_PACKET (Tag 0x02BC) it finds.
                         */
                        if (!xmpProcessed && dir.hasTag(TagIFD_Extension.IFD_XML_PACKET))
                        {
                            xmpProcessed = true;
                            EntryIFD entry = dir.getTagEntry(TagIFD_Extension.IFD_XML_PACKET);
                            processXmpSegment(writer, entry, zdt, xmpDump);
                        }
                    }
                }
            }
        }
    }

    /**
     * Patches ASCII-formatted date tags in-place.
     *
     * <p>
     * Handles standard EXIF date tags, i.e, DateTimeOriginal, etc and {@code GPSDateStamp}. GPS
     * tags are automatically coerced to UTC. The value is null-terminated and padded or truncated
     * to fit the original {@code slotWidthLimit} defined by the IFD entry count.
     * </p>
     *
     * @param writer
     *        the active binary writer
     * @param entry
     *        the IFD entry targeting an ASCII field
     * @param zdt
     *        the target date and time
     *
     * @throws IOException
     *         if the write operation fails
     */
    private static void processExifSegment(ImageRandomAccessWriter writer, EntryIFD entry, ZonedDateTime zdt) throws IOException
    {
        Taggable tag = entry.getTag();
        ZonedDateTime updatedTime = zdt;
        DateTimeFormatter formatter = EXIF_FORMATTER;

        if (tag == TagIFD_GPS.GPS_DATE_STAMP)
        {
            updatedTime = zdt.withZoneSameInstant(ZoneId.of("UTC"));
            formatter = GPS_FORMATTER;
        }

        String value = updatedTime.format(formatter);
        int slotWidthLimit = (int) entry.getCount();
        byte[] dateBytes = Arrays.copyOf((value + "\0").getBytes(StandardCharsets.US_ASCII), slotWidthLimit);

        if (slotWidthLimit >= dateBytes.length)
        {
            // Null-terminate within the specific slot
            dateBytes[Math.min(value.length(), slotWidthLimit - 1)] = 0;

            writer.seek(entry.getOffset());
            writer.writeBytes(dateBytes);

            LOGGER.debug(String.format("Patched ASCII tag [%s] at offset %d", tag, entry.getOffset()));
        }

        else
        {
            LOGGER.error(String.format("Skipped tag [%s]. Slot width [%d] too small for file [%s]", tag, slotWidthLimit, writer.getFilename()));
        }
    }

    /**
     * Patches the binary GPS time-stamp in-place using a sequence of three 64-bit rational numbers.
     *
     * <p>
     * Per the EXIF specification, the {@code GPSTimeStamp} tag (Tag 0x0007) consists of three
     * RATIONAL values representing UTC hours, minutes, and seconds. Each rational occupies 8 bytes
     * (a 4-byte unsigned integer numerator and a 4-byte unsigned integer denominator).
     * </p>
     *
     * <p>
     * It automatically converts the provided {@link ZonedDateTime} to the UTC zone to maintain
     * compliance with GPS metadata standards, regardless of the local system's timezone settings.
     * </p>
     *
     * @param writer
     *        the writer used to perform the surgical binary modification
     * @param entry
     *        the {@link EntryIFD} targeting the GPS_TIME_STAMP tag
     * @param zdt
     *        the target date and time to be converted and encoded
     *
     * @throws IOException
     *         if an I/O error occurs or the file offset is unreachable
     */
    private static void processExifGpsTimeStamp(ImageRandomAccessWriter writer, EntryIFD entry, ZonedDateTime zdt) throws IOException
    {
        byte[] timeBytes = new byte[24];
        ZonedDateTime utc = zdt.withZoneSameInstant(ZoneId.of("UTC"));

        ByteValueConverter.packRational(timeBytes, 0, utc.getHour(), 1, writer.getByteOrder());
        ByteValueConverter.packRational(timeBytes, 8, utc.getMinute(), 1, writer.getByteOrder());
        ByteValueConverter.packRational(timeBytes, 16, utc.getSecond(), 1, writer.getByteOrder());

        if (entry.getByteArray().length == timeBytes.length)
        {
            writer.seek(entry.getOffset());
            writer.writeBytes(timeBytes);

            LOGGER.debug(String.format("Patched GPS_TIME_STAMP rational at offset %d", entry.getOffset()));
        }

        else
        {
            LOGGER.error(String.format("Skipped tag [%s]. Slot width [%d] too small for file [%s]", entry.getTag(), entry.getByteLength(), writer.getFilename()));
        }
    }

    /**
     * Performs a binary-safe search and replace within an XMP XML packet.
     * *
     * <p>
     * It calculates physical byte offsets based on UTF-8 encoding rather than character indices.
     * This prevents "positional drift" when the XML contains multi-byte characters (e.g.,
     * Unicode symbols or BOM).
     * </p>
     *
     * <p>
     * Only values with a sufficient {@code slotByteWidth} (minimum 10 bytes) are patched to prevent
     * structure corruption.
     * </p>
     *
     * @param writer
     *        the active binary writer
     * @param entry
     *        the IFD entry containing the {@code TagIFD_Baseline#IFD_XML_PACKET}
     * @param zdt
     *        the replacement date
     * @param xmpDump
     *        indicates if the pre-patch buffer should be dumped to disk
     *
     * @throws IOException
     *         if binary seek or write fails
     */
    private static void processXmpSegment(ImageRandomAccessWriter writer, EntryIFD entry, ZonedDateTime zdt, boolean xmpDump) throws IOException
    {
        String[] xmpTags = {
                "xmp:CreateDate", "xap:CreateDate", "xmp:ModifyDate", "xap:ModifyDate",
                "xmp:MetadataDate", "xap:MetadataDate", "photoshop:DateCreated",
                "exif:DateTimeOriginal", "exif:DateTimeDigitized", "tiff:DateTime"
        };

        byte[] xmpBytes = entry.getByteArray();
        String xmlContent = new String(xmpBytes, StandardCharsets.UTF_8);

        for (String tag : xmpTags)
        {
            int tagIdx = xmlContent.indexOf(tag);

            while (tagIdx != -1)
            {
                // Skip closing tags like </xmp:CreateDate>
                if (tagIdx > 0 && xmlContent.charAt(tagIdx - 1) != '/')
                {
                    int[] span = Utils.findValueSpan(xmlContent, tagIdx);

                    if (span != null && span[1] >= 10)
                    {
                        int startIdx = span[0];
                        int charLen = span[1];

                        /*
                         * Maps the character index to the actual file byte offset by calculating
                         * the UTF-8 byte length of the preceding string, beginning at Index 0. This
                         * prevents positional drift if the XML contains multi-byte characters, for
                         * example: emojis or non-Latin text.
                         */
                        int vByteStart = xmlContent.substring(0, startIdx).getBytes(StandardCharsets.UTF_8).length;
                        long physicalPos = entry.getOffset() + vByteStart;
                        int slotByteWidth = xmlContent.substring(startIdx, startIdx + charLen).getBytes(StandardCharsets.UTF_8).length;
                        byte[] alignedPatch = Utils.alignXmpValueSlot(zdt, slotByteWidth);

                        if (alignedPatch != null && alignedPatch.length == slotByteWidth)
                        {
                            writer.seek(physicalPos);
                            writer.writeBytes(alignedPatch);

                            LOGGER.debug(String.format("\t-> Patched XMP tag [%s] at offset %d", tag, physicalPos));
                        }

                        else
                        {
                            LOGGER.error(String.format("Skipped XMP tag [%s] due to insufficient slot width [%d] for patching", tag, slotByteWidth));
                        }
                    }
                }

                tagIdx = xmlContent.indexOf(tag, tagIdx + tag.length());
            }
        }

        if (xmpDump)
        {
            Utils.printFastDumpXML(writer.getFilename(), xmpBytes);
        }
    }
}