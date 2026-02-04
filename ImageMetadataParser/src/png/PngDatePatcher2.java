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
 * Provides surgical patching for PNG files by targeting specific metadata chunks (eXIf, iTXt,
 * tIME).
 * 
 * <p>
 * This class performs in-place binary modifications to date-related metadata while maintaining
 * "Length Constancy." It ensures that the file's internal offsets remain valid by using
 * null-termination padding for EXIF fields and space-padding for XMP fields. After every
 * modification, the Cyclic Redundancy Check (CRC) for the affected chunk is recalculated and
 * updated according to the PNG specification.
 * </p>
 */
public final class PngDatePatcher2
{
    private static final LogFactory LOGGER = LogFactory.getLogger(PngDatePatcher.class);

    // Formatters for different metadata specifications
    private static final DateTimeFormatter EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter EXIF_OFFSET_FORMATTER = DateTimeFormatter.ofPattern("xxx", Locale.ENGLISH);
    private static final DateTimeFormatter GPS_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ENGLISH);
    private static final DateTimeFormatter XMP_LONG = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter XMP_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private PngDatePatcher2()
    {
        throw new UnsupportedOperationException("Utility class: Not intended for instantiation");
    }

    /**
     * Orchestrates the patching of all detectable date metadata within a PNG file.
     *
     * @param imagePath
     *        the path to the PNG file
     * @param newDate
     *        the replacement date and time
     * @param xmpDump
     *        (unused in this version) toggle for diagnostic XMP dumping
     * 
     * @throws IOException
     *         if the file is inaccessible or structured incorrectly
     */
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
                    processXmpSegment(handler, writer, zdt);
                    processTimeChunk(handler, writer, zdt);
                }
            }
        }
    }

    /**
     * Surgically patches date tags within the eXIf chunk by parsing the embedded TIFF structure.
     * This method respects the TIFF segment's native byte order (Little vs Big Endian) while
     * performing in-place overwrites.
     */
    private static void processExifSegment(ChunkHandler handler, ImageRandomAccessWriter writer, ZonedDateTime zdt) throws IOException
    {
        Taggable[] ifdTags = {
                TagIFD_Baseline.IFD_DATE_TIME, TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_DATE_TIME_DIGITIZED, TagIFD_GPS.GPS_DATE_STAMP,
                TagIFD_Exif.EXIF_OFFSET_TIME, TagIFD_Exif.EXIF_OFFSET_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_OFFSET_TIME_DIGITIZED
        };

        Optional<PngChunk> optExif = handler.getFirstChunk(ChunkType.eXIf);

        if (optExif.isPresent())
        {
            PngChunk exifChunk = optExif.get();
            TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(exifChunk.getPayloadArray());
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
                                // GPS dates must be UTC per EXIF spec
                                value = zdt.withZoneSameInstant(ZoneId.of("UTC")).format(GPS_FORMATTER);
                            }

                            else if (tag.toString().contains("OFFSET_TIME"))
                            {
                                // Handle iPhone specific timezone offsets (+11:00)
                                value = zdt.format(EXIF_OFFSET_FORMATTER);
                            }

                            else
                            {
                                value = zdt.format(EXIF_FORMATTER);
                            }

                            // Keep byte count identical to prevent structure shift
                            byte[] dateBytes = Arrays.copyOf((value + "\0").getBytes(StandardCharsets.US_ASCII), (int) entry.getCount());
                            writer.seek(physicalPos);
                            writer.writeBytes(dateBytes);

                            LOGGER.info(String.format("Patched EXIF tag [%s] at 0x%X with value [%s]", tag, physicalPos, value));
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
     * Scans XMP XML content within iTXt or tEXt chunks for date tags and performs binary
     * overwrites. It maps character indices to physical byte offsets to prevent drift caused by
     * multi-byte UTF-8 characters and pads shorter strings with spaces to maintain fixed offsets.
     */
    private static void processXmpSegment(ChunkHandler handler, ImageRandomAccessWriter writer, ZonedDateTime zdt) throws IOException
    {
        final String[] xmpTags = {
                "xmp:CreateDate", "xap:CreateDate", "xmp:ModifyDate", "xap:ModifyDate",
                "xmp:MetadataDate", "xap:MetadataDate", "photoshop:DateCreated",
                "exif:DateTimeOriginal", "exif:DateTimeDigitized", "tiff:DateTime"
        };

        // Scan both iTXt (modern/iPhone) and tEXt (legacy fallback)
        for (ChunkType type : new ChunkType[]{ChunkType.iTXt, ChunkType.tEXt})
        {
            for (PngChunk chunk : handler.getChunks(type))
            {
                boolean chunkModified = false;
                String xmlContent = new String(chunk.getPayloadArray(), StandardCharsets.UTF_8);
                int textOffset = (chunk instanceof PngChunkITXT) ? ((PngChunkITXT) chunk).getTextOffset() : 0;

                for (String tag : xmpTags)
                {
                    int tagIdx = xmlContent.indexOf(tag);
                    while (tagIdx != -1)
                    {
                        // Ignore closing XML tags
                        if (tagIdx > 0 && xmlContent.charAt(tagIdx - 1) != '/')
                        {
                            int[] span = findValueSpan(xmlContent, tagIdx);
                            if (span != null && span[1] >= 10)
                            {
                                int vCharStart = span[0];
                                int vCharWidth = span[1];

                                // Calculate byte offset to account for potential UTF-8 multi-byte
                                // chars before the tag
                                int vByteStart = xmlContent.substring(0, vCharStart).getBytes(StandardCharsets.UTF_8).length;
                                long physicalPos = chunk.getDataOffset() + textOffset + vByteStart;
                                String alignedPatch = alignXmpValueSlot(zdt, vCharWidth);

                                if (!chunk.isCompressed() && alignedPatch != null)
                                {
                                    chunkModified = true;
                                    writer.seek(physicalPos);
                                    writer.writeBytes(alignedPatch.getBytes(StandardCharsets.UTF_8));
                                    LOGGER.info(String.format("Patched XMP tag [%s] in %s at 0x%X", tag, type, physicalPos));
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
    }

    /**
     * Locates the value within an XMP tag, handling both attribute-style and element-style serialisation.
     * 
     * @return [startIndex, length] or null if the span is invalid
     */
    private static int[] findValueSpan(String content, int tagIdx)
    {
        int start, end;
        int equals = content.indexOf("=", tagIdx);
        int bracket = content.indexOf(">", tagIdx);

        if (equals != -1 && (bracket == -1 || equals < bracket)) // Attribute style: tag="value"
        {
            int quote = content.indexOf("\"", equals);
            if (quote == -1) return null;
            
            start = quote + 1;
            end = content.indexOf("\"", start);
        }
        
        else if (bracket != -1) // Element style: <tag>value</tag>
        {
            start = bracket + 1;
            end = content.indexOf("<", start);
        }
        
        else return null;

        return ((start > 0 && end > start) ? new int[]{start, end - start} : null);
    }

    /**
     * Formats the date to fit the existing XMP slot width, falling back to shorter ISO variations
     * or space-padding to prevent binary shifting.
     */
    private static String alignXmpValueSlot(ZonedDateTime zdt, int slotWidth)
    {
        String longIso = zdt.format(XMP_LONG);
        
        if (longIso.length() <= slotWidth) return String.format("%-" + slotWidth + "s", longIso);

        String shortIso = zdt.format(XMP_SHORT);
        
        if (shortIso.length() <= slotWidth) return String.format("%-" + slotWidth + "s", shortIso);

        if (slotWidth >= 10) return String.format("%-" + slotWidth + "s", shortIso.split("T")[0]);

        LOGGER.warn("XMP slot width too small for date patching: " + slotWidth);
        
        return null;
    }

    /**
     * Updates the CRC-32 checksum for a chunk. The CRC is calculated over the 4-byte Type field and the entire Data field. Written as a Big Endian unsigned 32-bit integer.
     */
    private static void updateChunkCRC(ImageRandomAccessWriter writer, PngChunk chunk) throws IOException
    {
        writer.seek(chunk.getFileOffset() + 4); // Position at Chunk Type
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
            LOGGER.debug(String.format("Updated CRC for %s: 0x%08X", chunk.getType(), newCrc));
        }
        
        finally
        {
            writer.setByteOrder(originalOrder);
        }
    }

    /**
     * Surgically patches the standard PNG tIME chunk using a 7-byte big-endian representation.
     */
    private static void processTimeChunk(ChunkHandler handler, ImageRandomAccessWriter writer, ZonedDateTime zdt) throws IOException
    {
        handler.getFirstChunk(ChunkType.tIME).ifPresent(timeChunk ->
        {
            ByteOrder originalOrder = writer.getByteOrder();
            
            try
            {
                writer.seek(timeChunk.getDataOffset());
                writer.setByteOrder(ByteOrder.BIG_ENDIAN);
                writer.writeShort((short) zdt.getYear());
                writer.writeByte((byte) zdt.getMonthValue());
                writer.writeByte((byte) zdt.getDayOfMonth());
                writer.writeByte((byte) zdt.getHour());
                writer.writeByte((byte) zdt.getMinute());
                writer.writeByte((byte) zdt.getSecond());

                updateChunkCRC(writer, timeChunk);
                LOGGER.info("Surgically patched native PNG tIME chunk.");
            }
            
            catch (IOException e)
            {
                LOGGER.error("Failed to patch tIME chunk: " + e.getMessage());
            }
            
            finally
            {
                writer.setByteOrder(originalOrder);
            }
        });
    }
}