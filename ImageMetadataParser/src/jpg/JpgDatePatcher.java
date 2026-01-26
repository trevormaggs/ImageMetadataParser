package jpg;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
 * Surgically patches EXIF (TIFF) and XMP (XML) dates in JPEG files. Overwrites bytes inline using
 * ImageRandomAccessReader to maintain file integrity.
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
    private static final Map<Taggable, DateTimeFormatter> EXIF_TAG_FORMATS;

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
    private JpgDatePatcher()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

    /**
     * Patches all identified metadata dates within the JPEG file.
     *
     * @param path
     *        the {@link Path} to the JPG to be modified
     * @param newDate
     *        the new timestamp to apply to all metadata fields
     *
     * @throws IOException
     *         if the file cannot be read, parsed, or written to
     */
    public static void patchAllDates(Path imagePath, FileTime newDate) throws IOException
    {
        ZonedDateTime zdt = newDate.toInstant().atZone(ZoneId.systemDefault());

        try (ImageRandomAccessReader reader = new ImageRandomAccessReader(imagePath, ByteOrder.BIG_ENDIAN, "rw"))
        {
            patchMetadataSegments(reader, zdt);
        }
    }

    private static void patchMetadataSegments(ImageRandomAccessReader reader, ZonedDateTime zdt) throws IOException
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
                            /* This directly skips the "Exif\0\0" preamble */
                            reader.skip(JpgParser.EXIF_IDENTIFIER.length);
                            processExifSegment(reader, length - JpgParser.EXIF_IDENTIFIER.length, zdt);
                        }

                        else if (header.length >= JpgParser.XMP_IDENTIFIER.length && Arrays.equals(Arrays.copyOf(header, JpgParser.XMP_IDENTIFIER.length), JpgParser.XMP_IDENTIFIER))
                        {
                            reader.skip(JpgParser.XMP_IDENTIFIER.length);
                            processXmpSegment(reader, length - JpgParser.XMP_IDENTIFIER.length, zdt);
                        }
                    }

                    reader.seek(payloadStart + length);
                }
            }
        }
    }

    private static void processExifSegment(ImageRandomAccessReader reader, int length, ZonedDateTime zdt) throws IOException
    {
        long tiffHeaderStart = reader.getCurrentPosition();
        byte[] payload = reader.readBytes(length);
        TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(payload);

        // GPS tags MUST be UTC per Exif spec
        ZonedDateTime utcTime = zdt.withZoneSameInstant(ZoneId.of("UTC"));

        for (DirectoryIFD dir : metadata)
        {
            // Handle standard String dates (including GPS_DATE_STAMP)
            for (Map.Entry<Taggable, DateTimeFormatter> formatEntry : EXIF_TAG_FORMATS.entrySet())
            {
                Taggable tag = formatEntry.getKey();

                if (dir.hasTag(tag))
                {
                    EntryIFD entry = dir.getTagEntry(tag);
                    long physicalPos = tiffHeaderStart + entry.getOffset();

                    ZonedDateTime targetTime = (tag == TagIFD_GPS.GPS_DATE_STAMP) ? utcTime : zdt;
                    String value = targetTime.format(formatEntry.getValue());
                    byte[] dateBytes = Arrays.copyOf((value + "\0").getBytes(StandardCharsets.US_ASCII), (int) entry.getCount());

                    reader.seek(physicalPos);
                    reader.write(dateBytes);
                }
            }

            // Handle GPS_TIME_STAMP (Rational[3])
            if (dir.hasTag(TagIFD_GPS.GPS_TIME_STAMP))
            {
                EntryIFD entry = dir.getTagEntry(TagIFD_GPS.GPS_TIME_STAMP);
                long physicalPos = tiffHeaderStart + entry.getOffset();

                // 3 Rationals = 24 bytes. Each Rational is 4-byte Numerator / 4-byte Denominator.
                byte[] timeBytes = new byte[24];
                ByteOrder order = reader.getByteOrder();

                // Use the utility class to pack the UTC time components
                ByteValueConverter.packRational(timeBytes, 0, utcTime.getHour(), 1, order);
                ByteValueConverter.packRational(timeBytes, 8, utcTime.getMinute(), 1, order);
                ByteValueConverter.packRational(timeBytes, 16, utcTime.getSecond(), 1, order);

                reader.seek(physicalPos);
                reader.write(timeBytes);

                LOGGER.info(String.format("Patched GPSTimeStamp at 0x%X to %02d:%02d:%02d UTC", physicalPos, utcTime.getHour(), utcTime.getMinute(), utcTime.getSecond()));
            }
        }
    }

    private static void processXmpSegment(ImageRandomAccessReader reader, int length, ZonedDateTime zdt) throws IOException
    {
        String[] xmpTags = {
                "xmp:CreateDate", "xap:CreateDate",
                "xmp:ModifyDate", "xap:ModifyDate",
                "xmp:MetadataDate", "xap:MetadataDate",
                "photoshop:DateCreated",
                "exif:DateTimeOriginal", "exif:DateTimeDigitized",
                "tiff:DateTime"
        };

        long startPos = reader.getCurrentPosition();
        String xmlContent = new String(reader.readBytes(length), StandardCharsets.UTF_8);

        for (String tag : xmpTags)
        {
            int tagIdx = xmlContent.indexOf(tag);

            while (tagIdx != -1)
            {
                // Ensure we aren't hitting a closing tag (e.g., </xmp:CreateDate>)
                if (tagIdx > 0 && xmlContent.charAt(tagIdx - 1) != '/')
                {
                    int[] span = findValueSpan(xmlContent, tagIdx, tag);

                    if (span != null && span[1] >= 10)
                    {
                        int vStart = span[0];
                        int vWidth = span[1];

                        String patch = (vWidth >= 25) ? zdt.format(XMP_LONG) : zdt.format(XMP_SHORT);

                        // Pad or truncate to maintain original byte length exactly
                        byte[] finalPatch = String.format("%-" + vWidth + "s", patch).substring(0, vWidth).getBytes(StandardCharsets.UTF_8);

                        // Start of segment + offset within XML
                        long physicalPos = startPos + vStart;

                        System.out.printf("physicalPos: %s\n", physicalPos);
                        System.out.printf("XMP: %s\n", ByteValueConverter.toHex(finalPatch));

                        reader.seek(physicalPos);
                        reader.write(finalPatch);

                        LOGGER.info("Patched XMP tag [" + tag + "] at 0x" + Long.toHexString(physicalPos));
                    }
                }

                tagIdx = xmlContent.indexOf(tag, tagIdx + tag.length());
            }
        }
    }

    private static int[] findValueSpan(String content, int tagIdx, String tagName)
    {
        // 1. Find the first delimiter after the tag name
        int bracket = content.indexOf(">", tagIdx);
        int quote = content.indexOf("\"", tagIdx);
        int equals = content.indexOf("=", tagIdx);

        // 2. Determine if we are looking at an Attribute (tag="value") or Element (<tag>value</tag>)
        // If there's an '=' before a '>', it's almost certainly an attribute.
        boolean isAttr = (equals != -1 && (bracket == -1 || equals < bracket));

        int start, end;
        
        if (isAttr)
        {
            // Safety: The quote should be very close to the tag (e.g., tag="...)
            // If the first quote is 50 chars away, we've jumped to a different attribute.
            if (quote == -1 || (quote - tagIdx) > tagName.length() + 5) return null;
            
            start = quote + 1;
            end = content.indexOf("\"", start);
        }
        
        else
        {
            // It's an element: <tag>value</tag>
            if (bracket == -1) return null;
            start = bracket + 1;
            end = content.indexOf("<", start);
        }

        if (start > 0 && end > start)
        {
            String value = content.substring(start, end);

            // GUARD: Never patch a Namespace URI/URL
            if (value.contains("http://") || value.contains("https://"))
            {
                return null;
            }

            return new int[]{start, end - start};
        }
        return null;
    }

    private static int[] findValueSpan2(String content, int tagIdx)
    {
        int bracket = content.indexOf(">", tagIdx);
        int quote = content.indexOf("\"", tagIdx);

        // Identify if the value is an attribute (inside quotes) or element (between brackets)
        boolean isAttr = (quote != -1 && (bracket == -1 || quote < bracket));
        int start = isAttr ? quote + 1 : bracket + 1;
        int end = content.indexOf(isAttr ? "\"" : "<", start);

        return (start > 0 && end > start) ? new int[]{start, end - start} : null;
    }
}