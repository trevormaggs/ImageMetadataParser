package heif.boxes;

import java.io.IOException;
import common.ByteStreamReader;
import common.Utils;
import logger.LogFactory;

/**
 * This derived Box class handles the Box identified as {@code irot} - Image rotation Box. For
 * technical details, refer to the Specification document -
 * {@code ISO/IEC 23008-12:2017 in Page 15}.
 *
 * The image rotation transformative item property of the {@code ImageRotationBox} box rotates the
 * reconstructed image of the associated image item in anti-clockwise direction in units of 90
 * degrees.
 * 
 * <p>
 * The specification defines the angle as :-
 * </p>
 * 
 * <ul>
 * <li>0: 0 degrees</li>
 * <li>1: 90 degrees anti-clockwise</li>
 * <li>2: 180 degrees anti-clockwise</li>
 * <li>3: 270 degrees anti-clockwise</li>
 * </ul>
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
public class ImageRotationBox extends Box
{
    private static final LogFactory LOGGER = LogFactory.getLogger(ImageRotationBox.class);
    private final int angle;
    private final int reserved;

    /**
     * This constructor creates a derived Box object whose aim is to retrieve the angle (in
     * anti-clockwise direction) in units of degrees.
     *
     * @param box
     *        the super Box object
     * @param reader
     *        a ByteStreamReader object for sequential byte array access
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public ImageRotationBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box);

        int data = reader.readUnsignedByte();

        // First 6 bits are reserved
        reserved = data & 0xFC;
        angle = data & 0x03;

        if (reserved != 0)
        {
            LOGGER.warn("irot box contains non-zero reserved bits [" + reserved + "]");
        }
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
        String tab = Utils.repeatPrint("\t", getHierarchyDepth());
        LOGGER.debug(String.format("%s%s '%s':\t\tangle=%d, reserved=%d", tab, this.getClass().getSimpleName(), getFourCC(), angle, reserved));
    }
}