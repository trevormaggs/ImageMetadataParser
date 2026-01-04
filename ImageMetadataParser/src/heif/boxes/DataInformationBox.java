package heif.boxes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import common.ByteValueConverter;
import common.ByteStreamReader;
import logger.LogFactory;

/**
 * This derived Box class handles the Box identified as {@code dinf} - Data Information Box. For
 * technical details, refer to the Specification document - {@code ISO/IEC 14496-12:2015} on Page 45
 * to 46.
 *
 * The data information box contains objects that declare the location of the media information in a
 * track.
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
public class DataInformationBox extends Box
{
    private static final LogFactory LOGGER = LogFactory.getLogger(DataInformationBox.class);
    private final DataReferenceBox dref;

    /**
     * This constructor creates a derived Box object, providing additional information from other
     * contained boxes, specifically {@code dref} - Data Reference Box and its nested contained
     * boxes, where further additional information on URL location and name is provided.
     *
     * @param box
     *        the super Box object
     * @param reader
     *        a ByteStreamReader object for sequential byte array access
     * 
     * @throws IOException
     *         if an I/O error occurs
     */
    public DataInformationBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box);

        markSegment(reader.getCurrentPosition());
        dref = new DataReferenceBox(new Box(reader), reader);
        commitSegment(reader.getCurrentPosition());
    }

    /**
     * Returns a combined list of all boxes contained in this {@code DataInformationBox},
     * particularly the DataReferenceBox ({@code dref}).
     *
     * @return a combined list of Box objects in reading order
     */
    @Override
    public List<Box> getBoxList()
    {
        List<Box> combinedList = new ArrayList<>();
        combinedList.add(dref);

        return combinedList;
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
        LOGGER.debug(String.format("%s%s '%s':\t\t(%s)", tab, this.getClass().getSimpleName(), getTypeAsString(), getHeifType().getBoxCategory()));
    }

    /**
     * An inner class designed to fill up the {@code dref} box type.
     */
    public static class DataReferenceBox extends FullBox
    {
        public int entryCount;
        public DataEntryBox[] dataEntry;

        public DataReferenceBox(Box box, ByteStreamReader reader) throws IOException
        {
            super(box, reader);

            entryCount = (int) reader.readUnsignedInteger();
            dataEntry = new DataEntryBox[entryCount];

            for (int i = 0; i < entryCount; i++)
            {
                dataEntry[i] = new DataEntryBox(new Box(reader), reader);
            }
        }

        /**
         * Returns a copy of contained boxes defined as {@code DataEntryBox} entries.
         *
         * @return the list of DataEntryBox objects
         */
        @Override
        public List<Box> getBoxList()
        {
            return new ArrayList<>(Arrays.asList(dataEntry));
        }

        /**
         * Logs the box hierarchy and internal entry data at the debug level.
         *
         * <p>
         * It provides a visual representation of the box's HEIF/ISO-BMFF structure. It is intended
         * for tree traversal and file inspection during development and degugging if required.
         * </p>
         */
        @Override
        public void logBoxInfo()
        {
            String tab = Box.repeatPrint("\t", getHierarchyDepth());
            LOGGER.debug(String.format("%s%s '%s':\t\tentryCount=%d", tab, this.getClass().getSimpleName(), getTypeAsString(), entryCount));
        }
    }

    /**
     * An inner class used to store a {@code DataEntryBox} object, containing information such as
     * URL location and name.
     */

    public static class DataEntryBox extends FullBox
    {
        public String name = "";
        public String location = "";

        public DataEntryBox(Box header, ByteStreamReader reader) throws IOException
        {
            super(header, reader);

            // ISO 14496-12: Flag 0x000001 means "self-contained" (the data is in this file, so no
            // strings are present)

            String type = getTypeAsString();
            boolean isSelfContained = (getFlags() & 0x000001) != 0;
            byte[] rawData = reader.readBytes((int) available());
            String[] parts = ByteValueConverter.splitNullDelimitedStrings(rawData);

            if (type.startsWith("url"))
            {
                if (!isSelfContained && available() > 0)
                {
                    if (parts.length > 0)
                    {
                        this.location = parts[0];
                    }
                }
            }

            else if (type.startsWith("urn"))
            {
                if (available() > 0)
                {
                    if (parts.length > 0)
                    {
                        this.name = parts[0];
                    }
                }

                if (!isSelfContained && available() > 0)
                {
                    if (parts.length > 1)
                    {
                        this.location = parts[1];
                    }
                }
            }
        }

        @Override
        public void logBoxInfo()
        {
            String tab = Box.repeatPrint("\t", getHierarchyDepth());
            boolean isSelf = (getFlags() & 0x000001) != 0;
            String info = isSelf ? "(Self-Contained)" : String.format("Location='%s'%s", location, name.isEmpty() ? "" : ", Name='" + name + "'");

            LOGGER.debug(String.format("%s%s '%s':\t\t%s", tab, this.getClass().getSimpleName(), getTypeAsString(), info));
        }
    }
}