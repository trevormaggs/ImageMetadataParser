package heif.boxes;

import java.io.IOException;
import common.ByteStreamReader;
import logger.LogFactory;

/**
 * This derived Box class handles the Box identified as {@code imir} - Image Mirroring Box. For
 * technical details, refer to the Specification document -
 * {@code ISO/IEC 23008-12:2017 in Page 16}.
 * 
 * <p>
 * The image mirroring transformative item property mirrors the image about either a vertical or
 * horizontal axis as follows:
 * </p>
 * 
 * <table border="1">
 * <caption>Semantics</caption>
 * <tr>
 * <th>axis = 0</th>
 * <td>The image is mirrored about the vertical axis (a left-to-right flip)</td>
 * </tr>
 * <tr>
 * <th>axis = 1</th>
 * <td>The image is mirrored about the horizontal axis (a top-to-bottom flip)</td>
 * </tr>
 * </table>
 * 
 * <p>
 * <strong>API Note:</strong> Additional testing is required to validate the reliability and
 * robustness of this implementation.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 6 January 2026
 */
public class ImageMirrorBox extends Box
{
    private static final LogFactory LOGGER = LogFactory.getLogger(ImageMirrorBox.class);
    private final int axis;
    private final int reserved;

    /**
     * This constructor creates a derived Box object whose aim is to mirror the image either
     * vertically or horizontally about its axis.
     * 
     * The axis specifies a vertical (axis = 0) or horizontal (axis = 1) axis for the mirroring
     * operation.
     * 
     * @param box
     *        the super Box object
     * @param reader
     *        a ByteStreamReader object for sequential byte array access
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public ImageMirrorBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box);

        int data = reader.readUnsignedByte();

        // First 7 bits are reserved
        reserved = data & 0xFE;
        axis = data & 0x01;

        if (reserved != 0)
        {
            LOGGER.warn("imir box contains non-zero reserved bits [" + reserved + "]");
        }
    }

    /**
     * @return true if the image is mirrored about the vertical axis (left-to-right flip)
     */
    public boolean isLeftToRightFlip()
    {
        return axis == 0;
    }

    /**
     * @return true if the image is mirrored about the horizontal axis (top-to-bottom flip)
     */
    public boolean isTopToBottomFlip()
    {
        return axis == 1;
    }

    /**
     * Logs the box hierarchy and internal entry data at the debug level.
     *
     * <p>
     * It provides a visual representation of the box's HEIF/ISO-BMFF structure. It is intended for
     * tree traversal and file inspection during development and debugging if required.
     * </p>
     */
    @Override
    public void logBoxInfo()
    {
        String tab = Box.repeatPrint("\t", getHierarchyDepth());
        LOGGER.debug(String.format("%s%s '%s':\t\taxis=%d, reserved=%d", tab, this.getClass().getSimpleName(), getFourCC(), axis, reserved));
    }
}