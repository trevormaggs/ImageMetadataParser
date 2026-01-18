package common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;

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
     * Sets the last modified time, last accessed time, and creation time of an image file.
     *
     * @param fpath
     *        the file path to modify
     * @param fileTime
     *        the file time to set
     *
     * @throws IOException
     *         if an error occurs while setting the file times
     */
    public static void changeFileTimeProperties(Path fpath, FileTime fileTime) throws IOException
    {
        BasicFileAttributeView attr = Files.getFileAttributeView(fpath, BasicFileAttributeView.class);

        attr.setTimes(fileTime, fileTime, fileTime);
    }

    /**
     * Generates a line of padded characters to n of times.
     *
     * @param ch
     *        string to be padded
     * @param n
     *        number of times to pad in integer form
     *
     * @return formatted string
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
     * Verifies that the requested range exists within the byte array.
     * 
     * @param data
     *        The byte array being accessed
     * @param offset
     *        rhe starting position
     * @param length
     *        rhe number of bytes to be read/written
     * @return true if the range is safe
     */
    public static boolean isSafeRange(byte[] data, int offset, int length)
    {
        return offset >= 0 && length >= 0 && (offset + length) <= data.length;
    }

    /**
     * Validates a box's boundaries before processing.
     */
    public static void validateBoxBounds(byte[] data, int offset, String expectedType)
    {
        if (!isSafeRange(data, offset, 8))
        {
            throw new IllegalStateException("Unexpected end of file while reading box header at " + offset);
        }

        // Optional: Verify the FourCC type matches what we expect
        String actualType = new String(data, offset + 4, 4);

        if (!actualType.equals(expectedType))
        {
            throw new IllegalArgumentException("Box mismatch! Expected " + expectedType + " but found " + actualType);
        }
    }

    /**
     * Scans the specified byte payload to identify the starting index of the TIFF Magic Bytes (Byte
     * Order Marks).
     * *
     * <p>
     * This method identifies both Little Endian ('II') or Big Endian ('MM') headers, validating
     * against the TIFF version markers (0x2A for Standard TIFF or 0x2B for BigTIFF). This is
     * essential for skipping container-specific preambles such as the JPEG 'Exif\0\0' signature or
     * HEIF-specific metadata offsets.
     * </p>
     * 
     * @param payload
     *        the raw byte array to be scanned
     * @return the zero-based index of the first byte of the TIFF header, or -1 if no valid TIFF
     *         signature is detected
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
}