package png;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import common.MetadataConstants;

/**
 * Encapsulates the {@code tIME} ancillary chunk, which records the last
 * modification time of the image content.
 * *
 * <p>
 * According to <b>ISO/IEC 15948:2004</b>, the tIME chunk contains 7 bytes
 * representing the UTC time. This class validates that each field (Year, Month,
 * Day, Hour, Minute, Second) falls within the legal ranges specified by the
 * PNG standard.
 * </p>
 */
public class PngChunkTIME2 extends PngChunk
{
    // Field range constants according to PNG spec
    private static final int MIN_MONTH = 1;
    private static final int MAX_MONTH = 12;
    private static final int MIN_DAY = 1;
    private static final int MAX_DAY = 31;
    private static final int MIN_HOUR = 0;
    private static final int MAX_HOUR = 23;
    private static final int MIN_MINUTE = 0;
    private static final int MAX_MINUTE = 59;
    private static final int MIN_SECOND = 0;
    private static final int MAX_SECOND = 60;

    private final int year;
    private final int month;
    private final int day;
    private final int hour;
    private final int minute;
    private final int second;

    /**
     * Constructs a tIME chunk and validates the 7-byte payload.
     * * @param length the data length (must be 7)
     * 
     * @param typeBytes
     *        raw chunk type
     * @param crc32
     *        chunk CRC
     * @param data
     *        the 7-byte time payload
     * @param offset
     *        physical file offset
     * @throws IllegalArgumentException
     *         if the data length is not 7 or if
     *         any time field is outside the valid PNG specification range
     */
    public PngChunkTIME2(long length, byte[] typeBytes, int crc32, byte[] data, long offset)
    {
        super(length, typeBytes, crc32, data, offset);

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

        validateFields();
    }

    private void validateFields()
    {
        if (month < MIN_MONTH || month > MAX_MONTH) throw new IllegalArgumentException("Invalid month: " + month);
        if (day < MIN_DAY || day > MAX_DAY) throw new IllegalArgumentException("Invalid day: " + day);
        if (hour < MIN_HOUR || hour > MAX_HOUR) throw new IllegalArgumentException("Invalid hour: " + hour);
        if (minute < MIN_MINUTE || minute > MAX_MINUTE) throw new IllegalArgumentException("Invalid minute: " + minute);
        if (second < MIN_SECOND || second > MAX_SECOND) throw new IllegalArgumentException("Invalid second: " + second);
    }

    /**
     * Converts the binary fields into a Java {@link Date} object.
     * 
     * <p>
     * Note: PNG tIME is always UTC. Java {@link Calendar} months are 0-based (January is 0), so
     * this method automatically offsets the PNG month (1-12).
     * </p>
     * 
     * @return a Date object representing the modification time in UTC
     */
    public Date getModificationDate()
    {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

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