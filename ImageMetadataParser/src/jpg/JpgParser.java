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
import batch.BatchMetadataUtils;
import common.AbstractImageParser;
import common.DigitalSignature;
import common.ImageRandomAccessReader;
import common.MetadataConstants;
import common.MetadataStrategy;
import logger.LogFactory;
import tif.DirectoryIFD;
import tif.TifMetadata;
import tif.TifParser;
import xmp.XmpDirectory;
import xmp.XmpHandler;

/**
 * A parser for JPG image files that extracts metadata from the APP segments, handling multi-segment
 * metadata, specifically for ICC and XMP data, in addition to the single-segment EXIF data.
 *
 * <p>
 * This parser adheres to the EXIF specification (version 2.32, CIPA DC-008-2019), which mandates
 * that all EXIF metadata must be contained within a single APP1 segment. The parser will search for
 * and process the first APP1 segment it encounters that contains the {@code Exif} identifier.
 * </p>
 *
 * <p>
 * For ICC profiles, the parser now collects and concatenates all APP2 segments that contain the
 * {@code ICC_PROFILE} identifier, following the concatenation rules defined in the ICC
 * specification. Similarly, for {@code XMP} data, it concatenates all APP1 segments with the
 * {@code http://ns.adobe.com/xap/1.0/} identifier to form a single XMP data block through segment
 * concatenation, supporting the Extended XMP (multi-segment) specification.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.5
 * @since 30 September 2025
 */
public class JpgParser extends AbstractImageParser
{
    private static final LogFactory LOGGER = LogFactory.getLogger(JpgParser.class);
    private static final int PADDING_LIMIT = 64;
    private static final byte[] EXIF_IDENTIFIER = "Exif\0\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ICC_IDENTIFIER = "ICC_PROFILE\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] XMP_IDENTIFIER = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.UTF_8);
    private TifMetadata metadata;
    private JpgSegmentData segmentData;

    /**
     * A simple immutable data carrier for the raw byte arrays of the different metadata segments
     * found in a JPEG file. This class encapsulates the raw EXIF, ICC, and XMP data payloads.
     */
    private static class JpgSegmentData
    {
        private final byte[] exif;
        private final byte[] xmp;
        private final byte[] icc;

        public JpgSegmentData(byte[] exif, byte[] xmp, byte[] icc)
        {
            this.exif = exif;
            this.xmp = xmp;
            this.icc = icc;
        }

        public Optional<byte[]> getExif()
        {
            return Optional.ofNullable(exif);
        }

        public Optional<byte[]> getXmp()
        {
            return Optional.ofNullable(xmp);
        }

        public Optional<byte[]> getIcc()
        {
            return Optional.ofNullable(icc);
        }

        public boolean hasMetadata()
        {
            return ((exif != null && exif.length > 0) ||
                    (xmp != null && xmp.length > 0) ||
                    (icc != null && icc.length > 0));
        }
    }

    /**
     * Constructs a new instance from a file path string.
     *
     * @param file
     *        the path to the JPG file as a string
     *
     * @throws IOException
     *         if the file cannot be opened or read
     */
    public JpgParser(String file) throws IOException
    {
        this(Paths.get(file));
    }

    /**
     * Constructs a new instance with the specified file path.
     *
     * @param fpath
     *        the path to the JPG file as an encapsulated object
     *
     * @throws IOException
     *         if the file cannot be opened or read
     */
    public JpgParser(Path fpath) throws IOException
    {
        super(fpath);

        LOGGER.info(String.format("Image file [%s] loaded", getImageFile()));

        String ext = BatchMetadataUtils.getFileExtension(getImageFile());

        if (!ext.equalsIgnoreCase("jpg") && !ext.equalsIgnoreCase("jpeg"))
        {
            LOGGER.warn(String.format("Incorrect extension name detected in file [%s]. Should be [jpg], but found [%s]", getImageFile().getFileName(), ext));
        }
    }

    /**
     * Removes the 6-byte {@code Exif\0\0} signature/header required by the JPEG standard before
     * passing the payload to a TIFF-based parser.
     *
     * @param data
     *        the raw EXIF chunk payload
     * @return the corrected EXIF data byte array
     */
    public static byte[] stripExifPreamble(byte[] data)
    {
        if (data == null || data.length < JpgParser.EXIF_IDENTIFIER.length)
        {
            return data;
        }

        for (int i = 0; i <= data.length - JpgParser.EXIF_IDENTIFIER.length; i++)
        {
            boolean found = true;

            for (int j = 0; j < JpgParser.EXIF_IDENTIFIER.length; j++)
            {
                if (data[i + j] != JpgParser.EXIF_IDENTIFIER[j])
                {
                    found = false;
                    break;
                }
            }

            if (found)
            {
                return Arrays.copyOfRange(data, i + JpgParser.EXIF_IDENTIFIER.length, data.length);
            }
        }

        // If preamble isn't found, it might already be raw TIFF data
        return data;
    }

    @Deprecated
    public static byte[] stripExifPreamble2(byte[] data)
    {
        if (data.length >= JpgParser.EXIF_IDENTIFIER.length && Arrays.equals(Arrays.copyOf(data, JpgParser.EXIF_IDENTIFIER.length), JpgParser.EXIF_IDENTIFIER))
        {
            return Arrays.copyOfRange(data, JpgParser.EXIF_IDENTIFIER.length, data.length);
        }

        return data;
    }

    /**
     * Reads the JPG image file to extract all supported raw metadata segments, specifically for
     * multi-segment ICC profiles (concatenated via sequence markers) and XMP data blocks, if
     * present), and uses the extracted data to initialise the necessary metadata object for later
     * data retrieval.
     *
     * @return true if at least one supported metadata segment (EXIF, XMP, or ICC) was found and
     *         extracted
     *
     * @throws IOException
     *         if a file reading error occurs during the parsing
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
     * Retrieves the extracted Exif metadata from the JPG image file, or a fallback if unavailable.
     * If XMP data is present, it will also be extracted to initialise the necessary metadata
     * object for later data retrieval.
     *
     * <p>
     * If the metadata has not yet been parsed and raw EXIF segment data is present, this method
     * triggers the parsing of all extracted metadata blocks. If parsing fails or no EXIF or XMP
     * segment is present, an empty {@link TifMetadata} object is returned as a fallback.
     * </p>
     *
     * @return a MetadataStrategy object, populated with EXIF data or empty
     */
    @Override
    public MetadataStrategy<DirectoryIFD> getMetadata()
    {
        if (metadata != null)
        {
            return metadata;
        }

        else if (segmentData.getExif().isPresent())
        {
            metadata = TifParser.parseTiffMetadataFromBytes(segmentData.getExif().get());
        }

        else if (segmentData.getXmp().isPresent())
        {
            /*
             * Default to Big-Endian, which is the JPEG standard,
             * if EXIF is absent.
             */
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
                LOGGER.error("Unable to parse XMP payload in file [" + getImageFile() + "] due to an error", exc);
            }
        }

        return metadata;
    }
    /**
     * Returns the detected {@code JPG} format.
     *
     * @return a {@link DigitalSignature} enum constant representing this image format
     */
    @Override
    public DigitalSignature getImageFormat()
    {
        return DigitalSignature.JPG;
    }

    /**
     * Generates a human-readable diagnostic string containing metadata details.
     *
     * @return a formatted string suitable for diagnostics, logging, or inspection
     */
    @Override
    public String formatDiagnosticString()
    {
        MetadataStrategy<DirectoryIFD> meta = getMetadata();
        StringBuilder sb = new StringBuilder();

        try
        {
            sb.append("\t\t\tJPG Metadata Summary").append(System.lineSeparator()).append(System.lineSeparator());
            sb.append(super.formatDiagnosticString());

            if (meta instanceof TifMetadata)
            {
                TifMetadata tif = (TifMetadata) meta;

                if (tif.hasMetadata())
                {
                    for (DirectoryIFD ifd : tif)
                    {
                        sb.append(ifd);
                    }
                }

                else
                {
                    sb.append("No EXIF metadata found.").append(System.lineSeparator());
                }

                sb.append(System.lineSeparator()).append(MetadataConstants.DIVIDER).append(System.lineSeparator());

                if (tif.hasXmpData())
                {
                    sb.append(tif.getXmpDirectory());
                }

                else
                {
                    sb.append("No XMP metadata found").append(System.lineSeparator());
                }

                sb.append(MetadataConstants.DIVIDER).append(System.lineSeparator());

                if (segmentData.getIcc().isPresent())
                {
                    sb.append("Parser has concatenated all ICC segments, ");
                    sb.append(String.format("totalling [%d] bytes of ICC Data.", segmentData.getIcc().get().length)).append(System.lineSeparator());
                }

                else
                {
                    sb.append("No ICC Profile found.").append(System.lineSeparator());
                }

                sb.append(MetadataConstants.DIVIDER).append(System.lineSeparator());
            }
        }

        catch (Exception exc)
        {
            LOGGER.error("Diagnostics failed for file [" + getImageFile() + "]", exc);

            sb.append("Error generating diagnostics [")
                    .append(exc.getClass().getSimpleName())
                    .append("]: ")
                    .append(exc.getMessage())
                    .append(System.lineSeparator());
        }

        return sb.toString();
    }

    /**
     * Reads all supported metadata segments, including EXIF, ICC and XMP, if present, from the JPEG
     * file stream.
     *
     * @param reader
     *        the input JPEG stream
     * @return a JpgSegmentData record containing the byte arrays for any found segments
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    private JpgSegmentData readMetadataSegments(ImageRandomAccessReader reader) throws IOException
    {
        byte[] exifSegment = null;
        List<byte[]> iccSegments = new ArrayList<>();
        List<byte[]> xmpSegments = new ArrayList<>();

        while (reader.getCurrentPosition() < reader.length())
        {
            JpgSegmentConstants segment = fetchNextSegment(reader);

            // SOS (Start of Scan) marks the beginning of the compressed image data.
            // Usually, no metadata exists after this point except the EOI marker.
            if (segment == null || segment == JpgSegmentConstants.END_OF_IMAGE || (segment == JpgSegmentConstants.START_OF_STREAM))
            {
                break;
            }

            if (segment.hasLengthField())
            {
                int length = reader.readUnsignedShort() - 2;

                // Length must be between 2 and 65535 bytes (unsigned short)
                if (length <= 0)
                {
                    continue;
                }

                // Decision point: Read or Skip?
                if (segment == JpgSegmentConstants.APP1_SEGMENT || segment == JpgSegmentConstants.APP2_SEGMENT)
                {
                    byte[] payload = reader.readBytes(length);

                    if (segment == JpgSegmentConstants.APP1_SEGMENT)
                    {
                        // Only one EXIF segment is allowed
                        if (exifSegment == null)
                        {
                            byte[] strippedPayload = JpgParser.stripExifPreamble(payload);

                            if (strippedPayload.length < payload.length)
                            {
                                exifSegment = strippedPayload;
                                LOGGER.debug(String.format("Valid EXIF APP1 segment found. Length [%d]", exifSegment.length));
                                continue;
                            }
                        }

                        // Check for XMP metadata (APP1 segments that are not EXIF might be XMP)
                        if (payload.length >= XMP_IDENTIFIER.length && Arrays.equals(Arrays.copyOfRange(payload, 0, XMP_IDENTIFIER.length), XMP_IDENTIFIER))
                        {
                            xmpSegments.add(Arrays.copyOfRange(payload, XMP_IDENTIFIER.length, payload.length));
                            LOGGER.debug(String.format("Valid XMP APP1 segment found. Length [%d]", payload.length));
                            continue;
                        }

                        LOGGER.debug(String.format("Non-EXIF/XMP APP1 segment skipped. Length [%d]", payload.length));
                    }

                    else if (segment == JpgSegmentConstants.APP2_SEGMENT)
                    {
                        if (payload.length >= ICC_IDENTIFIER.length && Arrays.equals(Arrays.copyOfRange(payload, 0, ICC_IDENTIFIER.length), ICC_IDENTIFIER))
                        {
                            iccSegments.add(payload);
                            LOGGER.debug(String.format("Valid ICC APP2 segment found. Length [%d]", payload.length));
                            continue;
                        }

                        LOGGER.debug(String.format("Non-ICC APP2 segment skipped. Length [%d]", payload.length));
                    }

                    else
                    {
                        LOGGER.debug(String.format("Unhandled segment [0xFF%02X] skipped. Length [%d]", segment.getFlag(), length));
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
     * Reads the next JPEG segment marker from the input stream.
     *
     * @param reader
     *        the input stream of the JPEG file, positioned at the current read cursor
     * @return a JpgSegmentConstants value representing the marker and its flag, or null if
     *         end-of-file is reached
     *
     * @throws IOException
     *         if an I/O error occurs while reading from the stream
     * @implNote it skips any non-0xFF bytes to re-synchronise with the next marker, providing
     *           robustness against minor stream corruption.
     */
    private JpgSegmentConstants fetchNextSegment(ImageRandomAccessReader reader) throws IOException
    {
        try
        {
            int fillCount = 0;

            while (true)
            {
                int marker;
                int flag;

                marker = reader.readUnsignedByte();

                if (marker != 0xFF)
                {
                    // resync to marker
                    continue;
                }

                flag = reader.readUnsignedByte();

                /*
                 * In some cases, JPEG allows multiple 0xFF bytes (fill or padding bytes) before the
                 * actual segment flag. These are not part of any segment and should be skipped to
                 * find the next true segment type. A warning is logged and parsing is terminated if
                 * an excessive number of consecutive 0xFF fill bytes are found, as this may
                 * indicate a malformed or corrupted file.
                 */
                while (flag == 0xFF)
                {
                    fillCount++;

                    // Arbitrary limit to prevent an infinite loop
                    if (fillCount > PADDING_LIMIT)
                    {
                        LOGGER.warn("Excessive 0xFF padding bytes detected, possible file corruption");
                        return null;
                    }

                    flag = reader.readUnsignedByte();

                }

                if (!(flag >= JpgSegmentConstants.RST0.getFlag() && flag <= JpgSegmentConstants.RST7.getFlag()) && flag != JpgSegmentConstants.UNKNOWN.getFlag())
                {
                    LOGGER.debug(String.format("Segment flag [%s] detected", JpgSegmentConstants.fromBytes(marker, flag)));
                }

                return JpgSegmentConstants.fromBytes(marker, flag);
            }
        }

        catch (EOFException eof)
        {
            return null;
        }
    }

    /**
     * Reconstructs a complete XMP metadata block by concatenating multiple raw XMP segments within
     * the APP1 block.
     *
     * <p>
     * The Extensible Metadata Platform (XMP) specification allows XMP data to be stored across
     * multiple APP1 segments within a JPEG file. This method reassembles these fragments into a
     * single, cohesive byte array for parsing.
     * </p>
     *
     * @param segments
     *        the list of byte arrays, each representing a raw APP1 segment containing XMP data
     *
     * @return the concatenated byte array, or returns null if no segments are available
     */
    private byte[] reconstructXmpSegments(List<byte[]> segments)
    {
        if (!segments.isEmpty())
        {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
            {
                for (byte[] seg : segments)
                {
                    baos.write(seg);
                }

                LOGGER.debug(String.format("Successfully reconstructed XMP metadata from [%d] segment(s)", segments.size()));

                return baos.toByteArray();
            }

            catch (IOException exc)
            {
                LOGGER.error("Failed to concatenate XMP segments", exc);
            }
        }

        return null;
    }

    /**
     * Reconstructs a complete ICC metadata block by concatenating multiple ICC profile segments.
     * Segments are ordered by their sequence number as specified in the header.
     *
     * @param segments
     *        the list of raw ICC segments
     * @return the concatenated byte array, or returns null if no valid segments are available
     */
    private byte[] reconstructIccSegments(List<byte[]> segments)
    {
        /*
         * The header is 14 bytes: ICC_PROFILE\0 (12 bytes)
         * + 1 byte sequence number + 1 byte total count
         */
        int headerLength = ICC_IDENTIFIER.length + 2;

        if (segments.isEmpty())
        {
            return null;
        }

        for (byte[] seg : segments)
        {
            if (seg.length < headerLength)
            {
                LOGGER.error("One or more ICC segments are too short to contain the required header information");
                return null;
            }
        }

        /*
         * Get the total number of segments (M) from the first segment's header (byte at index 13)
         */
        int totalCount = segments.get(0)[ICC_IDENTIFIER.length + 1] & 0xFF;

        if (totalCount == 0 || totalCount != segments.size())
        {
            LOGGER.warn(String.format("ICC segment count mismatch. Expected [%d] segments, but found [%d]. Profile may be corrupted or incomplete.", totalCount, segments.size()));
            return null;
        }

        /* Make sure all segments share the same total count */
        for (byte[] seg : segments)
        {
            if ((seg[ICC_IDENTIFIER.length + 1] & 0xFF) != totalCount)
            {
                LOGGER.error("Inconsistent total segment count (M) found across ICC segments. Profile is corrupted");
                return null;
            }
        }

        /* Using an anonymous class */
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
                baos.write(Arrays.copyOfRange(seg, headerLength, seg.length));
            }

            LOGGER.debug(String.format("Successfully reconstructed ICC profile from [%d] segment(s)", totalCount));

            return baos.toByteArray();
        }

        catch (IOException exc)
        {
            LOGGER.error("Failed to concatenate ICC segments", exc);
        }

        return null;
    }
}