package heif.boxes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import common.ByteStreamReader;
import logger.LogFactory;

/**
 * The {@code FileTypeBox} must be the first box in every HEIF-based file, including HEIC still
 * image files. It provides high-level file type information, including the major brand, minor
 * version, and a list of compatible brands.
 *
 * For technical details, refer to ISO/IEC 14496-12:2015, Page 7 (File Type Box).
 *
 * <p>
 * <strong>API Note:</strong> Additional testing is required to validate the reliability and
 * robustness of this implementation.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class FileTypeBox extends Box
{
    private static final LogFactory LOGGER = LogFactory.getLogger(FileTypeBox.class);
    private final byte[] majorBrand;
    private final long minorVersion;
    private final List<String> compatibleBrands;

    /**
     * Constructs a {@code FileTypeBox}, parsing its fields from the specified
     * {@link ByteStreamReader}.
     *
     * @param box
     *        the parent {@link Box} object containing size and type information
     * @param reader
     *        the byte reader for parsing box data
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public FileTypeBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box);

        markSegment(reader.getCurrentPosition());

        compatibleBrands = new ArrayList<>();
        majorBrand = reader.readBytes(4);
        minorVersion = reader.readUnsignedInteger();

        // 4 bytes majorBrand + 4 bytes minorVersion = 8 bytes
        long remaining = available() - 8;

        /*
         * Compatible brands start after the major brand and minor version.
         * Each compatible brand is 4 bytes.
         */
        for (int i = 0; i < remaining; i += 4)
        {
            compatibleBrands.add(new String(reader.readBytes(4), StandardCharsets.UTF_8));
        }

        commitSegment(reader.getCurrentPosition());
    }

    /**
     * Returns the primary brand identifier. Typically, for HEIC-based files, the major brand is
     * {@code heic}.
     *
     * @return the primary brand as a string
     */
    public String getMajorBrand()
    {
        return new String(majorBrand, StandardCharsets.UTF_8);
    }

    /**
     * Returns the minor version associated with the major brand.
     *
     * @return the minor version (usually 0 for HEIC files)
     */
    public long getMinorVersion()
    {
        return minorVersion;
    }

    /**
     * Returns an array of compatible brands as specified in the box, excluding the major brand.
     *
     * @return an array of compatible brand strings
     */
    public String[] getCompatibleBrands()
    {
        return compatibleBrands.toArray(new String[0]);
    }

    /**
     * Checks if the specified brand is listed in the compatible brands.
     *
     * @param brand
     *        the brand name to check
     *
     * @return boolean true if the brand is present, otherwise false
     */
    public boolean hasBrand(String brand)
    {
        for (String s : compatibleBrands)
        {
            if (s.equalsIgnoreCase(brand))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Logs the box hierarchy and internal entry data at the debug level.
     *
     * <p>
     * It provides a visual representation of the box's HEIF/ISO-BMFF structure. It is intended for
     * tree traversal and file inspection during development and degugging if required.
     * </p>
     */
    @Override
    public void logBoxInfo()
    {
        StringBuilder sb = new StringBuilder();

        sb.append(Box.repeatPrint("\t", getHierarchyDepth()));
        sb.append(String.format("%s '%s':\t\t\t\t", this.getClass().getSimpleName(), getTypeAsString()));
        sb.append(String.format("'major-brand=%s', ", getMajorBrand()));
        sb.append(String.format("compatible-brands='%s'", Arrays.toString(getCompatibleBrands())));

        LOGGER.debug(sb.toString());
    }
}