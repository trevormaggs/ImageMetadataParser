package webp;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import common.ImageRandomAccessWriter;
import common.Utils;
import jpg.JpgParser;
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
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 9 February 2026
 */
public final class WebPDatePatcher
{
    private static final LogFactory LOGGER = LogFactory.getLogger(WebPDatePatcher.class);
    private static final DateTimeFormatter EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter GPS_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ENGLISH);
    private static final DateTimeFormatter EXIF_OFFSET_FORMATTER = DateTimeFormatter.ofPattern("xxx", Locale.ENGLISH);

    /**
     * Default constructor is unsupported and will always throw an exception.
     *
     * @throws UnsupportedOperationException
     *         to indicate that instantiation is not supported
     */
    private WebPDatePatcher()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

    /**
     * Manages the patching of all detectable date metadata within a WebP file.
     *
     * @param imagePath
     *        the path to the PNG file
     * @param newDate
     *        the replacement date and time
     * @param xmpDump
     *        indicates whether to dump XMP data into an XML-formatted file for debugging, if the
     *        data is present. If true, a file is created based on the image name
     *
     * @throws IOException
     *         if the file is inaccessible or structured incorrectly
     */
    public static void patchAllDates(Path imagePath, FileTime newDate, boolean xmpDump) throws IOException
    {
        ZonedDateTime zdt = newDate.toInstant().atZone(ZoneId.systemDefault());
        EnumSet<WebPChunkType> chunkSet = EnumSet.of(WebPChunkType.EXIF, WebPChunkType.XMP);

        try (WebpHandler handler = new WebpHandler(imagePath, chunkSet))
        {
            if (handler.parseMetadata())
            {
                LOGGER.info(String.format("Preparing to patch new date in WebP file [%s]", imagePath));

                try (ImageRandomAccessWriter writer = new ImageRandomAccessWriter(imagePath, WebpHandler.WEBP_BYTE_ORDER))
                {
                    processExifSegment(handler, writer, zdt);
                    processXmpSegment(handler, writer, zdt, xmpDump);
                }
            }
        }
    }

    /**
     * Surgically patches date tags within the eXIf chunk by parsing the embedded TIFF structure.
     * This method respects the TIFF segment's native byte order (Little vs Big Endian) while
     * performing in-place overwrites.
     *
     * <p>
     * Special handling is applied to GPS date stamps to ensure they are recorded in UTC, as
     * outlined in the GPS specification.
     * </p>
     *
     * @param handler
     *        the chunk handler containing parsed metadata segments
     * @param writer
     *        the writer used to perform the in-place modification
     * @param zdt
     *        the new date and time to be applied
     *
     * @throws IOException
     *         if an I/O error occurs whilst accessing the file or parsing the TIFF data
     */
    private static void processExifSegment(WebpHandler handler, ImageRandomAccessWriter writer, ZonedDateTime zdt) throws IOException
    {
        Taggable[] ifdTags = {
                TagIFD_Baseline.IFD_DATE_TIME, TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_DATE_TIME_DIGITIZED, TagIFD_GPS.GPS_DATE_STAMP,
                TagIFD_Exif.EXIF_OFFSET_TIME, TagIFD_Exif.EXIF_OFFSET_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_OFFSET_TIME_DIGITIZED};

        Optional<WebpChunk> optExif = handler.getChunk(WebPChunkType.EXIF);

        if (optExif.isPresent())
        {
            boolean chunkModified = false;
            WebpChunk exifChunk = optExif.get();
            byte[] payload = exifChunk.getPayloadArray();
            byte[] strippedPayload = JpgParser.stripExifPreamble(payload);
            TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(strippedPayload);
            ByteOrder originalOrder = writer.getByteOrder();
            int preambleShift = payload.length - strippedPayload.length;

            try
            {
                writer.setByteOrder(metadata.getByteOrder());

                for (DirectoryIFD dir : metadata)
                {
                    for (Taggable tag : ifdTags)
                    {
                        if (dir.hasTag(tag))
                        {
                            String value;
                            EntryIFD entry = dir.getTagEntry(tag);

                            if (tag == TagIFD_GPS.GPS_DATE_STAMP)
                            {
                                value = zdt.withZoneSameInstant(ZoneId.of("UTC")).format(GPS_FORMATTER);
                            }

                            else if (tag.toString().contains("OFFSET_TIME"))
                            {
                                value = zdt.format(EXIF_OFFSET_FORMATTER);
                            }

                            else
                            {
                                value = zdt.format(EXIF_FORMATTER);
                            }

                            byte[] dateBytes = Arrays.copyOf((value + "\0").getBytes(StandardCharsets.US_ASCII), (int) entry.getCount());

                            chunkModified = true;
                            System.arraycopy(dateBytes, 0, payload, (int) entry.getOffset() + preambleShift, dateBytes.length);
                            LOGGER.info(String.format("Prepared patch for EXIF tag [%s] with value [%s]", tag, value));
                        }
                    }
                }

                if (chunkModified)
                {
                    writer.seek(exifChunk.getDataOffset());
                    writer.writeBytes(payload);

                    LOGGER.info("Surgically patched WebP EXIF chunk");
                }
            }

            finally
            {
                writer.setByteOrder(originalOrder);
            }
        }
    }

    /**
     * Scans XML content within WebP {@code 'XMP '} chunks for date tags and performs binary
     * overwrites.
     * 
     * <p>
     * To prevent data corruption in multi-byte UTF-8 environments, this method maps string-based
     * character indices to physical byte offsets. It enforces fixed-width patching by padding
     * values with spaces, ensuring the underlying RIFF chunk size remains constant.
     * </p>
     *
     * @param handler
     *        the active chunk
     * @param writer
     *        the writer used to perform the in-place modification
     * @param zdt
     *        the target date and time to be applied
     * @param xmpDump
     *        if {@code true}, exports the raw XMP payload to a file for inspection purposes
     *
     * @throws IOException
     *         if an I/O error occurs whilst accessing the file or overwriting data
     */
    private static void processXmpSegment(WebpHandler handler, ImageRandomAccessWriter writer, ZonedDateTime zdt, boolean xmpDump) throws IOException
    {
        final String[] xmpTags = {
                "xmp:CreateDate", "xap:CreateDate", "xmp:ModifyDate", "xap:ModifyDate",
                "xmp:MetadataDate", "xap:MetadataDate", "photoshop:DateCreated",
                "exif:DateTimeOriginal", "exif:DateTimeDigitized", "tiff:DateTime"
        };

        Optional<WebpChunk> optXMP = handler.getLastChunk(WebPChunkType.XMP);

        if (optXMP.isPresent())
        {
            WebpChunk chunk = optXMP.get();
            byte[] rawPayload = chunk.getPayloadArray();
            boolean chunkModified = false;
            String xmlContent = new String(rawPayload, StandardCharsets.UTF_8);

            for (String tag : xmpTags)
            {
                int tagIdx = xmlContent.indexOf(tag);

                while (tagIdx != -1)
                {
                    if (tagIdx > 0 && xmlContent.charAt(tagIdx - 1) != '/')
                    {
                        int[] span = Utils.findValueSpan(xmlContent, tagIdx);

                        if (span != null)
                        {
                            int startIdx = span[0];
                            int charLen = span[1];
                            int slotByteWidth = xmlContent.substring(startIdx, startIdx + charLen).getBytes(StandardCharsets.UTF_8).length;
                            byte[] alignedPatch = Utils.alignXmpValueSlot(zdt, slotByteWidth);

                            if (alignedPatch != null && alignedPatch.length == slotByteWidth)
                            {
                                int vByteStart = xmlContent.substring(0, startIdx).getBytes(StandardCharsets.UTF_8).length;

                                chunkModified = true;
                                System.arraycopy(alignedPatch, 0, rawPayload, vByteStart, alignedPatch.length);

                                LOGGER.info(String.format("Date [%s] patched XMP tag [%s]", zdt.format(EXIF_FORMATTER), tag));
                            }

                            else
                            {
                                LOGGER.error(String.format("Skipped XMP tag [%s] due to insufficient slot width [%d]", tag, slotByteWidth));
                            }
                        }
                    }

                    tagIdx = xmlContent.indexOf(tag, tagIdx + tag.length());
                }
            }

            if (chunkModified)
            {
                writer.seek(chunk.getDataOffset());
                writer.writeBytes(rawPayload);

                if (xmpDump)
                {
                    Utils.printFastDumpXML(writer.getFilename(), rawPayload);
                }
            }
        }
    }
}