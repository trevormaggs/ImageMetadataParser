package heif.boxes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import common.ByteValueConverter;
import common.ByteStreamReader;
import logger.LogFactory;

/**
 * This derived box, namely the {@code hdlr} type, declares media type of the track, and the process
 * by which the media-data in the track is presented. Typically, this is the contained box within
 * the parent {@code meta} box.
 *
 * This object consumes a total of 20 bytes, in addition to the variable length of the name string.
 * Exactly one instance of the {@code hdlr} box should exist.
 *
 * This implementation follows to the guidelines outlined in the Specification -
 * {@code ISO/IEC 14496-12:2015} on Page 29, and also {@code ISO/IEC 23008-12:2017 on Page 22}.
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
public class HandlerBox extends FullBox
{
    private static final LogFactory LOGGER = LogFactory.getLogger(HandlerBox.class);
    private static final byte[] PICT_BYTES = "pict".getBytes(StandardCharsets.UTF_8);
    private final String name;
    private final byte[] handlerType;

    /**
     * This constructor creates a derived Box object, extending the super class {@code FullBox} to
     * provide more specific information about this box.
     *
     * @param box
     *        the super Box object
     * @param reader
     *        a ByteStreamReader object for sequential byte array access
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public HandlerBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box, reader);

        /* Pre-defined = 0 */
        reader.skip(4);

        /* May be null */
        handlerType = reader.readBytes(4);

        /* Reserved = 0 */
        reader.skip(12);

        /*
         * Total length is expected to be 32 bytes:
         *
         * 4 bytes - Length
         * 4 bytes - Box Type
         * 4 bytes - from FullBox
         * 20 bytes - from this box plus n bytes for name
         */
        long remaining = available(reader);

        if (remaining > 0)
        {
            byte[] b = reader.readBytes((int) remaining);

            name = new String(ByteValueConverter.readFirstNullTerminatedByteArray(b), StandardCharsets.UTF_8);
        }

        else
        {
            name = "";
        }
    }

    /**
     * Returns a string representation of the Handler Type, providing information about the media
     * type for movie tracks or format type for meta box contents.
     *
     * @return the Handler Type as a string
     */
    public String getHandlerType()
    {
        return new String(handlerType, StandardCharsets.UTF_8);
    }

    /**
     * Returns a human-readable name for the track type, useful for debugging and inspection
     * purposes.
     *
     * @return string
     */
    public String getName()
    {
        return (name == null || name.isEmpty() ? "<Empty>" : name);
    }

    /**
     * Checks whether the handler type for still images or image sequences is the {@code pict} type.
     *
     * @return a boolean value of true if the handler is set for the {@code pict} type, otherwise
     *         false
     */

    public boolean containsPictHandler()
    {
        return Arrays.equals(handlerType, PICT_BYTES);
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
        String tab = Box.repeatPrint("\t", getHierarchyDepth());
        LOGGER.debug(String.format("%s%s '%s':\t\t'%s'", tab, this.getClass().getSimpleName(), getFourCC(), getHandlerType()));
    }
}