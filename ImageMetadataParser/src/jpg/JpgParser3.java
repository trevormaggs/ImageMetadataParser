package jpg;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import com.adobe.internal.xmp.XMPException;
import common.AbstractImageParser;
import common.DigitalSignature;
import common.ImageRandomAccessReader;
import common.Metadata;
import logger.LogFactory;
import tif.DirectoryIFD;
import tif.TifMetadata;
import tif.TifParser;
import xmp.XmpDirectory;
import xmp.XmpHandler;

/**
 * A parser for JPEG image files that extracts metadata from APP segments.
 * This class handles single-segment EXIF data and implements multi-segment
 * reassembly for ICC profiles (APP2) and Extended XMP data (APP1).
 *
 * @author Trevor Maggs
 * @version 1.6
 * @since 30 September 2025
 */
public class JpgParser3 extends AbstractImageParser
{
    private static final LogFactory LOGGER = LogFactory.getLogger(JpgParser.class);
    private static final int PADDING_LIMIT = 64;
    private static final byte[] EXIF_IDENTIFIER = "Exif\0\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ICC_IDENTIFIER = "ICC_PROFILE\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] XMP_IDENTIFIER = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.UTF_8);

    private TifMetadata metadata;
    private JpgSegmentData segmentData;

    /**
     * Constructs a new JpgParser from a file path string.
     * * @param file the path to the JPEG file
     * 
     * @throws IOException
     *         if the file cannot be read or accessed
     */
    public JpgParser3(String file) throws IOException
    {
        this(Paths.get(file));
    }

    /**
     * Constructs a new JpgParser from a Path object.
     * * @param fpath the Path to the JPEG file
     * 
     * @throws IOException
     *         if the file cannot be read or accessed
     */
    public JpgParser3(Path fpath) throws IOException
    {
        super(fpath);
        LOGGER.info(String.format("Image file [%s] loaded", getImageFile()));
    }

    /**
     * Removes the "Exif\0\0" JPEG signature from a segment payload to reveal
     * the raw TIFF structure.
     * * @param data the raw bytes from the APP1 segment
     * 
     * @return the data stripped of the preamble, or the original data if not found
     */
    public static byte[] stripExifPreamble(byte[] data)
    {
        if (data == null || data.length < EXIF_IDENTIFIER.length)
        {
            return data;
        }

        for (int i = 0; i <= data.length - EXIF_IDENTIFIER.length; i++)
        {
            boolean found = true;

            for (int j = 0; j < EXIF_IDENTIFIER.length; j++)
            {
                if (data[i + j] != EXIF_IDENTIFIER[j])
                {
                    found = false;
                    break;
                }
            }

            if (found)
            {
                return Arrays.copyOfRange(data, i + EXIF_IDENTIFIER.length, data.length);
            }
        }

        return data;
    }

    /**
     * Scans the JPEG file for all supported metadata segments.
     * * @return true if at least one metadata segment was successfully identified
     * 
     * @throws IOException
     *         if an error occurs during file reading
     */
    @Override
    public boolean readMetadata() throws IOException
    {
        try (ImageRandomAccessReader reader = new ImageRandomAccessReader(getImageFile()))
        {
            segmentData = readMetadataSegments(reader);
        }

        return segmentData.hasMetadata();
    }

    /**
     * Reconstructs and returns the parsed metadata. Prioritizes EXIF and
     * incorporates XMP if present.
     * * @return a Metadata object containing IFD and XMP directories
     */
    @Override
    public Metadata<DirectoryIFD> getMetadata()
    {
        if (metadata != null)
        {
            return metadata;
        }

        if (segmentData.getExif().isPresent())
        {
            metadata = TifParser.parseTiffMetadataFromBytes(segmentData.getExif().get());
        }

        else if (segmentData.getXmp().isPresent())
        {
            metadata = new TifMetadata(ByteOrder.BIG_ENDIAN);
        }

        else
        {
            return new TifMetadata();
        }

        if (segmentData.getXmp().isPresent())
        {
            try
            {
                XmpDirectory xmpDir = XmpHandler.addXmpDirectory(segmentData.getXmp().get());
                metadata.addXmpDirectory(xmpDir);
            }

            catch (XMPException exc)
            {
                LOGGER.error("XMP parse failure in [" + getImageFile() + "]", exc);
            }
        }

        return metadata;
    }

    /** @return the JPG digital signature */
    @Override
    public DigitalSignature getImageFormat()
    {
        return DigitalSignature.JPG;
    }

    /**
     * Orchestrates the reading of metadata segments from the stream.
     *
     * @param reader
     *        the active stream reader
     * @return a container for EXIF, XMP, and ICC payloads
     *
     * @throws IOException
     *         if reading from the stream fails
     */
    private JpgSegmentData readMetadataSegments(ImageRandomAccessReader reader) throws IOException
    {
        byte[] exifSegment = null;
        List<byte[]> iccSegments = new ArrayList<>();
        List<byte[]> xmpSegments = new ArrayList<>();

        while (reader.getCurrentPosition() < reader.length())
        {
            JpgSegmentConstants segment = fetchNextSegment(reader);

            if (segment == null || segment == JpgSegmentConstants.END_OF_IMAGE || segment == JpgSegmentConstants.START_OF_STREAM)
            {
                break;
            }

            if (segment.hasLengthField())
            {
                int length = reader.readUnsignedShort() - 2;

                if (length <= 0) continue;

                if (segment == JpgSegmentConstants.APP1_SEGMENT || segment == JpgSegmentConstants.APP2_SEGMENT)
                {
                    byte[] payload = reader.readBytes(length);

                    if (segment == JpgSegmentConstants.APP1_SEGMENT)
                    {
                        if (exifSegment == null)
                        {
                            byte[] stripped = stripExifPreamble(payload);

                            if (stripped.length < payload.length)
                            {
                                exifSegment = stripped;
                                continue;
                            }
                        }

                        if (payload.length >= XMP_IDENTIFIER.length && Arrays.equals(Arrays.copyOfRange(payload, 0, XMP_IDENTIFIER.length), XMP_IDENTIFIER))
                        {
                            xmpSegments.add(Arrays.copyOfRange(payload, XMP_IDENTIFIER.length, payload.length));
                        }
                    }

                    else if (segment == JpgSegmentConstants.APP2_SEGMENT)
                    {
                        if (payload.length >= ICC_IDENTIFIER.length && Arrays.equals(Arrays.copyOfRange(payload, 0, ICC_IDENTIFIER.length), ICC_IDENTIFIER))
                        {
                            iccSegments.add(payload);
                        }
                    }
                }

                else
                {
                    reader.skip(length);
                }
            }
        }

        return new JpgSegmentData(exifSegment, reconstructXmpSegments(xmpSegments), reconstructIccSegments(iccSegments));
    }

    /**
     * Reads the next segment marker. Skips 0xFF padding and filters out RSTn markers from debug
     * logging.
     *
     * @param reader
     *        the stream reader
     * @return the segment constant, or null if EOF or corrupted
     *
     * @throws IOException
     *         if stream access fails
     */
    private JpgSegmentConstants fetchNextSegment(ImageRandomAccessReader reader) throws IOException
    {
        try
        {
            int fillCount = 0;
            while (true)
            {
                int marker = reader.readUnsignedByte();

                if (marker != 0xFF) continue;

                int flag = reader.readUnsignedByte();

                while (flag == 0xFF)
                {
                    fillCount++;

                    if (fillCount > PADDING_LIMIT)
                    {
                        LOGGER.warn("Excessive 0xFF padding; possible corruption");
                        return null;
                    }

                    flag = reader.readUnsignedByte();
                }

                if (!(flag >= JpgSegmentConstants.RST0.getFlag() && flag <= JpgSegmentConstants.RST7.getFlag()) && flag != JpgSegmentConstants.UNKNOWN.getFlag())
                {
                    LOGGER.debug(String.format("Segment flag [0xFF%02X] detected", flag));
                }

                return JpgSegmentConstants.fromBytes(marker, flag);
            }
        }

        catch (EOFException e)
        {
            return null;
        }
    }

    /**
     * Reconstructs ICC profiles from multiple APP2 segments.
     *
     * @param segments
     *        the list of raw segment bytes
     *
     * @return the concatenated profile, or null if reconstruction fails
     */
    private byte[] reconstructIccSegments(List<byte[]> segments)
    {
        if (segments.isEmpty()) return null;

        int headerLen = ICC_IDENTIFIER.length + 2;
        int total = segments.get(0)[ICC_IDENTIFIER.length + 1] & 0xFF;

        if (total != segments.size())
        {
            LOGGER.warn("ICC segment count mismatch. Expected " + total + ", found " + segments.size());
            return null;
        }

        segments.sort(new Comparator<byte[]>()
        {
            @Override
            public int compare(byte[] s1, byte[] s2)
            {
                return Integer.compare(s1[ICC_IDENTIFIER.length] & 0xFF, s2[ICC_IDENTIFIER.length] & 0xFF);
            }
        });

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
        {
            for (byte[] seg : segments)
            {
                baos.write(Arrays.copyOfRange(seg, headerLen, seg.length));
            }

            return baos.toByteArray();
        }

        catch (IOException e)
        {
            return null;
        }
    }

    /**
     * Concatenates multiple XMP APP1 segments into a single block.
     *
     * @param segments
     *        the list of XMP fragments
     * @return the reassembled byte array, or null if empty
     */
    private byte[] reconstructXmpSegments(List<byte[]> segments)
    {
        if (segments.isEmpty()) return null;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
        {
            for (byte[] seg : segments)
            {
                baos.write(seg);
            }

            return baos.toByteArray();
        }

        catch (IOException e)
        {
            return null;
        }
    }

    /** Data carrier for extracted raw byte segments. */
    private static class JpgSegmentData
    {
        private final byte[] exif, xmp, icc;

        JpgSegmentData(byte[] e, byte[] x, byte[] i)
        {
            exif = e;
            xmp = x;
            icc = i;
        }

        Optional<byte[]> getExif()
        {
            return Optional.ofNullable(exif);
        }

        Optional<byte[]> getXmp()
        {
            return Optional.ofNullable(xmp);
        }

        Optional<byte[]> getIcc()
        {
            return Optional.ofNullable(icc);
        }

        boolean hasMetadata()
        {
            return exif != null || xmp != null || icc != null;
        }
    }
}