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
 * An utility class to provide the functionality to surgically patch dates both in EXIF segment
 * (TIFF) and XMP segment (XML) in JPEG files. It overwrites bytes inline using the file stream
 * resource to maintain file integrity and prevent metadata displacement.
 * </p>
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
     * Patches all identified metadata dates within the JPEG file at the specified path. Basically,
     * it iterates through JPEG markers to identify and process APP1 segments, containing EXIF or
     * XMP payloads.
     *
     * @param imagePath
     *        the {@link Path} to the JPG to be modified
     * @param newDate
     *        the new timestamp to apply to all metadata fields
     *
     * @throws IOException
     *         if the file cannot be read, parsed, or written to
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

                                // Not sure if it is necessary
                                if (xmpDump)
                                {
                                    String xmlName = imagePath.getFileName().toString().replaceAll("^(.*)\\.[^.]+$", "$1.xml");
                                    Path outputPath = imagePath.resolveSibling(xmlName);
                                    byte[] xmpBytes = reader.peek(payloadStart + JpgParser.XMP_IDENTIFIER.length, xmpLength);

                                    String formatted = fastPrettyPrint(xmpBytes);
                                    Files.write(outputPath, formatted.getBytes(StandardCharsets.UTF_8));
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
     * EXIF segment. Note, any date-time entries associated with GPS will be recorded in UTC, where
     * the rational is 8 bytes (4 for numerator and 4 for denominator).
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

        ByteOrder originalOrder = reader.getByteOrder();
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
                        ZonedDateTime targetTime = zdt;
                        DateTimeFormatter formatter = EXIF_FORMATTER;
                        EntryIFD entry = dir.getTagEntry(tag);
                        long physicalPos = tiffHeaderPos + entry.getOffset();

                        if (tag == TagIFD_GPS.GPS_DATE_STAMP)
                        {
                            // Logic shift: GPS tags must be UTC, others remain local
                            targetTime = zdt.withZoneSameInstant(ZoneId.of("UTC"));
                            formatter = GPS_FORMATTER;
                        }

                        String value = targetTime.format(formatter);
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
            reader.setByteOrder(originalOrder);
        }
    }

    /**
     * Scans the XMP XML content for date-related tags and overwrites their values, ensuring the
     * replacement entry to have an exact length to match with the the existing length of that
     * sub-string. If it is shorter, padding with spaces will be filled in.
     *
     * It facilitates character-to-byte mapping to ensure UTF-8 multi-byte characters do not offset
     * the physical write position.
     *
     * @param reader
     *        the reader positioned at the start of the XMP XML
     * @param length
     *        the length of the XMP payload
     * @param zdt
     *        the target date and time
     *
     * @throws IOException
     *         if writing to the XMP segment fails
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
                         * The idea is to calculate the real length from Index 0 to the position
                         * within the XML string, where the first occurrence of the required value.
                         * This maps the character index to the actual File byte offset. This
                         * ensures positional accuracy.
                         */
                        int vByteStart = xmlContent.substring(0, vCharStart).getBytes(StandardCharsets.UTF_8).length;
                        long physicalPos = startPos + vByteStart;
                        String safePatch = getSafeXmpPatch((vCharWidth >= 25) ? zdt.format(XMP_LONG) : zdt.format(XMP_SHORT), vCharWidth);

                        if (safePatch != null)
                        {
                            byte[] finalPatch = safePatch.getBytes(StandardCharsets.UTF_8);

                            reader.seek(physicalPos);
                            reader.write(finalPatch);

                            LOGGER.info(String.format("Patched XMP tag [%s] at 0x%X", tag, physicalPos));
                        }

                        /*
                         * It forces the replacement string to have an exact length as the old
                         * sub-string. If it is shorter, padding with spaces will be filled in.
                         */
                        // byte[] finalPatch = String.format("%-" + vCharWidth + "s",
                        // patch).substring(0, vCharWidth).getBytes(StandardCharsets.UTF_8);
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
     * the opening tag is closed ({@code >}).
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

        // Look for the end of the opening tag or the equals sign within a reasonable distance
        int bracket = content.indexOf(">", tagIdx);
        int equals = content.indexOf("=", tagIdx);

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
     * Validates if the new date string can fit into the existing XMP slot.
     * 
     * @param newDate
     *        the formatted date string
     * @param existingWidth
     *        the character width available in the XML
     * @return the safely adjusted string, or null if it cannot fit without corruption
     */
    private static String getSafeXmpPatch(String newDate, int existingWidth)
    {
        if (newDate.length() > existingWidth)
        {
            // If the slot is too small for a full ISO string, try the shorter version
            // Example: If slot is 10 chars, use "yyyy-MM-dd"
            if (existingWidth >= 10 && newDate.contains("T"))
            {
                String shorterDate = newDate.split("T")[0];

                if (shorterDate.length() <= existingWidth)
                {
                    return String.format("%-" + existingWidth + "s", shorterDate);
                }
            }

            LOGGER.warn(String.format("New date [%s] is longer than XMP slot [%d]. Skipping to avoid corruption.", newDate, existingWidth));

            return null;
        }

        // Pad with spaces if the new date is shorter than the original slot
        return String.format("%-" + existingWidth + "s", newDate);
    }

    /**
     * A high-performance, regex-based formatter for XMP debugging. Bypasses DOM overhead to provide
     * instant visual structure.
     */
    private static String fastPrettyPrint(byte[] xmpBytes)
    {
        String xml = new String(xmpBytes, StandardCharsets.UTF_8);

        xml = xml.replaceAll(">\\s*<", ">\n<");
        xml = xml.replaceAll("\\s+([a-zA-Z0-9]+:[a-zA-Z0-9]+=\")", "\n        $1");
        xml = xml.replaceAll("(?<=>)([^<\\s][^<]*)(?=<)", "\n    $1\n");
        xml = xml.replaceAll("\"\\s*/>", "\"\n/>").replaceAll("\">", "\"\n>");

        return xml.trim();
    }
}