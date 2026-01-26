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
 * Surgically patches EXIF (TIFF) and XMP (XML) dates in JPEG files.
 * Overwrites bytes inline to maintain file integrity.
 *
 * @version 1.6
 */
public final class JpgDatePatcherTest
{
    private static final LogFactory LOGGER = LogFactory.getLogger(JpgDatePatcherTest.class);
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

    private JpgDatePatcherTest()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

    public static void patchAllDates(Path imagePath, FileTime newDate) throws IOException
    {
        ZonedDateTime zdt = newDate.toInstant().atZone(ZoneId.systemDefault());

        try (ImageRandomAccessReader reader = new ImageRandomAccessReader(imagePath, ByteOrder.BIG_ENDIAN, "rw"))
        {
            long originalSize = reader.length();

            patchMetadataSegments(reader, zdt);

            if (reader.length() != originalSize)
            {
                throw new IOException("File integrity error: Surgical patch altered file size.");
            }
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

                if (length <= 0)
                {
                    continue;
                }

                long payloadStart = reader.getCurrentPosition();

                if (segment == JpgSegmentConstants.APP1_SEGMENT)
                {
                    byte[] header = reader.peek(payloadStart, Math.min(length, JpgParser.XMP_IDENTIFIER.length));

                    // 1. Handle EXIF
                    if (header.length >= JpgParser.EXIF_IDENTIFIER.length && Arrays.equals(Arrays.copyOf(header, JpgParser.EXIF_IDENTIFIER.length), JpgParser.EXIF_IDENTIFIER))
                    {
                        reader.skip(JpgParser.EXIF_IDENTIFIER.length);

                        // Detect TIFF Endianness (II vs MM)
                        ByteOrder originalOrder = reader.getByteOrder();
                        byte[] tiffMagic = reader.peek(reader.getCurrentPosition(), 2);

                        /*
                         * Because JPEG always follows the big-endianness and TIFF/Exif block can
                         * support either big endian or little endian order. Therefore, we need to
                         * make sure we honour the original byte order in order to patch information
                         * within the Tiff/Exif block, without causing corruption. We will then
                         * revert to the original byte order once patching is finished.
                         */
                        if (tiffMagic[0] == 0x49 && tiffMagic[1] == 0x49)
                        {
                            reader.setByteOrder(ByteOrder.LITTLE_ENDIAN);
                        }

                        try
                        {
                            processExifSegment(reader, length - JpgParser.EXIF_IDENTIFIER.length, zdt);
                        }

                        finally
                        {
                            // Restore to Big Endian for JPEG markers
                            reader.setByteOrder(originalOrder);
                        }
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

    private static void processExifSegment(ImageRandomAccessReader reader, int length, ZonedDateTime zdt) throws IOException
    {
        long tiffHeaderStart = reader.getCurrentPosition();
        byte[] payload = reader.readBytes(length);
        TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(payload);
        ZonedDateTime utcTime = zdt.withZoneSameInstant(ZoneId.of("UTC"));

        for (DirectoryIFD dir : metadata)
        {
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

                    LOGGER.info(String.format("Patched EXIF tag [%s] at 0x%X", tag, physicalPos));
                }
            }

            if (dir.hasTag(TagIFD_GPS.GPS_TIME_STAMP))
            {
                EntryIFD entry = dir.getTagEntry(TagIFD_GPS.GPS_TIME_STAMP);
                long physicalPos = tiffHeaderStart + entry.getOffset();
                byte[] timeBytes = new byte[24];

                ByteValueConverter.packRational(timeBytes, 0, utcTime.getHour(), 1, reader.getByteOrder());
                ByteValueConverter.packRational(timeBytes, 8, utcTime.getMinute(), 1, reader.getByteOrder());
                ByteValueConverter.packRational(timeBytes, 16, utcTime.getSecond(), 1, reader.getByteOrder());

                reader.seek(physicalPos);
                reader.write(timeBytes);
            }
        }
    }

    private static void processXmpSegment(ImageRandomAccessReader reader, int length, ZonedDateTime zdt) throws IOException
    {
        String[] xmpTags = {
                "xmp:CreateDate", "xap:CreateDate", "xmp:ModifyDate", "xap:ModifyDate",
                "xmp:MetadataDate", "xap:MetadataDate", "photoshop:DateCreated",
                "exif:DateTimeOriginal", "exif:DateTimeDigitized", "tiff:DateTime"
        };

        long startPos = reader.getCurrentPosition();
        String xml = new String(reader.readBytes(length), StandardCharsets.UTF_8);

        for (String tag : xmpTags)
        {
            int tagIdx = xml.indexOf(tag);

            while (tagIdx != -1)
            {
                // GUARD: Ensure it's not a closing tag or a namespace declaration (xmlns:xap=...)
                boolean isClosing = tagIdx > 0 && xml.charAt(tagIdx - 1) == '/';
                boolean isNamespace = tagIdx >= 6 && xml.substring(tagIdx - 6, tagIdx).contains("xmlns");

                if (!isClosing && !isNamespace)
                {
                    int[] span = findValueSpan(xml, tagIdx, tag);

                    if (span != null && span[1] >= 10)
                    {
                        int vStart = span[0];
                        int vWidth = span[1];
                        long physicalPos = startPos + vStart;

                        String patch = (span[1] >= 25) ? zdt.format(XMP_LONG) : zdt.format(XMP_SHORT);
                        byte[] finalPatch = String.format("%-" + vWidth + "s", patch).substring(0, vWidth).getBytes(StandardCharsets.UTF_8);

                        reader.seek(physicalPos);
                        reader.write(finalPatch);

                        LOGGER.info("Patched XMP tag [" + tag + "] at 0x" + Long.toHexString(physicalPos));
                    }
                }

                tagIdx = xml.indexOf(tag, tagIdx + tag.length());
            }
        }
    }

    private static int[] findValueSpan(String content, int tagIdx, String tagName)
    {
        int bracket = content.indexOf(">", tagIdx);
        int equals = content.indexOf("=", tagIdx);

        // Determine if attribute (tag="val") or element (<tag>val</tag>)
        boolean isAttr = (equals != -1 && (bracket == -1 || equals < bracket));
        int start, end;

        if (isAttr)
        {
            int quote = content.indexOf("\"", equals);

            if (quote == -1 || (quote - tagIdx) > tagName.length() + 5)
            {
                return null;
            }

            start = quote + 1;
            end = content.indexOf("\"", start);
        }

        else
        {
            if (bracket == -1) return null;
            start = bracket + 1;
            end = content.indexOf("<", start);
        }

        if (start > 0 && end > start)
        {
            // Final safety: don't patch URL strings
            String val = content.substring(start, end);

            if (val.contains("http://") || val.contains("https://"))
            {
                return null;
            }

            return new int[]{start, end - start};
        }

        return null;
    }
}