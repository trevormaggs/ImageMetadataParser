package png;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import common.MetadataConstants;

/**
 * Encapsulates the {@code tIME} ancillary chunk, which records the last modification time of the
 * image content (not the creation time).
 * 
 * <p>
 * According to the PNG specification, the time values must represent <b>Universal Coordinated Time
 * (UTC)</b> to ensure consistency across different time zones.
 * </p>
 * 
 * <p>
 * <strong>Chunk Layout (7 bytes):</strong>
 * </p>
 * 
 * <ul>
 * <li>Year: 2 bytes (complete year, e.g., 2026)</li>
 * <li>Month: 1 byte (1-12)</li>
 * <li>Day: 1 byte (1-31)</li>
 * <li>Hour: 1 byte (0-23)</li>
 * <li>Minute: 1 byte (0-59)</li>
 * <li>Second: 1 byte (0-60; 60 allows for leap seconds)</li>
 * </ul>
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
     * Converts the binary fields into a Java {@link Date} object.
     * 
     * <p>
     * Note: PNG {@code tIME} is defined as UTC. This method compensates for Java's 0-based
     * {@link Calendar} months (where January is 0) by subtracting 1 from the raw month value.
     * </p>
     * 
     * @return a {@link Date} representing the image modification time in UTC
     */
    public Date getModificationDate()
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
        StringBuilder sb = new StringBuilder();

        sb.append(super.toString());
        sb.append(String.format(MetadataConstants.FORMATTER, "Modification Date", getModificationDate()));

        return sb.toString();
    }
}