package jpg;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import common.ByteValueConverter;
import common.ImageRandomAccessReader;
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
 * A utility class providing the functionality to surgically patch dates in both the EXIF (TIFF) and
 * XMP (XML) segments of JPEG files. It overwrites bytes inline using the file stream resource to
 * maintain file integrity and prevent metadata displacement.
 *
 * <p>
 * The patcher handles three distinct date formats:
 * </p>
 *
 * <ul>
 * <li><b>EXIF ASCII:</b> Standardised "yyyy:MM:dd HH:mm:ss" strings</li>
 * <li><b>GPS Rational:</b> Binary encoded time components (H/1, M/1, S/1) in UTC</li>
 * <li><b>XMP ISO 8601:</b> XML-based date strings with or without timezone offsets</li>
 * </ul>
 *
 * @author Trevor Maggs
 * @version 1.5
 */
public final class JpgDatePatcher
{
    private static final LogFactory LOGGER = LogFactory.getLogger(JpgDatePatcher.class);
    private static final DateTimeFormatter EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter GPS_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ENGLISH);
    private static final DateTimeFormatter XMP_LONG = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter XMP_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Default constructor is unsupported and will always throw an exception.
     *
     * @throws UnsupportedOperationException
     *         to indicate that instantiation is not supported
     */
    private JpgDatePatcher()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

    /**
     * Patches all identified metadata dates within the JPEG file at the specified path. It iterates
     * through JPEG markers to identify and process APP1 segments containing EXIF or XMP payloads.
     *
     * @param imagePath
     *        the {@link Path} to the JPG to be modified
     * @param newDate
     *        the new timestamp to apply to all metadata fields
     * @param xmpDump
     *        indicates whether to dump XMP data into an XML-formatted file for debugging; if true,
     *        a file is created based on the image name
     */
    public static void patchAllDates(Path imagePath, FileTime newDate) throws IOException
    {
        patchAllDates(imagePath, newDate, false);
    }

    /**
     * Patches all identified metadata dates within the JPEG file at the specified path. Basically,
     * it iterates through JPEG markers to identify and process APP1 segments, containing EXIF or
     * XMP payloads.
     *
     * @param imagePath
     *        the {@link Path} to the JPG to be modified
     * @param newDate
     *        the new timestamp to apply to all metadata fields
     * @param xmpDump
     *        indicates whether to dump XMP data into an XML-formatted file based on the file name,
     *        useful for debugging or inspection purposes. Null will not create the dump
     *
     * @throws IOException
     *         if the file cannot be read, parsed, or written to
     */
    public static void patchAllDates(Path imagePath, FileTime newDate, boolean xmpDump) throws IOException
    {
        ZonedDateTime zdt = newDate.toInstant().atZone(ZoneId.systemDefault());

        try (ImageRandomAccessReader reader = new ImageRandomAccessReader(imagePath, ByteOrder.BIG_ENDIAN, "rw"))
        {
            while (reader.getCurrentPosition() < reader.length())
            {
                JpgSegmentConstants segment = JpgParser.fetchNextSegment(reader);

                if (segment == null || segment == JpgSegmentConstants.END_OF_IMAGE || segment == JpgSegmentConstants.START_OF_STREAM)
                {
                    break;
                }

                if (segment.hasLengthField())
                {
                    int length = reader.readUnsignedShort() - 2;

                    if (length > 0)
                    {
                        long payloadStart = reader.getCurrentPosition();

                        if (segment == JpgSegmentConstants.APP1_SEGMENT)
                        {
                            byte[] header = reader.peek(payloadStart, Math.min(length, JpgParser.XMP_IDENTIFIER.length));

                            if (header.length >= JpgParser.EXIF_IDENTIFIER.length && Arrays.equals(Arrays.copyOf(header, JpgParser.EXIF_IDENTIFIER.length), JpgParser.EXIF_IDENTIFIER))
                            {
                                reader.skip(JpgParser.EXIF_IDENTIFIER.length);
                                processExifSegment(reader, length - JpgParser.EXIF_IDENTIFIER.length, zdt);
                            }

                            else if (header.length >= JpgParser.XMP_IDENTIFIER.length && Arrays.equals(Arrays.copyOf(header, JpgParser.XMP_IDENTIFIER.length), JpgParser.XMP_IDENTIFIER))
                            {
                                int xmpLength = length - JpgParser.XMP_IDENTIFIER.length;

                                // Optional diagnostic dump of XMP payload to an external XML file
                                if (xmpDump)
                                {
                                    printFastDumpXML(imagePath, reader.peek(payloadStart + JpgParser.XMP_IDENTIFIER.length, xmpLength));
                                    // Utils.dumpFormattedXmp(imagePath);
                                }

                                reader.skip(JpgParser.XMP_IDENTIFIER.length);
                                processXmpSegment(reader, xmpLength, zdt);
                            }
                        }

                        reader.seek(payloadStart + length);
                    }
                }
            }
        }
    }

    /**
     * Patches the ASCII date tags and binary GPS time-stamp rationals if they are present within an
     * EXIF segment. Note that any date-time entries associated with GPS will be recorded in UTC,
     * where the rational is 8 bytes (4 for numerator and 4 for denominator).
     *
     * @param reader
     *        the reader positioned at the start of the TIFF header
     * @param length
     *        the length of the TIFF payload beginning at the TIFF header
     * @param zdt
     *        the target date and time
     *
     * @throws IOException
     *         if the TIFF structure is corrupt or writing fails
     */
    private static void processExifSegment(ImageRandomAccessReader reader, int length, ZonedDateTime zdt) throws IOException
    {
        Taggable[] ifdTags = {
                TagIFD_Baseline.IFD_DATE_TIME, TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_DATE_TIME_DIGITIZED, TagIFD_GPS.GPS_DATE_STAMP};

        ByteOrder currentOrder = reader.getByteOrder();
        long tiffHeaderPos = reader.getCurrentPosition();
        byte[] payload = reader.readBytes(length);
        TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(payload);

        try
        {
            reader.setByteOrder(metadata.getByteOrder());

            for (DirectoryIFD dir : metadata)
            {
                for (Taggable tag : ifdTags)
                {
                    if (dir.hasTag(tag))
                    {
                        ZonedDateTime updatedTime = zdt;
                        DateTimeFormatter formatter = EXIF_FORMATTER;
                        EntryIFD entry = dir.getTagEntry(tag);
                        long physicalPos = tiffHeaderPos + entry.getOffset();

                        if (tag == TagIFD_GPS.GPS_DATE_STAMP)
                        {
                            // Logic shift: GPS tags must be UTC, others remain local
                            updatedTime = zdt.withZoneSameInstant(ZoneId.of("UTC"));
                            formatter = GPS_FORMATTER;
                        }

                        String value = updatedTime.format(formatter);
                        byte[] dateBytes = Arrays.copyOf((value + "\0").getBytes(StandardCharsets.US_ASCII), (int) entry.getCount());

                        reader.seek(physicalPos);
                        reader.write(dateBytes);

                        LOGGER.info(String.format("Patched %s tag [%s] at 0x%X to %s", (tag == TagIFD_GPS.GPS_DATE_STAMP ? "UTC" : "Local"), tag, physicalPos, value));
                    }
                }

                if (dir.hasTag(TagIFD_GPS.GPS_TIME_STAMP))
                {
                    EntryIFD entry = dir.getTagEntry(TagIFD_GPS.GPS_TIME_STAMP);
                    long physicalPos = tiffHeaderPos + entry.getOffset();
                    ZonedDateTime utc = zdt.withZoneSameInstant(ZoneId.of("UTC"));

                    // TimeStamp has 3 Rationals (H, M, S) = 24 bytes total.
                    byte[] timeBytes = new byte[24];

                    // Hour / 1
                    ByteValueConverter.packRational(timeBytes, 0, utc.getHour(), 1, reader.getByteOrder());
                    // Minute / 1
                    ByteValueConverter.packRational(timeBytes, 8, utc.getMinute(), 1, reader.getByteOrder());
                    // Second / 1
                    ByteValueConverter.packRational(timeBytes, 16, utc.getSecond(), 1, reader.getByteOrder());

                    reader.seek(physicalPos);
                    reader.write(timeBytes);

                    LOGGER.info(String.format("Patched GPS_TIME_STAMP (Rational) at 0x%X", physicalPos));
                }
            }
        }

        finally
        {
            reader.setByteOrder(currentOrder);
        }
    }

    /**
     * Scans the XMP XML content for date-related tags and overwrites their values.
     *
     * <p>
     * This method performs an in-place binary overwrite. It maps character indices to UTF-8 byte
     * offsets to ensure the physical write position remains accurate, even if the XML contains
     * multi-byte characters.
     * </p>
     *
     * @param reader
     *        the reader positioned at the start of the XMP XML
     * @param length
     *        the length of the XMP payload
     * @param zdt
     *        the target date and time
     * @throws IOException
     *         if an I/O error occurs during the overwrite process
     */
    private static void processXmpSegment(ImageRandomAccessReader reader, int length, ZonedDateTime zdt) throws IOException
    {
        String[] xmpTags = {
                "xmp:CreateDate", "xap:CreateDate", "xmp:ModifyDate", "xap:ModifyDate",
                "xmp:MetadataDate", "xap:MetadataDate", "photoshop:DateCreated",
                "exif:DateTimeOriginal", "exif:DateTimeDigitized", "tiff:DateTime"
        };

        long startPos = reader.getCurrentPosition();
        byte[] xmpBytes = reader.readBytes(length);
        String xmlContent = new String(xmpBytes, StandardCharsets.UTF_8);

        for (String tag : xmpTags)
        {
            int tagIdx = xmlContent.indexOf(tag);

            while (tagIdx != -1)
            {
                // Skip closing tags like </xmp:CreateDate>
                if (tagIdx > 0 && xmlContent.charAt(tagIdx - 1) != '/')
                {
                    int[] span = findValueSpan(xmlContent, tagIdx);

                    if (span != null && span[1] >= 10)
                    {
                        int vCharStart = span[0];
                        int vCharWidth = span[1];

                        /*
                         * Maps the character index to the actual file byte offset by calculating
                         * the UTF-8 byte length of the preceding string, beginning at Index 0. This
                         * prevents positional drift if the XML contains multi-byte characters, for
                         * example: emojis or non-Latin text.
                         */
                        int vByteStart = xmlContent.substring(0, vCharStart).getBytes(StandardCharsets.UTF_8).length;
                        long physicalPos = startPos + vByteStart;
                        String newDatePatch = (vCharWidth >= 25) ? zdt.format(XMP_LONG) : zdt.format(XMP_SHORT);
                        String alignedPatch = alignXmpValueSlot(newDatePatch, vCharWidth);

                        if (alignedPatch != null)
                        {
                            reader.seek(physicalPos);
                            reader.write(alignedPatch.getBytes(StandardCharsets.UTF_8));

                            LOGGER.info(String.format("Patched XMP tag [%s] at 0x%X", tag, physicalPos));
                        }

                        else
                        {
                            LOGGER.error(String.format("Skipped XMP tag [%s] due to insufficient width (%d)", tag, vCharWidth));
                        }
                    }
                }

                tagIdx = xmlContent.indexOf(tag, tagIdx + tag.length());
            }
        }
    }

    /**
     * Identifies the exact byte-span of a value for a specific XMP tag within the XML content.
     *
     * <p>
     * This handles the two primary ways XMP serialises data:
     * </p>
     *
     * <ul>
     * <li><b>Attribute:</b> {@code <xmp:ModifyDate="2011:10:07".../>}</li>
     * <li><b>Element:</b> {@code <xmp:ModifyDate>2011:10:07</xmp:ModifyDate>}</li>
     * </ul>
     *
     * The algorithm determines the structure by checking if an assignment ({@code =}) occurs before
     * the opening tag is closed ({@code >}). The returned span represents the raw string content
     * between the XML delimiters (quotes for attributes or brackets for elements), excluding the
     * delimiters themselves.
     *
     * @param content
     *        the XML string to scan
     * @param tagIdx
     *        the starting index of the tag name within the content
     * @return an array of two integers: {@code [startOffset, length]}, or {@code null} if the value
     *         span cannot be validly determined
     */
    private static int[] findValueSpan(String content, int tagIdx)
    {
        int start = 0;
        int end = 0;
        int equals = content.indexOf("=", tagIdx);
        int bracket = content.indexOf(">", tagIdx);

        // See if it is an attribute (tag="val")
        if (equals != -1 && (bracket == -1 || equals < bracket))
        {
            int quote = content.indexOf("\"", equals);

            if (quote == -1)
            {
                return null;
            }

            start = quote + 1;
            end = content.indexOf("\"", start);
        }

        // See if it is an element (<tag>val</tag>)
        else if (bracket != -1)
        {
            start = bracket + 1;
            end = content.indexOf("<", start);
        }

        return ((start > 0 && end > start) ? new int[]{start, end - start} : null);
    }

    /**
     * Formats a date string to fit exactly into a pre-existing XMP value slot. Ensures the file
     * structure remains intact by padding with spaces or truncating non-essential date components
     * if necessary. The idea is to ensure that the replacement string does not cause corruption in
     * the existing XML structure by maintaining a constant byte-footprint.
     *
     * @param newDate
     *        the formatted date string
     * @param slotWidth
     *        the character width available in the XML
     * @return the safely adjusted string, or null if it cannot fit
     */
    private static String alignXmpValueSlot(String newDate, int slotWidth)
    {
        if (newDate.length() > slotWidth)
        {
            // If the slot is too small for a full ISO string, try the shorter version
            // Example: If slot is 10 chars, use "yyyy-MM-dd"
            if (slotWidth >= 10 && newDate.contains("T"))
            {
                String shorterDate = newDate.split("T")[0];

                if (shorterDate.length() <= slotWidth)
                {
                    return String.format("%-" + slotWidth + "s", shorterDate);
                }
            }

            LOGGER.warn(String.format("New date [%s] is longer than XMP slot [%d]. Skipping to avoid corruption.", newDate, slotWidth));

            return null;
        }

        // Pad with spaces if the new date is shorter than the original slot
        return String.format("%-" + slotWidth + "s", newDate);
    }

    /**
     * A lightweight, regex-based formatter for XMP debugging. It bypasses DOM overhead to provide
     * an immediate visual structure of the XMP packet. The formatted content is written to a
     * sibling XML file for inspection.
     *
     * @param imagePath
     *        the path of the source image
     * @param xmpBytes
     *        the raw XMP bytes extracted from the JPEG
     * @return the formatted XML string
     *
     * @throws IOException
     *         if an I/O error occurs during the file write
     */
    private static String printFastDumpXML(Path imagePath, byte[] xmpBytes) throws IOException
    {
        String xmlName = imagePath.getFileName().toString().replaceAll("^(.*)\\.[^.]+$", "$1.xml");
        Path outputPath = imagePath.resolveSibling(xmlName);

        String xml = new String(xmpBytes, StandardCharsets.UTF_8);

        // 1. Force newlines between tags
        xml = xml.replaceAll(">\\s*<", ">\n<");

        // 2. Indent attributes to make them readable
        xml = xml.replaceAll("\\s+([a-zA-Z0-9]+:[a-zA-Z0-9]+=\")", "\n    $1");

        // 3. Handle element values (content between tags)
        xml = xml.replaceAll("(?<=>)([^<\\s][^<]*)(?=<)", "\n        $1\n");

        // 4. Clean up self-closing tags and block endings
        xml = xml.replaceAll("\"\\s*/>", "\"\n/>").replaceAll("\">", "\"\n>");

        String finalXml = xml.trim();

        Files.write(outputPath, finalXml.getBytes(StandardCharsets.UTF_8));

        return finalXml;
    }
}