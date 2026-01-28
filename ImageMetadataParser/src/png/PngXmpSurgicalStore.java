package png;

import java.nio.charset.StandardCharsets;

/**
 * Utility for performing in-place string replacements for XMP data.
 */
public class PngXmpSurgicalStore
{
    /**
     * Finds the XMP date tag and overwrites it within the byte array.
     * Ensure the newDate string matches the length of the existing slot.
     *
     * @param payload
     *        The raw iTXt chunk payload
     * @param newDate
     *        The formatted date string (e.g., "2026-01-28T22:16:15Z")
     * @return The modified payload
     */
    public byte[] patchXmpDate(byte[] payload, String newDate)
    {
        String xmlContent = new String(payload, StandardCharsets.UTF_8);

        // Typical XMP date tags
        String[] tags = {"<xmp:ModifyDate>", "<xmp:CreateDate>", "<xmp:MetadataDate>"};

        for (String tag : tags)
        {
            int startTagIndex = xmlContent.indexOf(tag);

            if (startTagIndex != -1)
            {
                int startPos = startTagIndex + tag.length();
                int endPos = xmlContent.indexOf("</", startPos);

                if (endPos != -1)
                {
                    int slotLength = endPos - startPos;
                    String formattedDate = alignDateToSlot(newDate, slotLength);

                    // Convert formatted date back to bytes and overwrite the payload
                    byte[] dateBytes = formattedDate.getBytes(StandardCharsets.UTF_8);
                    System.arraycopy(dateBytes, 0, payload, startPos, Math.min(dateBytes.length, slotLength));
                }
            }
        }

        return payload;
    }

    /**
     * Adjusts the date string to fit the exact byte length of the existing XML slot
     * to prevent chunk length changes.
     */
    private String alignDateToSlot(String date, int length)
    {
        if (date.length() > length)
        {
            return date.substring(0, length);
        }
        while (date.length() < length)
        {
            date += " "; // Padding with spaces is valid inside XML values
        }

        return date;
    }
}