package heif.boxes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import common.ByteValueConverter;
import common.ByteStreamReader;
import logger.LogFactory;

/**
 * Represents the {@code auxc} (Auxiliary Type Property Box), providing auxiliary image type
 * information.
 *
 * Auxiliary images shall be associated with an {@code AuxiliaryTypeProperty} as defined here. It
 * includes a URN identifying the type of the auxiliary image. it may also include other fields, as
 * required by the URN.
 *
 * <p>
 * Specification Reference: ISO/IEC 23008-12:2017 on Page 14
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class AuxiliaryTypePropertyBox extends FullBox
{
    private static final LogFactory LOGGER = LogFactory.getLogger(AuxiliaryTypePropertyBox.class);
    private final String auxType;
    private final byte[] auxSubtype;

    /**
     * Constructs an {@code AuxiliaryTypePropertyBox} from the box header and content.
     *
     * @param box
     *        the parent {@link Box}
     * @param reader
     *        the byte reader
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public AuxiliaryTypePropertyBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box, reader);

        auxSubtype = reader.readBytes((int) available());
        auxType = new String(ByteValueConverter.readFirstNullTerminatedByteArray(auxSubtype), StandardCharsets.UTF_8);

        commitSegment(reader.getCurrentPosition());
    }

    /**
     * Returns the auxiliary type string (URN or similar).
     *
     * @return auxiliary type
     */
    public String getAuxType()
    {
        return auxType;
    }

    /**
     * Returns the raw auxSubtype bytes, which may contain additional parameters after the
     * null-terminated string.
     *
     * @return a copy of the auxSubtype bytes
     */
    public byte[] getAuxSubtype()
    {
        return auxSubtype.clone();
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
        LOGGER.debug(String.format("%s%s '%s':\t\tauxType=%s", tab, this.getClass().getSimpleName(), getTypeAsString(), auxType));
    }
}