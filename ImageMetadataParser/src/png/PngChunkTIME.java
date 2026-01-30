package png;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import common.MetadataConstants;

/**
 * Encapsulates the tIME ancillary chunk, which records the last modification time of the image
 * content.
 */
public class PngChunkTIME extends PngChunk
{
    private final int year;
    private final int month;
    private final int day;
    private final int hour;
    private final int minute;
    private final int second;

    public PngChunkTIME(long length, byte[] typeBytes, int crc32, byte[] data, long offset)
    {
        super(length, typeBytes, crc32, data, offset);

        // The tIME chunk must be exactly 7 bytes
        if (data.length != 7)
        {
            throw new IllegalArgumentException("Invalid tIME chunk length. Expected 7, found " + data.length);
        }

        this.year = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        this.month = data[2] & 0xFF;
        this.day = data[3] & 0xFF;
        this.hour = data[4] & 0xFF;
        this.minute = data[5] & 0xFF;
        this.second = data[6] & 0xFF;
    }

    /**
     * Converts the binary fields into a Java Date object. Note: PNG tIME is always UTC.
     */
    public Date getJavaDate()
    {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        
        // Calendar months are 0-based in Java (Jan = 0)
        cal.set(year, month - 1, day, hour, minute, second);
        cal.set(Calendar.MILLISECOND, 0);
        
        return cal.getTime();
    }

    @Override
    public String toString()
    {
        return super.toString() + String.format(MetadataConstants.FORMATTER, "Modification Date (tIME)", String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second));
    }
}