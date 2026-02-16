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
import tif.tagspecs.TagIFD_GPS;
import tif.tagspecs.Taggable;

/**
 * Performs surgical patching of TIFF files. Unlike PNG or JPG, TIFF is structured as a linked list
 * of Image File Directories (IFDs). This class navigates those directories to patch EXIF ASCII
 * dates, GPS binary rationals, and XMP XML packets.
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

    private TiffDatePatcher()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

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

                        // Patch ASCII tags
                        for (Taggable tag : asciiTags)
                        {
                            // O(1) Map lookup - much faster than scanning every entry in the IFD
                            EntryIFD entry = dir.getTagEntry(tag);

                            if (entry != null)
                            {
                                processExifSegment(writer, entry, zdt);
                            }
                        }

                        // Patch GPS Rational Time
                        EntryIFD entry = dir.getTagEntry(TagIFD_GPS.GPS_TIME_STAMP);

                        if (entry != null)
                        {
                            processExifGpsTimeStamp(writer, entry, zdt);
                        }

                        // Patch XMP Packet
                        if (!xmpProcessed && dir.hasTag(TagIFD_Baseline.IFD_XML_PACKET))
                        {
                            xmpProcessed = true;
                            processXmpSegment(writer, dir.getTagEntry(TagIFD_Baseline.IFD_XML_PACKET), zdt, xmpDump);
                        }
                    }
                }
            }
        }
    }

    private static void processExifSegment(ImageRandomAccessWriter writer, EntryIFD entry, ZonedDateTime zdt) throws IOException
    {
        Taggable tag = entry.getTag();
        ZonedDateTime updatedTime = zdt;
        DateTimeFormatter formatter = EXIF_FORMATTER;

        if (tag == TagIFD_GPS.GPS_DATE_STAMP)
        {
            // Logic shift: GPS tags must be UTC, others remain local
            updatedTime = zdt.withZoneSameInstant(ZoneId.of("UTC"));
            formatter = GPS_FORMATTER;
        }

        String value = updatedTime.format(formatter);
        int slotWidthLimit = (int) entry.getCount();
        byte[] dateBytes = Arrays.copyOf((value + "\0").getBytes(StandardCharsets.US_ASCII), slotWidthLimit);

        // Force null termination
        dateBytes[slotWidthLimit - 1] = 0;

        writer.seek(entry.getOffset());
        writer.writeBytes(dateBytes);

        LOGGER.info(String.format("Patched ASCII tag [%s] at offset %d", tag, entry.getOffset()));
    }

    private static void processExifGpsTimeStamp(ImageRandomAccessWriter writer, EntryIFD entry, ZonedDateTime zdt) throws IOException
    {
        byte[] timeBytes = new byte[24];
        ZonedDateTime utc = zdt.withZoneSameInstant(ZoneId.of("UTC"));

        ByteValueConverter.packRational(timeBytes, 0, utc.getHour(), 1, writer.getByteOrder());
        ByteValueConverter.packRational(timeBytes, 8, utc.getMinute(), 1, writer.getByteOrder());
        ByteValueConverter.packRational(timeBytes, 16, utc.getSecond(), 1, writer.getByteOrder());

        writer.seek(entry.getOffset());
        writer.writeBytes(timeBytes);

        LOGGER.info(String.format("Patched GPS_TIME_STAMP rational at offset %d", entry.getOffset()));
    }

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

                            System.out.printf("LOOK: %s\n", new String(alignedPatch));

                            LOGGER.info(String.format("\t-> Patched XMP tag [%s] at offset %d", tag, physicalPos));
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