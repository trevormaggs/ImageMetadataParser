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
public final class JpgDatePatcher2
{
    private static final LogFactory LOGGER = LogFactory.getLogger(JpgDatePatcher2.class);
    private static final byte[] XMP_ID = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.UTF_8);
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
    private JpgDatePatcher2()
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
            // patchMetadataSegments(reader, zdt);
            readMetadataSegments(reader, zdt);
        }
    }

    private static void readMetadataSegments(ImageRandomAccessReader reader, ZonedDateTime zdt) throws IOException
    {
        byte[] exifSegment = null;

        while (reader.getCurrentPosition() < reader.length())
        {
            JpgSegmentConstants segment = fetchNextSegment(reader);

            // SOS (Start of Scan) marks the beginning of the compressed image data. Usually, no
            // metadata exists after this point except the EOI marker.
            if (segment == null || segment == JpgSegmentConstants.END_OF_IMAGE || (segment == JpgSegmentConstants.START_OF_STREAM))
            {
                break;
            }

            System.out.printf("LOOK: %s\n", segment);

            if (segment.hasLengthField())
            {
                int length = reader.readUnsignedShort() - 2;

                // Length must be between 2 and 65535 bytes (unsigned short)
                if (length < 1)
                {
                    continue;
                }

                // byte[] payload = reader.readBytes(length);

                if (segment == JpgSegmentConstants.APP1_SEGMENT)
                {
                    byte[] header = reader.peek(reader.getCurrentPosition(), length);

                    // System.out.printf("LOOK0: %s\n", ByteValueConverter.toHex(header));

                    if (header.length >= JpgParser.EXIF_IDENTIFIER.length && Arrays.equals(Arrays.copyOf(header, JpgParser.EXIF_IDENTIFIER.length), JpgParser.EXIF_IDENTIFIER))
                    {
                        byte[] payload = Arrays.copyOfRange(header, JpgParser.EXIF_IDENTIFIER.length, header.length);

                        // System.out.printf("LOOK1: %s\n", new String(payload,
                        // StandardCharsets.US_ASCII));

                        processExifSegment(reader, length, zdt);

                        continue;
                    }

                    if (header.length >= JpgParser.XMP_IDENTIFIER.length && Arrays.equals(Arrays.copyOfRange(header, 0, JpgParser.XMP_IDENTIFIER.length), JpgParser.XMP_IDENTIFIER))
                    {
                        byte[] payload = Arrays.copyOfRange(header, JpgParser.XMP_IDENTIFIER.length, header.length);
                        // System.out.printf("LOOK2: %s\n", new String(payload,
                        // StandardCharsets.US_ASCII));

                        // continue;
                    }
                }

                else
                {
                    LOGGER.debug(String.format("Unhandled segment [0xFF%02X] skipped. Length [%d]", segment.getFlag(), length));
                }

                reader.skip(length);
            }
        }
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

    private static void patchMetadataSegments(ImageRandomAccessReader reader, ZonedDateTime zdt) throws IOException
    {
        reader.seek(0);

        while (reader.getCurrentPosition() < reader.length() - 4)
        {
            if (reader.readUnsignedByte() != 0xFF)
            {
                continue;
            }

            int flag = reader.readUnsignedByte();

            // APP1 Segment (0xFFE1)
            if (flag == 0xE1)
            {
                int length = reader.readUnsignedShort() - 2;
                long startPos = reader.getCurrentPosition();
                byte[] header = reader.readBytes(JpgParser.EXIF_IDENTIFIER.length);

                System.out.printf("LOOK: %s\n", ByteValueConverter.toHex(header));

                if (Arrays.equals(header, JpgParser.EXIF_IDENTIFIER))
                {
                    // processExifSegment(reader, startPos, length, zdt);
                }

                else if (Arrays.equals(Arrays.copyOf(header, XMP_ID.length), XMP_ID))
                {
                    processXmpSegment(reader, startPos, length, zdt);
                }

                reader.seek(startPos + length);
            }

            // Start of Scan (End of metadata area)
            else if (flag == 0xDA)
            {
                break;
            }
        }
    }

    // private static void processExifSegment(ImageRandomAccessReader reader, long startPos, int
    // length, ZonedDateTime zdt) throws IOException

    private static void processExifSegment(ImageRandomAccessReader reader, int length, ZonedDateTime zdt) throws IOException
    {
        byte[] ExifPremable = JpgParser.EXIF_IDENTIFIER;
        long startPos = reader.getCurrentPosition();
        byte[] tiffPayload = JpgParser.stripExifPreamble(reader.readBytes(length));
        TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(tiffPayload);
        
        System.out.printf("LOOK2: %s\n", ByteValueConverter.toHex(tiffPayload));

        for (DirectoryIFD dir : metadata)
        {
            for (Map.Entry<Taggable, DateTimeFormatter> formatEntry : EXIF_TAG_FORMATS.entrySet())
            {
                Taggable tag = formatEntry.getKey();

                if (dir.hasTag(tag))
                {
                    System.out.printf("LOOK2: %s\n", tag);

                    EntryIFD entry = dir.getTagEntry(tag);

                    // The offset in TIFF is relative to the start of the TIFF header (startPos + 6)
                    long physicalPos = startPos + ExifPremable.length + entry.getOffset();

                    String value = zdt.format(formatEntry.getValue());

                    // Ensure we include the null terminator and match the original byte count
                    // exactly
                    byte[] dateBytes = Arrays.copyOf((value + "\0").getBytes(StandardCharsets.US_ASCII), (int) entry.getCount());

                    System.out.printf("LOOK2: %s\n", ByteValueConverter.toHex(dateBytes));
                    
                    reader.seek(physicalPos);
                    reader.write(dateBytes);

                    LOGGER.info("Patched EXIF tag [" + tag + "] at 0x" + Long.toHexString(physicalPos));
                }
            }
        }
    }

    private static void processXmpSegment(ImageRandomAccessReader reader, long startPos, int length, ZonedDateTime zdt) throws IOException
    {
        String[] xmpTags = {"xmp:CreateDate", "xmp:ModifyDate", "xmp:MetadataDate", "photoshop:DateCreated"};

        reader.seek(startPos + XMP_ID.length);
        byte[] payload = reader.readBytes(length - XMP_ID.length);
        String content = new String(payload, StandardCharsets.UTF_8);

        for (String tag : xmpTags)
        {
            int tagIdx = content.indexOf(tag);

            while (tagIdx != -1)
            {
                // Safety: ignore closing tags like </xmp:CreateDate>
                if (tagIdx > 0 && content.charAt(tagIdx - 1) != '/')
                {
                    int[] span = findValueSpan(content, tagIdx);

                    // Basic validation: ensure the span is large enough to hold a date
                    if (span != null && span[1] >= 10)
                    {
                        int vStart = span[0];
                        int vWidth = span[1];

                        String patch = (vWidth >= 25) ? zdt.format(XMP_LONG) : zdt.format(XMP_SHORT);

                        // Surgically format the patch to fit the exact width
                        byte[] finalPatch = String.format("%-" + vWidth + "s", patch).substring(0, vWidth).getBytes(StandardCharsets.UTF_8);
                        long physicalPos = startPos + XMP_ID.length + vStart;

                        reader.seek(physicalPos);
                        reader.write(finalPatch);

                        LOGGER.info("Patched XMP tag [" + tag + "] at 0x" + Long.toHexString(physicalPos));
                    }
                }

                tagIdx = content.indexOf(tag, tagIdx + tag.length());
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
}