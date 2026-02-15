package tif;

import java.io.IOException;
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
import tif.DirectoryIFD.EntryIFD;
import tif.tagspecs.TagIFD_Baseline;
import tif.tagspecs.TagIFD_Exif;
import tif.tagspecs.TagIFD_GPS;
import tif.tagspecs.Taggable;

/**
 * Performs surgical patching of TIFF files. Unlike PNG or JPG, TIFF is structured as
 * a linked list of Image File Directories (IFDs). This class navigates those directories
 * to patch EXIF ASCII dates, GPS binary rationals, and XMP XML packets.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 15 February 2026
 */
public final class TiffDatePatcher2
{
    private static final LogFactory LOGGER = LogFactory.getLogger(TiffDatePatcher2.class);
    private static final DateTimeFormatter EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter GPS_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ENGLISH);

    private TiffDatePatcher2()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

    public static void patchAllDates(Path imagePath, FileTime newDate, boolean xmpDump) throws IOException
    {
        ZonedDateTime zdt = newDate.toInstant().atZone(ZoneId.systemDefault());

        try (IFDHandler handler = new IFDHandler(imagePath))
        {
            if (handler.parseMetadata())
            {
                TifMetadata metadata = new TifMetadata(handler.getTifByteOrder());

                LOGGER.info(String.format("Preparing to patch TIFF file [%s]", imagePath.getFileName()));

                try (ImageRandomAccessWriter writer = new ImageRandomAccessWriter(imagePath, metadata.getByteOrder()))
                {
                    processDirectories(metadata, writer, zdt, xmpDump);
                }
            }
        }
    }

    private static void processDirectories(TifMetadata metadata, ImageRandomAccessWriter writer, ZonedDateTime zdt, boolean xmpDump) throws IOException
    {
        Taggable[] asciiTags = {
                TagIFD_Baseline.IFD_DATE_TIME,
                TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_DATE_TIME_DIGITIZED,
                TagIFD_GPS.GPS_DATE_STAMP
        };

        for (DirectoryIFD dir : metadata)
        {
            for (Taggable tag : asciiTags)
            {
                if (dir.hasTag(tag))
                {
                    patchAsciiTag(writer, dir.getTagEntry(tag), tag, zdt);  
                }
            }

            if (dir.hasTag(TagIFD_GPS.GPS_TIME_STAMP))
            {
                patchGpsTimeStamp(writer, dir.getTagEntry(TagIFD_GPS.GPS_TIME_STAMP), zdt);
            }

            // 3. Handle XMP Packet (XML)
            if (dir.hasTag(TagIFD_Baseline.IFD_XML_PACKET))
            {
                patchXmpPacket(writer, dir.getTagEntry(TagIFD_Baseline.IFD_XML_PACKET), zdt, xmpDump);
            }
        }
    }

    private static void patchAsciiTag(ImageRandomAccessWriter writer, EntryIFD entry, Taggable tag, ZonedDateTime zdt) throws IOException
    {
        boolean isGps = (tag == TagIFD_GPS.GPS_DATE_STAMP);
        ZonedDateTime timeToUse = isGps ? zdt.withZoneSameInstant(ZoneId.of("UTC")) : zdt;
        String val = timeToUse.format(isGps ? GPS_FORMATTER : EXIF_FORMATTER);

        byte[] dateBytes = Arrays.copyOf((val + "\0").getBytes(StandardCharsets.US_ASCII), (int) entry.getCount());

        writer.seek(entry.getOffset());
        writer.writeBytes(dateBytes);

        LOGGER.info(String.format("Patched ASCII tag [%s] at offset %d", tag, entry.getOffset()));
    }

    private static void patchGpsTimeStamp(ImageRandomAccessWriter writer, EntryIFD entry, ZonedDateTime zdt) throws IOException
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

    private static void patchXmpPacket(ImageRandomAccessWriter writer, EntryIFD entry, ZonedDateTime zdt, boolean xmpDump) throws IOException
    {
        long offset = entry.getOffset();
        int length = (int) entry.getCount();

        byte[] xmpBytes = writer.peek(offset, length);
        String xmlContent = new String(xmpBytes, StandardCharsets.UTF_8);

        // Here you would reuse your processXmpSegment logic,
        // passing the writer and the specific offset for the XMP tag.
        // ... (Logic from your JpgDatePatcher.processXmpSegment) ...

        if (xmpDump)
        {
            Utils.printFastDumpXML(writer.getFilename(), xmpBytes);
        }
    }
}