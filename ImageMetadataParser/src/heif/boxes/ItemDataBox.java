package heif.boxes;

import java.io.IOException;
import common.ByteStreamReader;
import logger.LogFactory;

/**
 * This derived Box class handles the Box identified as {@code idat} - Item Data Box. For technical
 * details, refer to the Specification document - {@code ISO/IEC 14496-12:2015} on Page 86.
 *
 * This box contains the data of metadata items that use the construction method indicating that an
 * item’s data extents are stored within this box.
 *
 * <p>
 * <strong>API Note:</strong> This implementation assumes a flat byte array. No item parsing is
 * performed beyond raw byte extraction. Further testing is needed for edge cases and compatibility.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class ItemDataBox extends Box
{
    private static final LogFactory LOGGER = LogFactory.getLogger(ItemDataBox.class);
    private final byte[] data;

    /**
     * This constructor creates a derived Box object, providing additional data of metadata items.
     *
     * @param box
     *        the super Box object
     * @param reader
     *        a ByteStreamReader object for sequential byte array access
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public ItemDataBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box);

        setCurrentBytePosition(reader.getCurrentPosition());

        long count = available();
        data = reader.readBytes((int) count);

        setExitBytePosition(reader.getCurrentPosition());
    }

    /**
     * Returns a copy of the raw data stored in this {@code ItemDataBox} resource.
     *
     * @return the item data as a byte array
     */
    public byte[] getData()
    {
        return data.clone();
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
        String tab = Box.repeatPrint("\t", getHierarchyDepth());
        LOGGER.debug(String.format("%s%s '%s':", tab, this.getClass().getSimpleName(), getTypeAsString()));

        if (data.length < 65)
        {
            sb.append(String.format("\t\tData bytes: "));

            for (byte b : data)
            {
                sb.append(String.format("0x%02X ", b));
            }
        }

        else
        {
            sb.append(String.format("\t\tData size: %d bytes (hex dump omitted)", data.length));
        }

        LOGGER.debug(sb.toString());
    }
}