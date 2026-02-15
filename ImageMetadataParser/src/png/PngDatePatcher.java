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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.CRC32;
import common.ImageRandomAccessWriter;
import common.Utils;
import logger.LogFactory;
import tif.DirectoryIFD;
import tif.DirectoryIFD.EntryIFD;
import tif.TagHint;
import tif.TifMetadata;
import tif.TifParser;
import tif.tagspecs.TagIFD_Baseline;
import tif.tagspecs.TagIFD_Exif;
import tif.tagspecs.TagIFD_GPS;
import tif.tagspecs.Taggable;

/**
 * Performs surgical patching of PNG files by targeting specific metadata chunks (eXIf, iTXt, tIME,
 * and tEXt).
 *
 * <p>
 * This class performs surgical updates that preserve the original file size. It maintains
 * structural integrity by padding new dates with nulls (EXIF) or spaces (XMP) to match the existing
 * byte count. Every change triggers an automatic CRC recalculation to ensure the PNG remains
 * technically valid.
 * </p>
 * 
 * <pre>
    <b>*** Tips for exiftool commands ***</b>
 
    Delete all XMP items
    exiftool -XMP:all= XMPimage.png 
    
    Add dates to XMP
    exiftool.exe -all= -XMP-exif:DateTimeOriginal="2024:01:01 12:00:00" -XMP-xmp:CreateDate="2024:01:01 12:00:00"  -XMP-xmp:ModifyDate="2024:01:01 12:00:00" XMPimage.png
                            OR
    exiftool.exe "-XMP:CreateDate=2001:11:12 10:10:10" XMPimage.png
    exiftool.exe "-XMP:ModifyDate=2001:11:12 10:10:10" XMPimage.png 
    exiftool.exe "-XMP-exif:DateTimeOriginal=2004:11:12 10:10:10" XMPimage.png
        
    List metadata
    exiftool.exe -G -a -s XMPimage.png    
    
    Check if tIME chunk exists
    exiftool.exe -PNG:ModificationTime -G1 -s XMPimage.png  
    
    Add ModifyDate to tIME chunk 
    exiftool.exe "-PNG:ModifyDate=2023:10:10 10:10:10" XMPimage.png
    
    Add to textual chunk for CreateDate
    exiftool.exe "-PNG:CreationTime=2023:10:10 10:10:10" XMPimage.png
    
    exiftool.exe -AllDates="2023:10:10 10:10:10" -PNG:ModifyDate="2023:10:10 10:10:10" -PNG:CreationTime="2023:10:10 10:10:10" XMPimage.png
    
    Print out all time entries
    exiftool.exe -a -G1 -s -Time:All XMPimage.png
 * </pre>
 *
 * @author Trevor Maggs
 * @version 1.1
 * @since 5 February 2026
 */
public final class PngDatePatcher
{
    private static final LogFactory LOGGER = LogFactory.getLogger(PngDatePatcher.class);
    private static final DateTimeFormatter EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter GPS_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ENGLISH);
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

    /**
     * Manages the patching of all detectable date metadata within a PNG file.
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
        EnumSet<ChunkType> chunkSet = EnumSet.of(ChunkType.tEXt, ChunkType.iTXt, ChunkType.eXIf, ChunkType.tIME);

        try (ChunkHandler handler = new ChunkHandler(imagePath, chunkSet))
        {
            if (handler.parseMetadata())
            {
                LOGGER.info(String.format("Preparing to patch new date in PNG file [%s]", imagePath));

                try (ImageRandomAccessWriter writer = new ImageRandomAccessWriter(imagePath, ChunkHandler.PNG_BYTE_ORDER))
                {
                    processExifSegment(handler, writer, zdt);
                    processXmpSegment(handler, writer, zdt, xmpDump);
                    processTimeSegment(handler, writer, zdt);
                    processTextualChunk(handler, writer, zdt);
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
    private static void processExifSegment(ChunkHandler handler, ImageRandomAccessWriter writer, ZonedDateTime zdt) throws IOException
    {
        Taggable[] targetTags = {
                TagIFD_Baseline.IFD_DATE_TIME, TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_DATE_TIME_DIGITIZED, TagIFD_GPS.GPS_DATE_STAMP,
                TagIFD_Exif.EXIF_OFFSET_TIME, TagIFD_Exif.EXIF_OFFSET_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_OFFSET_TIME_DIGITIZED};

        Optional<PngChunk> optExif = handler.getFirstChunk(ChunkType.eXIf);

        if (optExif.isPresent())
        {
            PngChunk exifChunk = optExif.get();
            byte[] payload = exifChunk.getPayloadArray();
            boolean chunkModified = false;
            ByteOrder originalOrder = writer.getByteOrder();
            TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(payload);

            try
            {
                writer.setByteOrder(metadata.getByteOrder());

                for (DirectoryIFD dir : metadata)
                {
                    for (Taggable tag : targetTags)
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
                            System.arraycopy(dateBytes, 0, payload, (int) entry.getOffset(), dateBytes.length);
                            LOGGER.info(String.format("Prepared patch for EXIF tag [%s] with value [%s]", tag, value));
                        }
                    }
                }

                if (chunkModified)
                {
                    writer.seek(exifChunk.getDataOffset());
                    writer.writeBytes(payload);

                    updateChunkCRC(writer, exifChunk, payload);
                    LOGGER.info("Surgically patched eXIf chunk with optimized single-write I/O.");
                }
            }

            finally
            {
                writer.setByteOrder(originalOrder);
            }
        }
    }

    /**
     * Scans XML content within {@code iTXt} chunks for date tags and performs binary overwrites. It
     * maps character indices to physical byte offsets to prevent drift caused by multi-byte UTF-8
     * characters and pads shorter strings with spaces to maintain fixed offsets.
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
            PngChunkITXT chunk = (PngChunkITXT) optITxt.get();
            String xmlContent = chunk.getText();
            byte[] rawPayload = chunk.getPayloadArray();
            boolean chunkModified = false;

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

                            if (!chunk.isCompressed() && alignedPatch != null)
                            {
                                int vByteStart = xmlContent.substring(0, startIdx).getBytes(StandardCharsets.UTF_8).length;

                                chunkModified = true;
                                System.arraycopy(alignedPatch, 0, rawPayload, (int) (chunk.getTextOffset() + vByteStart), alignedPatch.length);
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
                updateChunkCRC(writer, chunk, rawPayload);

                if (xmpDump)
                {
                    byte[] xml = Arrays.copyOfRange(rawPayload, (int) chunk.getTextOffset(), rawPayload.length);
                    Utils.printFastDumpXML(writer.getFilename(), xml);
                }
            }
        }
    }

    /**
     * Surgically patches the standard PNG {@code tIME} chunk using a 7-byte big-endian
     * representation.
     * 
     * @param handler
     *        the PNG chunk handler containing parsed chunks
     * @param writer
     *        the writer used for in-place modification
     * @param zdt
     *        the new date and time to apply
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    private static void processTimeSegment(ChunkHandler handler, ImageRandomAccessWriter writer, ZonedDateTime zdt) throws IOException
    {
        Optional<PngChunk> optTime = handler.getFirstChunk(ChunkType.tIME);

        if (optTime.isPresent())
        {
            PngChunk chunk = optTime.get();
            byte[] timePayload = new byte[7];

            int year = zdt.getYear();
            timePayload[0] = (byte) ((year >> 8) & 0xFF);
            timePayload[1] = (byte) (year & 0xFF);
            timePayload[2] = (byte) zdt.getMonthValue();
            timePayload[3] = (byte) zdt.getDayOfMonth();
            timePayload[4] = (byte) zdt.getHour();
            timePayload[5] = (byte) zdt.getMinute();
            timePayload[6] = (byte) zdt.getSecond();

            writer.seek(chunk.getDataOffset());
            writer.writeBytes(timePayload);

            updateChunkCRC(writer, chunk, timePayload);
            LOGGER.info("Date [" + zdt.format(EXIF_FORMATTER) + "] patched in chunk [tIME (ModifyDate)]");
        }
    }

    /**
     * Surgically patches standard tEXt chunks to update date-related keywords.
     *
     * <p>
     * This method identifies chunks like {@code Creation Time}, performs a binary overwrite within
     * the existing slot width using ISO-8859-1 encoding, and updates the CRC.
     * </p>
     *
     * @param handler
     *        the metadata handler containing parsed chunks
     * @param writer
     *        the writer used for in-place modification
     * @param zdt
     *        the new date and time to apply
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    private static void processTextualChunk(ChunkHandler handler, ImageRandomAccessWriter writer, ZonedDateTime zdt) throws IOException
    {
        Optional<List<PngChunk>> optText = handler.getChunks(ChunkType.tEXt);

        if (optText.isPresent())
        {
            for (PngChunk ref : optText.get())
            {
                if (ref instanceof PngChunkTEXT)
                {
                    PngChunkTEXT chunk = (PngChunkTEXT) ref;
                    TextKeyword tk = TextKeyword.fromIdentifierString(chunk.getKeyword());

                    if (tk.getHint() == TagHint.HINT_DATE)
                    {
                        // PNG spec: tEXt is [Keyword][0x00][Value]
                        int valueOffset = chunk.getKeyword().length() + 1;
                        int slotWidth = (int) (chunk.getLength() - valueOffset);

                        if (slotWidth >= 10)
                        {
                            String dateString = zdt.format(EXIF_FORMATTER);

                            if (slotWidth < dateString.length())
                            {
                                dateString = zdt.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
                            }

                            String patchString = String.format("%-" + slotWidth + "s", dateString);
                            byte[] patchBytes = patchString.getBytes(StandardCharsets.ISO_8859_1);

                            if (patchBytes.length == slotWidth)
                            {
                                byte[] rawPayload = chunk.getPayloadArray();
                                long physicalPos = chunk.getDataOffset() + valueOffset;

                                System.arraycopy(patchBytes, 0, rawPayload, valueOffset, patchBytes.length);
                                writer.seek(physicalPos);
                                writer.writeBytes(patchBytes);
                                updateChunkCRC(writer, chunk, rawPayload);

                                LOGGER.info(String.format("Date [%s] patched for keyword [%s] in chunk [%s]", dateString, chunk.getKeyword(), chunk.getType().getName()));
                            }

                            else
                            {
                                LOGGER.warn(String.format("Skipping [%s]. Slot too small [%d]", chunk.getKeyword(), slotWidth));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates the CRC checksum for a specific chunk to ensure the file remains valid after a
     * surgical patch. The calculation encompasses both the 4-byte Type identifier and the modified
     * Data segment.
     *
     * <p>
     * This method ensures the byte order is set to Big Endian for the CRC write operation, as
     * defined by the PNG specification, before reverting to the original byte order to maintain
     * logical consistency.
     * </p>
     *
     * @param writer
     *        the file writer positioned for patching
     * @param chunk
     *        the {@link PngChunk} being updated
     * @param updatedPayload
     *        the newly patched payload
     *
     * @throws IOException
     *         if an I/O error occurs whilst accessing the file stream
     */
    private static void updateChunkCRC(ImageRandomAccessWriter writer, PngChunk chunk, byte[] updatedPayload) throws IOException
    {
        CRC32 crcCalculator = new CRC32();

        crcCalculator.update(chunk.getTypeBytes());
        crcCalculator.update(updatedPayload);

        long newCrc = crcCalculator.getValue();
        ByteOrder originalOrder = writer.getByteOrder();

        try
        {
            writer.setByteOrder(ByteOrder.BIG_ENDIAN);
            writer.seek(chunk.getDataOffset() + chunk.getLength());
            writer.writeInteger((int) newCrc);

            LOGGER.info(String.format("CRC [0x%08X] updated in %s chunk", newCrc, chunk.getType()));
        }

        finally
        {
            writer.setByteOrder(originalOrder);
        }
    }
}