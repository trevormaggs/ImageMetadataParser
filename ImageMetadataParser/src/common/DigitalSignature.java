package common;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

/**
 * Enumerates known image formats by identifying their distinct magic numbers in the image file
 * header. These magic numbers reside in the first few bytes of the file.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public enum DigitalSignature
{
    JPG("jpg", new int[][]{{0xFF, 0xD8}}),
    TIF("tif", new int[][]{{0x4D, 0x4D}, {0x49, 0x49}}),
    PNG("png", new int[][]{{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}}),
    HEIF("heic", new int[][]{{0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63}}),
    WEBP("webp", new int[][]{{0x57, 0x45, 0x42, 0x50}}),
    MOV("mov", new int[][]{{0x66, 0x74, 0x79, 0x70, 0x71, 0x74}, {0x6D, 0x6F, 0x6F, 0x76}}),
    AVI("avi", new int[][]{{0x52, 0x49, 0x46, 0x46}}),
    MP4("mp4", new int[][]{{0x66, 0x74, 0x79, 0x70, 0x6D, 0x70, 0x34, 0x32}}),
    UNKNOWN("", new int[][]{{0x00, 0x00}});

    private final String extension;
    private final int[][] magicNumbers;

    DigitalSignature(String extension, int[][] magicNumbers)
    {
        this.extension = extension;
        this.magicNumbers = magicNumbers;
    }

    /**
     * Returns the extension of the image file name without the dot.
     *
     * <p>
     * If the file name is unknown, an empty string is returned.
     * </p>
     *
     * @return the file extension, for example: {@code "jpg"} or {@code "png"} etc, or an empty
     *         string if none
     */
    public String getFileExtensionName()
    {
        return extension;
    }

    /**
     * Returns a byte array of magic numbers based on the specified array index.
     *
     * @param index
     *        the index of the magic number array to retrieve
     * 
     * @return an array of bytes containing the magic numbers
     * 
     * @throws IllegalArgumentException
     *         if the index is out of bounds
     */
    public byte[] getMagicNumbers(int index)
    {
        if (index < 0 || index >= magicNumbers.length)
        {
            throw new IllegalArgumentException("Index [" + index + "] is out of bounds for magicNumbers array");
        }

        int[] intArray = magicNumbers[index];
        byte[] byteArray = new byte[intArray.length];

        for (int i = 0; i < intArray.length; i++)
        {
            byteArray[i] = (byte) intArray[i];
        }

        return byteArray;
    }

    /**
     * Returns whether this media file is a known video format, for example: MP4, MOV, AVI, etc.
     *
     * @return true if the media is a video, otherwise false
     */
    public boolean isVideo()
    {
        switch (this)
        {
            case AVI:
            case MOV:
            case MP4:
                return true;

            default:
                return false;
        }
    }

    /**
     * Detects the file signature based on magic numbers.
     *
     * @param file
     *        the file path as a String
     * @return a matching DigitalSignature enum, or UNKNOWN if none matched
     * @throws IOException
     *         if the file is unreadable or missing
     */
    public static DigitalSignature detectFormat(String file) throws IOException
    {
        return detectFormat(Paths.get(file));
    }

    /**
     * Detects the file signature based on magic numbers.
     *
     * @param path
     *        the file path
     * @return a matching DigitalSignature enum, or UNKNOWN if none matched
     * @throws IOException
     *         if the file is unreadable or missing
     */
    public static DigitalSignature detectFormat(Path path) throws IOException
    {
        int maxLength = 0;

        /* Determine the longest magic number sequence (for buffer size) */
        for (DigitalSignature sig : EnumSet.complementOf(EnumSet.of(UNKNOWN)))
        {
            for (int[] magic : sig.magicNumbers)
            {
                if (magic.length > maxLength)
                {
                    maxLength = magic.length;
                }
            }
        }

        /*
         * This makes sure the source byte array is long
         * enough to facilitate the sub-array search
         */
        byte[] buffer = new byte[maxLength * 2];

        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(path)))
        {
            int totalRead = 0;

            /* Force robust completeness of buffer fill */
            while (totalRead < buffer.length)
            {
                int bytesRead = input.read(buffer, totalRead, buffer.length - totalRead);

                if (bytesRead == -1)
                {
                    break;
                }

                totalRead += bytesRead;
            }

            for (DigitalSignature sig : EnumSet.complementOf(EnumSet.of(UNKNOWN)))
            {
                for (int[] magic : sig.magicNumbers)
                {
                    if (containsMagicNumbers(buffer, magic))
                    {
                        return sig;
                    }
                }
            }
        }

        catch (NoSuchFileException e)
        {
            throw new IOException("File [" + path + "] does not exist", e);
        }

        return UNKNOWN;
    }

    /**
     * Checks whether the given byte array contains the magic number sequence.
     *
     * @param fileHeader
     *        the initial bytes of the file
     * @param magic
     *        the magic number sequence to search for
     * 
     * @return true if the magic number exists anywhere in the header
     */
    private static boolean containsMagicNumbers(byte[] fileHeader, int[] magic)
    {
        OUTER:
        for (int i = 0; i <= fileHeader.length - magic.length; i++)
        {
            for (int j = 0; j < magic.length; j++)
            {
                if ((fileHeader[i + j] & 0xFF) != magic[j])
                {
                    continue OUTER;
                }
            }

            // Sub-array found
            return true;
        }

        return false;
    }
}