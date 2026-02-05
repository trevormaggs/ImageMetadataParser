package common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

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
    private static final DateTimeFormatter XMP_LONG = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter XMP_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

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
     * Consolidates XMP value discovery into a single atomic operation by identifying value
     * boundaries for both XML elements and attributes.
     * *
     * <p>
     * If a quote (") appears before a bracket (>), the value is treated as an attribute. Otherwise,
     * it is treated as an element value (stopping at the start of a closing tag).
     * </p>
     *
     * @param content
     *        the raw XML/XMP string
     * @param tagIdx
     *        The starting index of the property name
     * @return an int array where [0] is the logical start index and [1] is the byte width, or
     *         {@code null} if valid boundaries cannot be resolved
     */
    private static int[] findValueSpanOld(String content, int tagIdx)
    {
        int bracket = content.indexOf(">", tagIdx);
        int quote = content.indexOf("\"", tagIdx);
        boolean isAttr = (quote != -1 && (bracket == -1 || quote < bracket));

        int start = isAttr ? quote + 1 : bracket + 1;
        int end = content.indexOf(isAttr ? "\"" : "<", start);

        return (start > 0 && end > start) ? new int[]{start, end - start} : null;
    }

    /**
     * Formats the date to fit the existing XMP slot width, falling back to shorter ISO variations
     * or space-padding to prevent binary shifting.
     */
    public static String alignXmpValueSlot(ZonedDateTime zdt, int slotWidth)
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

        return null;
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
    public static String printFastDumpXML(Path imagePath, byte[] xmpBytes) throws IOException
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