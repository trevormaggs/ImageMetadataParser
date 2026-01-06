package heif.boxes;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import common.ByteStreamReader;
import common.ByteValueConverter;
import heif.BoxFactory;
import heif.HeifBoxType;
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

        Box child = BoxFactory.createBox(reader);

        if (child != null && child.getHeifType() == HeifBoxType.DATA_REFERENCE)
        {
            dref = (DataReferenceBox) child;
        }

        else
        {
            dref = null;
            // reader.skip(available());
        }

        commitSegment(reader.getCurrentPosition());
    }

    /**
     * Returns a copy of {@code DataReferenceBox} object ({@code dref}) as a list.
     *
     * @return the list of {@code dref} box
     */
    @Override
    public List<Box> getBoxList()
    {
        return (dref != null) ? Collections.singletonList(dref) : Collections.emptyList();
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
        LOGGER.debug(String.format("%s%s '%s':\t\t(%s)", tab, this.getClass().getSimpleName(), getFourCC(), getHeifType().getBoxCategory()));
    }

    /**
     * An inner class designed to fill up the {@code dref} box type.
     */
    public static class DataReferenceBox extends FullBox
    {
        private final DataEntryBox[] dataEntry;

        public DataReferenceBox(Box box, ByteStreamReader reader) throws IOException
        {
            super(box, reader);

            long entryCount = reader.readUnsignedInteger();

            this.dataEntry = new DataEntryBox[(int) entryCount];

            for (int i = 0; i < entryCount; i++)
            {
                this.dataEntry[i] = new DataEntryBox(new Box(reader), reader);
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
            return Collections.unmodifiableList(Arrays.asList(dataEntry));
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
            LOGGER.debug(String.format("%s%s '%s':\t\tentryCount=%d", tab, this.getClass().getSimpleName(), getFourCC(), dataEntry.length));
        }
    }

    /**
     * An inner class used to store a {@code DataEntryBox} object, containing information such as
     * URL/URN location and name.
     */
    public static class DataEntryBox extends FullBox
    {
        private String name = "";
        private String location = "";

        public DataEntryBox(Box header, ByteStreamReader reader) throws IOException
        {
            super(header, reader);

            // ISO 14496-12: Flag 0x000001 means "self-contained" (the data is in this file, so no
            // strings are present)

            String type = getFourCC();
            boolean selfContained = (getFlags() & 0x000001) != 0;

            if (available(reader) > 0)
            {
                byte[] rawData = reader.readBytes((int) available(reader));
                String[] parts = ByteValueConverter.splitNullDelimitedStrings(rawData);

                if (type.startsWith("url"))
                {
                    if (!selfContained && parts.length > 0)
                    {
                        this.location = parts[0];
                    }
                }

                else if (type.startsWith("urn"))
                {
                    if (parts.length > 0)
                    {
                        this.name = parts[0];
                    }

                    if (!selfContained && parts.length > 1)
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
            boolean selfContained = (getFlags() & 0x000001) != 0;

            StringBuilder sb = new StringBuilder().append(selfContained ? "(Self-Contained) " : "");

            if (!location.isEmpty())
            {
                sb.append("Location='").append(location).append("' ");
            }

            if (!name.isEmpty())
            {
                sb.append("Name='").append(name).append("'");
            }

            LOGGER.debug(String.format("%s%s '%s':\t\t%s", tab, this.getClass().getSimpleName(), getFourCC(), sb.toString().trim()));
        }
    }
}