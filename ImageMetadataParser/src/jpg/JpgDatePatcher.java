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
import java.util.Locale;
import common.ByteValueConverter;
import common.ImageRandomAccessWriter;
import common.Utils;
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
     * Patches all identified metadata dates within the JPEG file at the specified path, iterating
     * through JPEG markers to identify and process APP1 segments containing EXIF or XMP payloads.
     *
     * @param imagePath
     *        the {@link Path} to the JPG to be patched
     * @param newDate
     *        the new timestamp to apply to all metadata fields
     * @throws IOException
     *         if the file cannot be read, parsed, or written to
     */
    public static void patchAllDates(Path imagePath, FileTime newDate) throws IOException
    {
        patchAllDates(imagePath, newDate, false);
    }

    /**
     * Patches all identified metadata dates within the JPEG file at the specified path, iterating
     * through JPEG markers to identify and process APP1 segments containing EXIF or XMP payloads.
     *
     * @param imagePath
     *        the {@link Path} to the JPG to be patched
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

        try (ImageRandomAccessWriter writer = new ImageRandomAccessWriter(imagePath, ByteOrder.BIG_ENDIAN))
        {
            LOGGER.info(String.format("Preparing to patch new date in JPG file [%s]", imagePath));

            while (writer.getCurrentPosition() < writer.length())
            {
                JpgSegmentConstants segment = JpgParser.fetchNextSegment(writer);

                if (segment == null || segment == JpgSegmentConstants.END_OF_IMAGE || segment == JpgSegmentConstants.START_OF_STREAM)
                {
                    break;
                }

                if (segment.hasLengthField())
                {
                    int length = writer.readUnsignedShort() - 2;

                    if (length > 0)
                    {
                        long payloadStart = writer.getCurrentPosition();

                        if (segment == JpgSegmentConstants.APP1_SEGMENT)
                        {
                            byte[] header = writer.peek(payloadStart, Math.min(length, JpgParser.XMP_IDENTIFIER.length));

                            if (header.length >= JpgParser.EXIF_IDENTIFIER.length && Arrays.equals(Arrays.copyOf(header, JpgParser.EXIF_IDENTIFIER.length), JpgParser.EXIF_IDENTIFIER))
                            {
                                writer.skip(JpgParser.EXIF_IDENTIFIER.length);
                                processExifSegment(writer, length - JpgParser.EXIF_IDENTIFIER.length, zdt);
                            }

                            else if (header.length >= JpgParser.XMP_IDENTIFIER.length && Arrays.equals(Arrays.copyOf(header, JpgParser.XMP_IDENTIFIER.length), JpgParser.XMP_IDENTIFIER))
                            {
                                int xmpLength = length - JpgParser.XMP_IDENTIFIER.length;

                                // Optional diagnostic dump of XMP payload to an external XML file
                                if (xmpDump)
                                {
                                    Utils.printFastDumpXML(imagePath, writer.peek(payloadStart + JpgParser.XMP_IDENTIFIER.length, xmpLength));
                                }

                                writer.skip(JpgParser.XMP_IDENTIFIER.length);
                                processXmpSegment(writer, xmpLength, zdt);
                            }
                        }

                        writer.seek(payloadStart + length);
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
    private static void processExifSegment(ImageRandomAccessWriter writer, int length, ZonedDateTime zdt) throws IOException
    {
        Taggable[] ifdTags = {
                TagIFD_Baseline.IFD_DATE_TIME, TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_DATE_TIME_DIGITIZED, TagIFD_GPS.GPS_DATE_STAMP};

        ByteOrder currentOrder = writer.getByteOrder();
        long tiffHeaderPos = writer.getCurrentPosition();
        byte[] payload = writer.readBytes(length);
        TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(payload);

        try
        {
            writer.setByteOrder(metadata.getByteOrder());

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

                        writer.seek(physicalPos);
                        writer.writeBytes(dateBytes);

                        LOGGER.info(String.format("Date [%s (%s)] patched in EXIF tag [%s] ", value, (tag == TagIFD_GPS.GPS_DATE_STAMP ? "UTC" : "Local"), tag));
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
                    ByteValueConverter.packRational(timeBytes, 0, utc.getHour(), 1, writer.getByteOrder());
                    // Minute / 1
                    ByteValueConverter.packRational(timeBytes, 8, utc.getMinute(), 1, writer.getByteOrder());
                    // Second / 1
                    ByteValueConverter.packRational(timeBytes, 16, utc.getSecond(), 1, writer.getByteOrder());

                    writer.seek(physicalPos);
                    writer.writeBytes(timeBytes);

                    LOGGER.info(String.format("Date [%s (UTC)] patched in tag [GPS_TIME_STAMP (Rational)]", zdt.format(GPS_FORMATTER)));
                }
            }
        }

        finally
        {
            writer.setByteOrder(currentOrder);
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
    private static void processXmpSegment(ImageRandomAccessWriter writer, int length, ZonedDateTime zdt) throws IOException
    {
        String[] xmpTags = {
                "xmp:CreateDate", "xap:CreateDate", "xmp:ModifyDate", "xap:ModifyDate",
                "xmp:MetadataDate", "xap:MetadataDate", "photoshop:DateCreated",
                "exif:DateTimeOriginal", "exif:DateTimeDigitized", "tiff:DateTime"
        };

        long startPos = writer.getCurrentPosition();
        byte[] xmpBytes = writer.readBytes(length);
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
                        long physicalPos = startPos + vByteStart;
                        int slotByteWidth = xmlContent.substring(startIdx, startIdx + charLen).getBytes(StandardCharsets.UTF_8).length;
                        // String alignedPatch = Utils.alignXmpValueSlot(zdt, charLen);
                        byte[] alignedPatch = Utils.alignXmpValueSlot(zdt, slotByteWidth);

                        if (alignedPatch != null)
                        {
                            writer.seek(physicalPos);
                            // writer.writeBytes(alignedPatch.getBytes(StandardCharsets.UTF_8));
                            writer.writeBytes(alignedPatch);

                            LOGGER.info(String.format("Date [%s] patched for XMP tag [%s]", new String(alignedPatch), tag));
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
    }
}