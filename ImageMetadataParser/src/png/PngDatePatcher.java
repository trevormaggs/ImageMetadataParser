package png;

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
import java.util.zip.CRC32;
import common.ImageRandomAccessWriter;
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
 * Provides surgical patching for PNG files by targeting specific metadata chunks. This class
 * ensures that file length remains constant and CRCs are recalculated.
 * 
 * <p>
 * This patcher maintains "Length Constancy": it uses null-termination padding for EXIF and
 * space-padding for XMP to ensure the file's internal offsets and total size remain unchanged.
 * </p>
 */
public final class PngDatePatcher
{
    private static final LogFactory LOGGER = LogFactory.getLogger(PngDatePatcher.class);
    private static final DateTimeFormatter EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter GPS_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ENGLISH);
    private static final DateTimeFormatter XMP_LONG = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter XMP_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter EXIF_OFFSET_FORMATTER = DateTimeFormatter.ofPattern("xxx", Locale.ENGLISH);

    /**
     * Default constructor is unsupported and will always throw an exception.
     *
     * @throws UnsupportedOperationException
     *         to indicate that instantiation is not supported
     */
    private PngDatePatcher()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

    public static void patchAllDates(Path imagePath, FileTime newDate, boolean xmpDump) throws IOException
    {
        ZonedDateTime zdt = newDate.toInstant().atZone(ZoneId.systemDefault());
        EnumSet<ChunkType> chunkSet = EnumSet.of(ChunkType.tEXt, ChunkType.zTXt, ChunkType.iTXt, ChunkType.eXIf, ChunkType.tIME);

        try (ChunkHandler handler = new ChunkHandler(imagePath, chunkSet))
        {
            if (handler.parseMetadata())
            {
                try (ImageRandomAccessWriter writer = new ImageRandomAccessWriter(imagePath, ChunkHandler.PNG_BYTE_ORDER))
                {
                    processExifSegment(handler, writer, zdt);
                    processXmpSegment(handler, writer, zdt, true);
                    processTimeChunk(handler, writer, zdt);
                }
            }
        }
    }

    /**
     * Surgically patches the date-related tags within the EXIF segment of the PNG file. This method
     * parses the TIFF structure embedded in the {@code eXIf} chunk, identifies specific date-time
     * tags, and overwrites them in-place.
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
    private static void processExifSegment(ChunkHandler handler, ImageRandomAccessWriter writer, ZonedDateTime zdt) throws IOException
    {
        Taggable[] ifdTags = {
                TagIFD_Baseline.IFD_DATE_TIME, TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_DATE_TIME_DIGITIZED, TagIFD_GPS.GPS_DATE_STAMP,
                TagIFD_Exif.EXIF_OFFSET_TIME, TagIFD_Exif.EXIF_OFFSET_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_OFFSET_TIME_DIGITIZED};

        Optional<PngChunk> optExif = handler.getFirstChunk(ChunkType.eXIf);

        if (optExif.isPresent())
        {
            PngChunk exifChunk = optExif.get();
            byte[] payload = exifChunk.getPayloadArray();
            TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(payload);

            ByteOrder originalOrder = writer.getByteOrder();

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
                            long physicalPos = exifChunk.getDataOffset() + entry.getOffset();

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

                            // Apply surgical write (keeping original buffer size)
                            byte[] dateBytes = Arrays.copyOf((value + "\0").getBytes(StandardCharsets.US_ASCII), (int) entry.getCount());
                            writer.seek(physicalPos);
                            writer.writeBytes(dateBytes);

                            LOGGER.info(String.format("Patched EXIF tag [%s] at 0x%X", tag, physicalPos));
                        }
                    }
                }

                updateChunkCRC(writer, exifChunk);
            }

            finally
            {
                writer.setByteOrder(originalOrder);
            }
        }
    }

    /**
     * Scans the XMP XML content for date-related tags and overwrites their values in-place.
     * <p>
     * This method performs a surgical binary overwrite of the XML content. To prevent positional
     * drift caused by multi-byte UTF-8 characters, it maps character indices to physical byte
     * offsets. If the new date string is shorter than the original, it is padded with spaces
     * to maintain a constant byte-footprint and preserve the file's structural integrity.
     * </p>
     *
     * We target the iTXt chunk, which is commonly used for XMP in PNGs
     *
     * @param handler
     *        the chunk handler containing the parsed iTXt segments
     * @param writer
     *        the writer used to perform the in-place modification
     * @param zdt
     *        the target date and time to be applied
     * @throws IOException
     *         if an I/O error occurs whilst accessing the file or overwriting data
     */
    private static void processXmpSegment(ChunkHandler handler, ImageRandomAccessWriter writer, ZonedDateTime zdt, boolean xmpDump) throws IOException
    {
        final String[] xmpTags = {
                "xmp:CreateDate", "xap:CreateDate", "xmp:ModifyDate", "xap:ModifyDate",
                "xmp:MetadataDate", "xap:MetadataDate", "photoshop:DateCreated",
                "exif:DateTimeOriginal", "exif:DateTimeDigitized", "tiff:DateTime"
        };

        Optional<PngChunk> optITxt = handler.getLastChunk(ChunkType.iTXt);

        if (optITxt.isPresent() && optITxt.get() instanceof PngChunkITXT)
        {
            boolean chunkModified = false;
            PngChunkITXT chunk = (PngChunkITXT) optITxt.get();
            byte[] rawPayload = chunk.getPayloadArray();
            String xmlContent = new String(rawPayload, StandardCharsets.UTF_8);

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
                            int vByteStart = xmlContent.substring(0, vCharStart).getBytes(StandardCharsets.UTF_8).length;
                            long physicalPos = chunk.getDataOffset() + chunk.getTextOffset() + vByteStart;
                            String alignedPatch = alignXmpValueSlot(zdt, vCharWidth);

                            if (!chunk.isCompressed() && alignedPatch != null)
                            {
                                chunkModified = true;
                                writer.seek(physicalPos);
                                writer.writeBytes(alignedPatch.getBytes(StandardCharsets.UTF_8));

                                LOGGER.info(String.format("Patched XMP tag [%s] at 0x%X", tag, physicalPos));
                            }
                        }
                    }

                    tagIdx = xmlContent.indexOf(tag, tagIdx + tag.length());
                }
            }

            if (chunkModified)
            {
                updateChunkCRC(writer, chunk);
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

    private static String alignXmpValueSlot(ZonedDateTime zdt, int slotWidth)
    {
        // Long ISO - 2026-01-28T18:30:00+11:00
        String longIso = zdt.format(XMP_LONG);

        if (longIso.length() <= slotWidth)
        {
            return String.format("%-" + slotWidth + "s", longIso);
        }

        // Short ISO - 2026-01-28T18:30:00
        String shortIso = zdt.format(XMP_SHORT);

        if (shortIso.length() <= slotWidth)
        {
            return String.format("%-" + slotWidth + "s", shortIso);
        }

        // Date Only - 2026-01-28
        if (slotWidth >= 10)
        {
            return String.format("%-" + slotWidth + "s", shortIso.split("T")[0]);
        }

        LOGGER.warn(String.format("XMP slot width [%d] is too small for date patching.", slotWidth));

        return null;
    }

    /**
     * Updates the CRC checksum for a specific chunk to ensure the file remains valid after a
     * surgical patch. The calculation encompasses both the 4-byte Type identifier and the modified
     * Data segment.
     *
     * <p>
     * It ensures the byte order is set to Big Endian for the CRC write operation, as outlined in
     * the PNG specification, before reverting to the original byte order to maintain logic
     * integrity.
     * </p>
     *
     * @param writer
     *        the file writer positioned for patching
     * @param chunk
     *        the {@link PngChunk} being updated
     *
     * @throws IOException
     *         if an I/O error occurs whilst accessing the file stream
     */
    private static void updateChunkCRC(ImageRandomAccessWriter writer, PngChunk chunk) throws IOException
    {
        writer.seek(chunk.getFileOffset() + 4);

        CRC32 crcCalculator = new CRC32();
        byte[] crcSegment = writer.readBytes(4 + (int) chunk.getLength());

        crcCalculator.update(crcSegment);

        long newCrc = crcCalculator.getValue();
        ByteOrder originalOrder = writer.getByteOrder();

        try
        {
            writer.setByteOrder(ByteOrder.BIG_ENDIAN);
            writer.seek(chunk.getDataOffset() + chunk.getLength());
            writer.writeInteger((int) newCrc);

            LOGGER.debug(String.format("Updated CRC for %s at 0x%X: 0x%08X", chunk.getType(), (writer.getCurrentPosition() - 4), newCrc));
        }

        finally
        {
            writer.setByteOrder(originalOrder);
        }
    }

    private static void processTimeChunk(ChunkHandler handler, ImageRandomAccessWriter writer, ZonedDateTime zdt) throws IOException
    {
        Optional<PngChunk> optTime = handler.getFirstChunk(ChunkType.tIME);

        if (optTime.isPresent())
        {
            PngChunk timeChunk = optTime.get();
            writer.seek(timeChunk.getDataOffset());

            ByteOrder originalOrder = writer.getByteOrder();
            writer.setByteOrder(ByteOrder.BIG_ENDIAN);

            try
            {
                writer.writeShort((short) zdt.getYear());
                writer.writeByte((byte) zdt.getMonthValue());
                writer.writeByte((byte) zdt.getDayOfMonth());
                writer.writeByte((byte) zdt.getHour());
                writer.writeByte((byte) zdt.getMinute());
                writer.writeByte((byte) zdt.getSecond());

                updateChunkCRC(writer, timeChunk);

                LOGGER.info("Surgically patched PNG tIME chunk.");
            }

            finally
            {
                writer.setByteOrder(originalOrder);
            }
        }
    }

    /**
     * Extracts and saves the XMP payload to a standalone file for structural analysis.
     * 
     * @param imagePath
     *        the original image path to derive the dump filename
     * @param payload
     *        the raw bytes of the iTXt chunk data
     */
    @Deprecated
    private static void dumpXmpToDisk(Path imagePath, byte[] payload)
    {
        try
        {
            Path dumpPath = imagePath.resolveSibling(imagePath.getFileName() + ".xmp.xml");
            java.nio.file.Files.write(dumpPath, payload);
            LOGGER.info("XMP payload dumped to: " + dumpPath.toAbsolutePath());
        }

        catch (IOException e)
        {
            LOGGER.error("Failed to dump XMP payload: " + e.getMessage());
        }
    }
}