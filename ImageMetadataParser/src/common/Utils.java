package common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Provides general utility methods for file manipulation, metadata extraction, and string
 * formatting.
 *
 * <p>
 * This class includes specialised logic for XMP serialisation, TIFF header detection, and
 * forensic-safe string alignment.
 * </p>
 */
public final class Utils
{
    /**
     * Prevents direct instantiation.
     *
     * @throws UnsupportedOperationException
     *         to indicate that direct instantiation is not supported
     */
    private Utils()
    {
        throw new UnsupportedOperationException("Instantiation not allowed");
    }

    /**
     * Returns the extension of the image file name, excluding the dot.
     *
     * <p>
     * If the file name does not contain an extension, an empty string is returned.
     * </p>
     *
     * @param fpath
     *        the file path
     *
     * @return the file extension, for example: {@code "jpg"} or {@code "png"} etc, or an empty
     *         string if none
     */
    public static String getFileExtension(Path fpath)
    {
        String fileName = fpath.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');

        return (lastDot == -1) ? "" : fileName.substring(lastDot + 1).toLowerCase();
    }

    /**
     * Updates the creation, last access, and last modified time-stamps of the file.
     *
     * @param fpath
     *        the path to the file
     * @param fileTime
     *        the new time-stamp to apply to all three attributes
     *
     * @throws IOException
     *         if the filesystem attributes cannot be modified
     */
    public static void updateFileTimeStamps(Path fpath, FileTime fileTime) throws IOException
    {
        BasicFileAttributeView attr = Files.getFileAttributeView(fpath, BasicFileAttributeView.class);
        attr.setTimes(fileTime, fileTime, fileTime);
    }

    /**
     * Generates a string consisting of the specified sequence repeated n times.
     *
     * @param ch
     *        the string to be repeated
     * @param n
     *        the number of times to repeat the string
     * @return the resulting formatted string, or an empty string if n <= 0
     */
    public static String repeatPrint(String ch, int n)
    {
        if (n <= 0)
        {
            return "";
        }

        StringBuilder sb = new StringBuilder(ch.length() * n);

        for (int i = 0; i < n; i++)
        {
            sb.append(ch);
        }

        return sb.toString();
    }

    /**
     * Validates a box's boundaries before processing.
     */
    public static void validateBoxBounds(byte[] data, int offset, String expectedType)
    {
        if (!(offset >= 0 && (offset + 8) <= data.length))
        {
            throw new IllegalStateException("Unexpected end of file while reading box header at [" + offset + "]");
        }

        // Verify the FourCC type matches what we expect
        String actualType = new String(data, offset + 4, 4);

        if (!actualType.equals(expectedType))
        {
            throw new IllegalArgumentException("Box mismatch! Expected [" + expectedType + "], but found [" + actualType + "]");
        }
    }

    /**
     * Scans a byte payload to identify the starting index of the TIFF Magic Bytes (Byte Order
     * Marks). This is essential for skipping container-specific preambles such as the JPEG
     * 'Exif\0\0' signature.
     *
     * @param payload
     *        the raw byte array to be scanned
     * @return the zero-based index of the TIFF header, or -1 if no signature is detected
     */
    public static int calculateShiftTiffHeader(byte[] payload)
    {
        if (payload != null && payload.length >= 4)
        {
            /*
             * Per ISO/IEC 23008-12, Exif items may have a preamble or offset bytes.
             * Scan for the II (0x4949) or MM (0x4D4D) magic bytes.
             */
            for (int i = 0; i <= payload.length - 4; i++)
            {
                // Little Endian (II)
                if (payload[i] == 0x49 && payload[i + 1] == 0x49)
                {
                    if (payload[i + 2] == 0x2A || payload[i + 2] == 0x2B)
                    {
                        return i;
                    }
                }

                // Big Endian (MM)
                if (payload[i] == 0x4D && payload[i + 1] == 0x4D)
                {
                    if (payload[i + 3] == 0x2A || payload[i + 3] == 0x2B)
                    {
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    /**
     * Locates the value within an XMP tag, handling both attribute-style and element-style
     * serialisation.
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
     * @param content
     *        the XML string to scan
     * @param tagIdx
     *        the starting index of the tag name within the content
     * @return an array of two integers: {@code [startIndex, length]} or {@code null} if the span is
     *         invalid
     */
    public static int[] findValueSpan(String content, int tagIdx)
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

    /**
     * Generates a byte array containing the best-fitting ISO date string to fit a fixed-width XMP
     * slot.
     *
     * <p>
     * Falls back from full ISO (with offset) to short ISO (no offset) to date-only. If the date is
     * shorter than the slot, it is padded with ASCII spaces (0x20) to maintain binary alignment
     * within the file.
     * </p>
     *
     * @param zdt
     *        the timestamp to format
     * @param slotWidth
     *        the exact physical width of the target binary slot in bytes
     * @return a byte array of length defined by {@code slotWidth}, or {@code null} if cannot be
     *         computed
     */
    public static byte[] alignXmpValueSlot(ZonedDateTime zdt, int slotWidth)
    {
        String longISO = zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")); // 2026-01-28T18:30:00+11:00
        String shortISO = zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")); // 2026-01-28T18:30:00
        String dateOnly = shortISO.split("T")[0]; // 2026-01-28
        String bestfitISO;

        // Since ISO dates are ASCII, length() == byte count in UTF-8.
        if (longISO.length() <= slotWidth)
        {
            bestfitISO = longISO;
        }

        else if (shortISO.length() <= slotWidth)
        {
            bestfitISO = shortISO;
        }

        else if (dateOnly.length() <= slotWidth)
        {
            /*
             * Date Only - 2026-01-28
             * Threshold: ISO 8601 strings (YYYY-MM-DD)
             * require at least 10 characters.
             */
            bestfitISO = dateOnly;
        }

        else
        {
            return null;
        }

        byte[] slotBytes = new byte[slotWidth];
        byte[] dateBytes = bestfitISO.getBytes(StandardCharsets.UTF_8);

        Arrays.fill(slotBytes, (byte) 0x20);
        System.arraycopy(dateBytes, 0, slotBytes, 0, dateBytes.length);

        return slotBytes;
    }

    public static String printFastDumpXML(Path imagePath, byte[] xmpBytes) throws IOException
    {
        // 1. Safe conversion
        String xml = new String(xmpBytes, StandardCharsets.UTF_8);

        // 2. Clean up newlines between tags so we can iterate line-by-line
        String cleanXml = xml.replaceAll(">\\s*<", ">\n<");
        String[] lines = cleanXml.split("\n");
        StringBuilder sb = new StringBuilder();
        int indent = 0;

        for (String line : lines)
        {
            String trimmed = line.trim();

            if (trimmed.isEmpty())
            {
                continue;
            }

            // Decrease indent for closing tags </...
            if (trimmed.startsWith("</"))
            {
                indent--;
            }

            // Append indentation (Java 8 way)
            for (int i = 0; i < indent; i++)
            {
                sb.append("    ");
            }

            sb.append(trimmed).append("\n");

            // Increase indent for opening tags
            // Rule: Starts with <, isn't a closer </, isn't a processing instruction <?
            // and doesn't end with a self-closer />
            if (trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.startsWith("<?") && !trimmed.endsWith("/>"))
            {
                // Only indent if the line doesn't already contain the closing tag
                // (e.g., <tag>value</tag> stays on one line)
                if (!trimmed.contains("</"))
                {
                    indent++;
                }
            }
        }

        // 3. Save to .xml file
        String fileName = imagePath.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        String xmlName = (lastDot > 0 ? fileName.substring(0, lastDot) : fileName) + ".xml";

        Files.write(imagePath.resolveSibling(xmlName), sb.toString().getBytes(StandardCharsets.UTF_8));

        return sb.toString();
    }

    /**
     * A lightweight, regex-based formatter for XMP debugging. It bypasses DOM overhead to provide
     * an immediate visual structure of the XMP packet.
     *
     * <p>
     * Note: This method is designed for human inspection and should not be used for production XML
     * modification.
     * </p>
     *
     * @param imagePath
     *        the path of the source image
     * @param xmpBytes
     *        the raw XMP bytes extracted from the JPEG
     * @return the formatted XML string
     *
     * @throws IOException
     *         if an I/O error occurs during the file write
     */
    public static String printFastDumpXML2(Path imagePath, byte[] xmpBytes) throws IOException
    {
        String xmlName = imagePath.getFileName().toString().replaceAll("^(.*)\\.[^.]+$", "$1.xml");
        Path outputPath = imagePath.resolveSibling(xmlName);

        String xml = new String(xmpBytes, StandardCharsets.UTF_8);

        // 1. Force newlines between tags
        xml = xml.replaceAll(">\\s*<", ">\n<");

        // 2. Indent attributes to make them readable
        xml = xml.replaceAll("\\s+([a-zA-Z0-9]+:[a-zA-Z0-9]+=\")", "\n    $1");

        // 3. Handle element values (content between tags)
        xml = xml.replaceAll("(?<=>)([^<\\s][^<]*)(?=<)", "\n        $1\n");

        // 4. Clean up self-closing tags and block endings
        xml = xml.replaceAll("\"\\s*/>", "\"\n/>").replaceAll("\">", "\"\n>");

        String finalXml = xml.trim();

        Files.write(outputPath, finalXml.getBytes(StandardCharsets.UTF_8));

        return finalXml;
    }
}