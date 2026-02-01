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
 * Provides surgical patching for PNG files by targeting specific metadata chunks. This class
 * ensures that file length remains constant and CRCs are recalculated.
 */
public final class PngDatePatcher
{
    private static final LogFactory LOGGER = LogFactory.getLogger(PngDatePatcher.class);
    private static final DateTimeFormatter EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter GPS_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ENGLISH);
    private static final DateTimeFormatter XMP_LONG = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter XMP_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

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
                try (ImageRandomAccessReader reader = new ImageRandomAccessReader(imagePath, ChunkHandler.PNG_BYTE_ORDER, "rw"))
                {
                    processExifSegment(handler, reader, zdt);
                    // processXmpSegment(handler, zdt);
                }
            }
        }
    }

    private static void processExifSegment(ChunkHandler handler, ImageRandomAccessReader reader, ZonedDateTime zdt) throws IOException
    {
        Taggable[] ifdTags = {
                TagIFD_Baseline.IFD_DATE_TIME, TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL,
                TagIFD_Exif.EXIF_DATE_TIME_DIGITIZED, TagIFD_GPS.GPS_DATE_STAMP};

        Optional<PngChunk> optExif = handler.getFirstChunk(ChunkType.eXIf);

        if (optExif.isPresent())
        {
            PngChunk exifChunk = optExif.get();
            byte[] payload = exifChunk.getPayloadArray();
            TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(payload);

            ByteOrder originalOrder = reader.getByteOrder();
            try
            {
                reader.setByteOrder(metadata.getByteOrder());

                for (DirectoryIFD dir : metadata)
                {
                    for (Taggable tag : ifdTags)
                    {
                        if (dir.hasTag(tag))
                        {
                            ZonedDateTime updatedTime = zdt;
                            DateTimeFormatter formatter = EXIF_FORMATTER;
                            EntryIFD entry = dir.getTagEntry(tag);

                            // Calculate physical position: Chunk Data Start + TIFF Relative Offset
                            long physicalPos = exifChunk.getDataOffset() + entry.getOffset();

                            if (tag == TagIFD_GPS.GPS_DATE_STAMP)
                            {
                                // Logic shift: GPS tags must be UTC, others remain local
                                updatedTime = zdt.withZoneSameInstant(ZoneId.of("UTC"));
                                formatter = GPS_FORMATTER;
                            }

                            String value = updatedTime.format(formatter);
                            byte[] dateBytes = Arrays.copyOf((value + "\0").getBytes(StandardCharsets.US_ASCII), (int) entry.getCount());

                            reader.seek(physicalPos);
                            reader.write(dateBytes);

                            LOGGER.info(String.format("Patched EXIF tag [%s] at 0x%X", tag, physicalPos));
                        }
                    }
                }

                // AFTER all tags are patched, we must update the Chunk's CRC
                updateChunkCRC(reader, exifChunk);

            }

            finally
            {
                reader.setByteOrder(originalOrder);
            }
        }
    }

    private static void updateChunkCRC(ImageRandomAccessReader reader, PngChunk chunk) throws IOException
    {
        CRC32 crcCalculator = new CRC32();

        // 1. CRC includes the Chunk Type (4 bytes)
        crcCalculator.update(chunk.getChunkBytes());

        // 2. CRC includes the Data Field
        reader.seek(chunk.getDataOffset());
        byte[] updatedData = reader.readBytes((int) chunk.getLength());
        crcCalculator.update(updatedData);

        int newCrc = (int) crcCalculator.getValue();

        // 3. Write CRC immediately after data
        // Data Offset + Length points to the start of the 4-byte CRC field
        reader.seek(chunk.getDataOffset() + chunk.getLength());
        reader.writeInt(newCrc);

        LOGGER.debug(String.format("Updated CRC for %s chunk to 0x%08X", chunk.getType(), newCrc));
    }
}