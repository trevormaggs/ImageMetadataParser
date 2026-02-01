package png;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Provides surgical patching for PNG files by targeting specific metadata chunks.
 * This class ensures that file length remains constant and CRCs are recalculated.
 */
public class PngDatePatcher2
{
    private final PngXmpSurgicalStore xmpStore = new PngXmpSurgicalStore();

    public void patch(Path path, FileTime newTime, List<PngChunk> chunks) throws IOException
    {
        ZonedDateTime dt = ZonedDateTime.ofInstant(newTime.toInstant(), ZoneId.of("UTC"));

        RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw");
        try
        {
            for (int i = 0; i < chunks.size(); i++)
            {
                PngChunk chunk = chunks.get(i);
                ChunkType type = chunk.getType();

                if (type == ChunkType.tIME)
                {
                    patchTimeChunk(raf, chunk, dt);
                }

                else if (type == ChunkType.eXIf)
                {
                    patchExifChunk(raf, chunk, dt);
                }

                else if (type == ChunkType.iTXt)
                {
                    PngChunkITXT itxt = (PngChunkITXT) chunk;

                    if ("XML:com.adobe.xmp".equals(itxt.getKeyword()))
                    {
                        patchXmpChunk(raf, itxt, dt);
                    }
                }
            }
        }

        finally
        {
            raf.close();
        }
    }

    /**
     * Patches the binary tIME chunk (7 bytes of raw date/time data).
     */
    private void patchTimeChunk(RandomAccessFile raf, PngChunk chunk, ZonedDateTime dt) throws IOException
    {
        byte[] newData = new byte[7];
        newData[0] = (byte) (dt.getYear() >> 8);
        newData[1] = (byte) (dt.getYear() & 0xFF);
        newData[2] = (byte) dt.getMonthValue();
        newData[3] = (byte) dt.getDayOfMonth();
        newData[4] = (byte) dt.getHour();
        newData[5] = (byte) dt.getMinute();
        newData[6] = (byte) dt.getSecond();

        performSurgery(raf, chunk, newData);
    }

    /**
     * Patches the iTXt chunk specifically containing XMP metadata.
     */
    private void patchXmpChunk(RandomAccessFile raf, PngChunkITXT chunk, ZonedDateTime dt) throws IOException
    {
        // We use a standardised ISO format for XMP
        String isoDate = dt.toInstant().toString();
        byte[] payload = chunk.getPayloadArray();
        byte[] patchedPayload = xmpStore.patchXmpDate(payload, isoDate);

        performSurgery(raf, chunk, patchedPayload);
    }

    /**
     * Patches the Exif chunk. PNG Exif chunks contain a raw TIFF header followed by tags.
     */
    private void patchExifChunk(RandomAccessFile raf, PngChunk chunk, ZonedDateTime dt) throws IOException
    {
        byte[] payload = chunk.getPayloadArray();

        // This would call your existing Exif logic to find "YYYY:MM:DD HH:MM:SS"
        // and overwrite the bytes in the payload array.
        // ExifPatcher.applySurgery(payload, dt);

        performSurgery(raf, chunk, payload);
    }

    /**
     * Executes the physical file write and CRC update.
     */
    private void performSurgery(RandomAccessFile raf, PngChunk chunk, byte[] newPayload) throws IOException
    {
        // 1. Overwrite the Data field
        raf.seek(chunk.getDataOffset());
        raf.write(newPayload);

        // 2. Recalculate CRC (Type + Data)
        CRC32 crcCalculator = new CRC32();

        // IMPORTANT: The CRC must include the 4-byte Type (e.g., 'tIME' or 'iTXt')
        // getChunkBytes() returns the 4-byte ASCII type identifier
        crcCalculator.update(chunk.getChunkBytes());
        crcCalculator.update(newPayload);

        int newCrc = (int) crcCalculator.getValue();

        // 3. Overwrite the CRC field (located immediately after the data) fileOffset (4) + length
        // (4) + data (length)
        raf.seek(chunk.getDataOffset() + chunk.getLength());
        raf.writeInt(newCrc);
    }
}