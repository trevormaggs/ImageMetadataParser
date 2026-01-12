package heif.boxes;

import java.io.IOException;
import common.ByteStreamReader;
import common.Utils;
import logger.LogFactory;

/**
 * This derived Box class handles the Box identified as {@code ispe} - Image spatial extents Box.
 * For technical details, refer to the Specification document -
 * {@code ISO/IEC 23008-12:2017 in Page 11}.
 *
 * The {@code ImageSpatialExtentsProperty} Box records the width and height of the associated image
 * item. Every image item shall be associated with one property of this type, prior to the
 * association of all transformative properties.
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
public class ImageSpatialExtentsProperty extends FullBox
{
    private static final LogFactory LOGGER = LogFactory.getLogger(ImageSpatialExtentsProperty.class);
    public final long imageWidth;
    public final long imageHeight;

    /**
     * This constructor creates a derived Box object whose aim is to gather information both on
     * width and height of the associated image item.
     *
     * @param box
     *        the super Box object
     * @param reader
     *        a ByteStreamReader object for sequential byte array access
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public ImageSpatialExtentsProperty(Box box, ByteStreamReader reader) throws IOException
    {
        super(box, reader);

        imageWidth = reader.readUnsignedInteger();
        imageHeight = reader.readUnsignedInteger();
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
        LOGGER.debug(String.format("%s%s '%s':\t\timageWidth=%d, imageHeight=%d", tab, this.getClass().getSimpleName(), getFourCC(), imageWidth, imageHeight));
    }
}