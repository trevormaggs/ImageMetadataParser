package jpg;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import batch.BatchMetadataUtils;
import common.AbstractImageParser;
import common.DigitalSignature;
import common.ImageFileInputStream;
import common.ImageReadErrorException;
import common.MetadataStrategy;
import logger.LogFactory;
import tif.DirectoryIFD;
import tif.DirectoryIFD.EntryIFD;
import tif.TifMetadata;
import tif.TifMetadataStrategy;
import tif.TifParser;
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
 * {@code http://ns.adobe.com/xap/1.0/} identifier to form a single XMP data block through byte
 * reconstruction.
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
    private MetadataStrategy<DirectoryIFD> metadata;
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
     * Constructs a new instance with the specified file path.
     *
     * @param fpath
     *        the path to the JPG file to be parsed
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
     * Removes the potential leading JPEG/EXIF identifier (APP1 marker) from the raw EXIF payload.
     *
     * @param data
     *        the raw EXIF chunk payload
     * @return the corrected EXIF data byte array
     */
    public static byte[] stripExifPreamble(byte[] data)
    {
        if (data.length >= JpgParser.EXIF_IDENTIFIER.length && Arrays.equals(Arrays.copyOf(data, JpgParser.EXIF_IDENTIFIER.length), JpgParser.EXIF_IDENTIFIER))
        {
            return Arrays.copyOfRange(data, JpgParser.EXIF_IDENTIFIER.length, data.length);
        }

        return data;
    }

    /**
     * Reads the JPG image file to extract all supported raw metadata segments (specifically EXIF
     * and XMP, if present), and uses the extracted data to initialise the necessary metadata
     * objects for later data retrieval.
     *
     * @return true once at least one metadata segment has been successfully parsed, otherwise false
     *
     * @throws ImageReadErrorException
     *         if a parsing or file reading error occurs
     */
    @Override
    public boolean readMetadata() throws ImageReadErrorException
    {
        try (ImageFileInputStream jpgStream = new ImageFileInputStream(getImageFile()))
        {
            segmentData = readMetadataSegments(jpgStream);
        }

        catch (IOException exc)
        {
            throw new ImageReadErrorException(exc);
        }

        return segmentData.hasMetadata();
    }

    /**
     * Retrieves the extracted Exif metadata from the JPG image file, or a fallback if unavailable.
     *
     * <p>
     * If the metadata has not yet been parsed and raw EXIF segment data is present, this method
     * triggers the parsing of the EXIF data, which is a TIFF structure. If parsing fails or no EXIF
     * segment is present, an empty {@link TifMetadata} object is returned as a fallback.
     * </p>
     *
     * @return a MetadataStrategy object, populated with EXIF data or empty
     */
    @Override
    public MetadataStrategy<DirectoryIFD> getMetadata()
    {
        if (metadata == null)
        {
            if (segmentData.getExif().isPresent())
            {
                metadata = TifParser.parseFromExifSegment(segmentData.getExif().get());

                if (metadata == null)
                {
                    LOGGER.warn("Raw EXIF segment found but parsing failed. Returning empty metadata");
                }
            }
        }

        return (metadata == null ? new TifMetadata() : metadata);
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
        MetadataStrategy<?> meta = getMetadata();
        StringBuilder sb = new StringBuilder();

        try
        {
            sb.append("\t\t\tJPG Metadata Summary").append(System.lineSeparator()).append(System.lineSeparator());
            sb.append(super.formatDiagnosticString());

            if (meta instanceof TifMetadataStrategy && ((TifMetadataStrategy) meta).hasExifData())
            {
                TifMetadataStrategy tif = (TifMetadataStrategy) meta;

                for (DirectoryIFD ifd : tif)
                {
                    sb.append("Directory Type - ")
                            .append(ifd.getDirectoryType().getDescription())
                            .append(String.format(" (%d entries)%n", ifd.size()))
                            .append(DIVIDER)
                            .append(System.lineSeparator());

                    for (EntryIFD entry : ifd)
                    {
                        String value = ifd.getString(entry.getTag());
                        sb.append(String.format(FMT, "Tag Name", entry.getTag() + " (Tag ID: " + String.format("0x%04X", entry.getTagID()) + ")"));
                        sb.append(String.format(FMT, "Field Type", entry.getFieldType() + " (count: " + entry.getCount() + ")"));
                        sb.append(String.format(FMT, "Value", (value == null || value.isEmpty() ? "Empty" : value)));
                        sb.append(System.lineSeparator());
                    }
                }
            }

            else
            {
                sb.append("No EXIF metadata found").append(System.lineSeparator());
            }

            sb.append(System.lineSeparator()).append(DIVIDER).append(System.lineSeparator());

            if (segmentData.getIcc().isPresent())
            {
                sb.append("ICC Profile Found: ").append(segmentData.getIcc().get().length).append(" bytes").append(System.lineSeparator());
                sb.append("    Note: Parser has concatenated all ICC segments.").append(System.lineSeparator());
            }

            else
            {
                sb.append("No ICC Profile found.").append(System.lineSeparator());
            }

            sb.append(System.lineSeparator());

            if (segmentData.getXmp().isPresent())
            {
                sb.append("XMP Data Found: ").append(segmentData.getXmp().get().length).append(" bytes").append(System.lineSeparator());
                sb.append("    Note: Parser has concatenated all XMP segments.").append(System.lineSeparator());
            }

            else
            {
                sb.append("No XMP Data found.").append(System.lineSeparator());
            }
        }

        catch (Exception exc)
        {
            sb.append("Error generating diagnostics: ").append(exc.getMessage()).append(System.lineSeparator());
            LOGGER.error("Diagnostics failed for file [" + getImageFile() + "]", exc);

            exc.printStackTrace();
        }

        return sb.toString();
    }

    /**
     * Reads the next JPEG segment marker from the input stream.
     *
     * @param stream
     *        the input stream of the JPEG file, positioned at the current read cursor
     * @return a JpgSegmentConstants value representing the marker and its flag, or null if
     *         end-of-file is reached
     *
     * @throws IOException
     *         if an I/O error occurs while reading from the stream
     */
    private JpgSegmentConstants fetchNextSegment(ImageFileInputStream stream) throws IOException
    {
        try
        {
            int fillCount = 0;

            while (true)
            {
                int marker;
                int flag;

                marker = stream.readUnsignedByte();

                if (marker != 0xFF)
                {
                    // resync to marker
                    continue;
                }

                flag = stream.readUnsignedByte();

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

                    // Arbitrary limit to prevent infinite loops
                    if (fillCount > PADDING_LIMIT)
                    {
                        LOGGER.warn("Excessive 0xFF padding bytes detected, possible file corruption");
                        return null;
                    }

                    flag = stream.readUnsignedByte();

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
     * Reads all supported metadata segments, including EXIF, ICC and XMP, if present, from the JPEG
     * file stream.
     *
     * @param stream
     *        the input JPEG stream
     * @return a JpgSegmentData record containing the byte arrays for any found segments
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    private JpgSegmentData readMetadataSegments(ImageFileInputStream stream) throws IOException
    {
        byte[] exifSegment = null;
        List<byte[]> iccSegments = new ArrayList<>();
        List<byte[]> xmpSegments = new ArrayList<>();

        while (true)
        {
            JpgSegmentConstants segment = fetchNextSegment(stream);

            if (segment == null)
            {
                break;
            }

            if (!segment.hasLengthField())
            {
                if (segment == JpgSegmentConstants.END_OF_IMAGE || segment == JpgSegmentConstants.START_OF_STREAM)
                {
                    LOGGER.debug("End marker reached, stopping metadata parsing");
                    break;
                }
            }

            else
            {
                int length = stream.readUnsignedShort() - 2;

                // Each APP segment is limit to 64K in size
                if (length <= 0 || length > 65535)
                {
                    continue;
                }

                byte[] payload = stream.readBytes(length);

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

                    // If it wasn't EXIF (checked above) or XMP, it's a generic, unhandled APP1.
                    LOGGER.debug(String.format("Non-EXIF/XMP APP1 segment skipped. Length [%d]", payload.length));
                }

                else if (segment == JpgSegmentConstants.APP2_SEGMENT)
                {
                    if (payload.length >= ICC_IDENTIFIER.length && Arrays.equals(Arrays.copyOfRange(payload, 0, ICC_IDENTIFIER.length), ICC_IDENTIFIER))
                    {
                        iccSegments.add(payload);
                        LOGGER.debug(String.format("Valid ICC APP2 segment found. Length [%d]", payload.length));
                    }

                    else
                    {
                        LOGGER.debug(String.format("Non-ICC APP2 segment skipped. Length [%d]", payload.length));
                    }
                }

                else
                {
                    LOGGER.debug(String.format("Unhandled segment [0xFF%02X] skipped. Length [%d]", segment.getFlag(), length));
                }
            }
        }

        return new JpgSegmentData(exifSegment, reconstructXmpSegments(xmpSegments), reconstructIccSegments(iccSegments));
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
     *
     * @return the concatenated byte array, or returns null if no valid segments are available
     */
    private byte[] reconstructIccSegments(List<byte[]> segments)
    {
        // The header is 14 bytes, including 2 bytes for the sequence/total count
        final int headerLength = ICC_IDENTIFIER.length + 2;

        if (!segments.isEmpty())
        {
            segments.sort(new Comparator<byte[]>()
            {
                @Override
                public int compare(byte[] s1, byte[] s2)
                {
                    return Integer.compare(s1[ICC_IDENTIFIER.length], s2[ICC_IDENTIFIER.length]);
                }
            });

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
            {
                for (byte[] seg : segments)
                {
                    baos.write(Arrays.copyOfRange(seg, headerLength, seg.length));
                }

                return baos.toByteArray();
            }

            catch (IOException exc)
            {
                LOGGER.error("Failed to concatenate ICC segments", exc);
            }
        }

        return null;
    }

    // REMOVE IT
    public MetadataStrategy<?> getXmpInfo()
    {
        if (segmentData.getXmp().isPresent())
        {
            try
            {
                XmpHandler xmpHandler = new XmpHandler(segmentData.getXmp().get());

                if (xmpHandler.parseMetadata())
                {
                    LOGGER.info("XMP metadata parsed successfully.");

                    // System.out.printf("File: %s\n", getImageFile());
                    // System.out.printf("LOOK0: %s\n",
                    // xmpHandler.getXmpPropertyValue(XmpSchema.DC_CREATOR));
                    // System.out.printf("LOOK1: %s\n",
                    // xmpHandler.getXmpPropertyValue(XmpSchema.XAP_METADATADATE));
                    // System.out.printf("LOOK2: %s\n",
                    // xmpHandler.getXmpPropertyValue(XmpSchema.DC_TITLE));
                    // xmpHandler.testDump();
                }

                else
                {
                    LOGGER.warn("Failed to parse XMP metadata.");
                }
            }

            catch (ImageReadErrorException exc)
            {
                exc.printStackTrace();
            }
        }

        return null;
    }
}