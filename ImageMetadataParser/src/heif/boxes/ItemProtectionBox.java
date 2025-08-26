package heif.boxes;

import java.nio.charset.StandardCharsets;
import common.SequentialByteReader;

/**
 * This derived Box class handles the Box identified as {@code ipro} - Item Protection Box. For
 * technical details, refer to the Specification document - ISO/IEC 14496-12:2015, on page 80.
 * 
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class ItemProtectionBox extends FullBox
{
    private int protectionCount;
    private ProtectionSchemeInfoBox[] protectionInfo;

    /**
     * This constructor creates a derived Box object, providing an array of item protection
     * information, for use by the Item Information Box.
     * 
     * @param box
     *        a parent Box object
     * @param reader
     *        a SequentialByteReader object for sequential byte array access
     */
    public ItemProtectionBox(Box box, SequentialByteReader reader)
    {
        super(box, reader);

        /*
         * aligned(8) class ItemProtectionBox
         * extends FullBox(‘ipro’, version = 0, 0)
         * {
         * unsigned int(16) protection_count;
         * for (i=1; i<=protection_count; i++)
         * {
         * ProtectionSchemeInfoBox protection_information;
         * }
         * }
         */

        protectionCount = reader.readUnsignedShort();
        protectionInfo = new ProtectionSchemeInfoBox[protectionCount];

        for (int i = 0; i < protectionCount; i++)
        {
            protectionInfo[i] = new ProtectionSchemeInfoBox(box, reader);
        }
    }

    static class ProtectionSchemeInfoBox extends Box
    {
        public ProtectionSchemeInfoBox(Box box, SequentialByteReader reader)
        {
            super(box);
        }

        class OriginalFormatBox extends Box
        {
            String dataFormat;

            public OriginalFormatBox(Box box, SequentialByteReader reader)
            {
                super(reader);

                dataFormat = new String(reader.readBytes(4), StandardCharsets.UTF_8);
            }
        }
    }
}