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
}