package jpg;

import java.io.EOFException;
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
            JpgSegmentConstants segment = fetchNextSegment(reader);

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
                            processExifSegment(reader, length, zdt);
                        }

                        else if (header.length >= JpgParser.XMP_IDENTIFIER.length && Arrays.equals(Arrays.copyOf(header, JpgParser.XMP_IDENTIFIER.length), JpgParser.XMP_IDENTIFIER))
                        {
                            System.out.printf("XMP: %s\n", ByteValueConverter.toHex(Arrays.copyOf(header, JpgParser.XMP_IDENTIFIER.length)));

                            processXmpSegment(reader, length, zdt);
                        }
                    }

                    reader.seek(payloadStart + length);
                }
            }
        }
    }

    private static void processExifSegment(ImageRandomAccessReader reader, int length, ZonedDateTime zdt) throws IOException
    {
        long startPos = reader.getCurrentPosition();
        byte[] payload = reader.readBytes(length);
        byte[] tiffPayload = JpgParser.stripExifPreamble(payload);
        TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(tiffPayload);

        for (DirectoryIFD dir : metadata)
        {
            for (Map.Entry<Taggable, DateTimeFormatter> formatEntry : EXIF_TAG_FORMATS.entrySet())
            {
                Taggable tag = formatEntry.getKey();

                if (dir.hasTag(tag))
                {
                    EntryIFD entry = dir.getTagEntry(tag);
                    long physicalPos = startPos + JpgParser.EXIF_IDENTIFIER.length + entry.getOffset();

                    // Logic shift: GPS tags must be UTC, others remain local
                    ZonedDateTime targetTime = (tag == TagIFD_GPS.GPS_DATE_STAMP) ? zdt.withZoneSameInstant(ZoneId.of("UTC")) : zdt;
                    String value = targetTime.format(formatEntry.getValue());
                    byte[] dateBytes = Arrays.copyOf((value + "\0").getBytes(StandardCharsets.US_ASCII), (int) entry.getCount());

                    System.out.printf("Exif: %s\n", ByteValueConverter.toHex(dateBytes));

                    reader.seek(physicalPos);
                    reader.write(dateBytes);

                    LOGGER.info(String.format("Patched %s tag [%s] at 0x%X to %s", (tag == TagIFD_GPS.GPS_DATE_STAMP ? "UTC" : "Local"), tag, physicalPos, value));
                }
            }
        }
    }

    private static void processXmpSegment(ImageRandomAccessReader reader, int length, ZonedDateTime zdt) throws IOException
    {
        long startPos = reader.getCurrentPosition();

        String[] xmpTags = {
                "xmp:CreateDate", "xap:CreateDate",
                "xmp:ModifyDate", "xap:ModifyDate",
                "xmp:MetadataDate", "xap:MetadataDate",
                "photoshop:DateCreated",
                "exif:DateTimeOriginal", "exif:DateTimeDigitized"}; 

        String fullContent = new String(reader.readBytes(length), StandardCharsets.UTF_8);
        String xmlContent = fullContent.substring(JpgParser.XMP_IDENTIFIER.length);

        for (String tag : xmpTags)
        {
            int tagIdx = xmlContent.indexOf(tag);

            while (tagIdx != -1)
            {
                // Ensure we aren't hitting a closing tag (e.g., </xmp:CreateDate>)
                if (tagIdx > 0 && xmlContent.charAt(tagIdx - 1) != '/')
                {
                    int[] span = findValueSpan(xmlContent, tagIdx);

                    if (span != null && span[1] >= 10)
                    {
                        int vStart = span[0];
                        int vWidth = span[1];

                        String patch = (vWidth >= 25) ? zdt.format(XMP_LONG) : zdt.format(XMP_SHORT);

                        // Pad or truncate to maintain original byte length exactly
                        byte[] finalPatch = String.format("%-" + vWidth + "s", patch).substring(0, vWidth).getBytes(StandardCharsets.UTF_8);

                        // 4. Calculate physical position:
                        // Start of segment + Length of Identifier + offset within XML
                        long physicalPos = startPos + JpgParser.XMP_IDENTIFIER.length + vStart;

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

    private static int[] findValueSpan(String content, int tagIdx)
    {
        int bracket = content.indexOf(">", tagIdx);
        int quote = content.indexOf("\"", tagIdx);

        // Identify if the value is an attribute (inside quotes) or element (between brackets)
        boolean isAttr = (quote != -1 && (bracket == -1 || quote < bracket));
        int start = isAttr ? quote + 1 : bracket + 1;
        int end = content.indexOf(isAttr ? "\"" : "<", start);

        return (start > 0 && end > start) ? new int[]{start, end - start} : null;
    }

    private static JpgSegmentConstants fetchNextSegment(ImageRandomAccessReader reader) throws IOException
    {
        try
        {
            int fillCount = 0;

            while (true)
            {
                int marker;
                int flag;

                marker = reader.readUnsignedByte();

                if (marker != 0xFF)
                {
                    // resync to marker
                    continue;
                }

                flag = reader.readUnsignedByte();

                /*
                 * In some cases, JPEG allows multiple 0xFF bytes (fill or padding bytes) before the
                 * actual segment flag. These are not part of any segment and should be skipped to
                 * find the next true segment type. A warning is logged and parsing is terminated if
                 * an excessive number of consecutive 0xFF fill bytes are found, as this may
                 * indicate a malformed or corrupted file.
                 */
                while (flag == 0xFF)
                {
                    fillCount++;

                    // Arbitrary limit to prevent an infinite loop
                    if (fillCount > 64)
                    {
                        LOGGER.warn("Excessive 0xFF padding bytes detected, possible file corruption");
                        return null;
                    }

                    flag = reader.readUnsignedByte();

                }

                if (!(flag >= JpgSegmentConstants.RST0.getFlag() && flag <= JpgSegmentConstants.RST7.getFlag()) && flag != JpgSegmentConstants.UNKNOWN.getFlag())
                {
                    LOGGER.debug(String.format("Segment flag [%s] detected", JpgSegmentConstants.fromBytes(marker, flag)));
                }

                return JpgSegmentConstants.fromBytes(marker, flag);
            }
        }

        catch (EOFException eof)
        {
            return null;
        }
    }
}