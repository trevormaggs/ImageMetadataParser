package common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import heif.BoxHandler;
import heif.boxes.ItemLocationBox;
import heif.boxes.ItemLocationBox.ExtentData;
import heif.boxes.ItemLocationBox.ItemLocationEntry;
import heif.boxes.ItemPropertiesBox;
import heif.boxes.ItemPropertiesBox.ItemPropertyContainerBox;
import heif.boxes.ItemPropertyAssociationBox;

/**
 * Provides functionality to inject transformative image properties (Mirroring, Clean Aperture, and
 * Pixel Aspect Ratio) into an existing HEIF/HEIC file.
 * 
 * <p>
 * This class performs binary surgery on the ISO-BMFF structure, managing box size updates, property
 * associations, and file offset shifts to ensure the resulting file remains valid.
 * </p>
 */
public class HeifPropertyInjector
{
    /**
     * Injects 'imir', 'clap', and 'pasp' properties into the HEIF file.
     * 
     * @param handler
     *        the parsed BoxHandler containing the HEIF structure
     * @param input
     *        the path to the source HEIF file
     * @param output
     *        the path where the modified HEIF file will be saved
     * 
     * @throws IOException
     *         if file reading or writing fails
     */
    public void injectProperties(BoxHandler handler, Path input, Path output) throws IOException
    {
        byte[] data = Files.readAllBytes(input);

        int primaryItemID = (int) handler.getPITM().getItemID();
        ItemPropertiesBox iprp = handler.getIPRP();
        ItemPropertyContainerBox ipco = iprp.getItemPropertyContainerBox();
        ItemPropertyAssociationBox ipma = iprp.getItemPropertyAssociationBox();

        // Create Property Payloads
        byte[] imir = createMirrorBox(1); // Vertical axis (T-B flip)
        byte[] clap = createClapBox(3024, 4032, 0, 0);
        byte[] pasp = createPaspBox(1, 1);

        byte[] props = new byte[imir.length + clap.length + pasp.length];
        ByteBuffer.wrap(props).put(imir).put(clap).put(pasp);

        // Prepare Associations in IPMA
        int firstNewIdx = ipco.getBoxList().size() + 1;
        int[] indices = {firstNewIdx, firstNewIdx + 1, firstNewIdx + 2};
        boolean[] essential = {false, true, false};
        byte[] assocs = createAssociationBlob(ipma, indices, essential);

        // Determine Injection Topology
        // We calculate where to insert properties (ipco) and associations (ipma)
        int ipcoAt = (int) (ipco.getStartOffset() + ipco.getBoxSize());
        int ipmaAt = findIpmaInsertionPoint(ipma, primaryItemID);

        int firstPt = Math.min(ipcoAt, ipmaAt);
        int secondPt = Math.max(ipcoAt, ipmaAt);
        byte[] firstLoad = (ipcoAt < ipmaAt) ? props : assocs;
        byte[] secondLoad = (ipcoAt < ipmaAt) ? assocs : props;

        // Stitch File Segments
        int totalShift = props.length + assocs.length;
        byte[] newData = new byte[data.length + totalShift];
        ByteBuffer buffer = ByteBuffer.wrap(newData);

        buffer.put(data, 0, firstPt);
        buffer.put(firstLoad);
        buffer.put(data, firstPt, secondPt - firstPt);
        buffer.put(secondLoad);
        buffer.put(data, secondPt, data.length - secondPt);

        // Update Parent Box Sizes (ipco, ipma, iprp, meta)
        // These updates account for the recursive nature of the ISO-BMFF box structure.
        updateBoxSize(newData, (int) ipco.getStartOffset(), props.length, ipcoAt, ipmaAt, props.length, assocs.length);
        updateBoxSize(newData, (int) ipma.getStartOffset(), assocs.length, ipcoAt, ipmaAt, props.length, assocs.length);
        updateBoxSize(newData, (int) iprp.getStartOffset(), totalShift, ipcoAt, ipmaAt, props.length, assocs.length);
        updateBoxSize(newData, (int) handler.getMETA().getStartOffset(), totalShift, ipcoAt, ipmaAt, props.length, assocs.length);

        // Perform Internal Structure Surgery
        // Adjusts internal item location offsets and property association counts.
        updateIlocOffsets(handler, newData, ipcoAt, ipmaAt, props.length, assocs.length);
        incrementIpmaCount(newData, ipma, primaryItemID, 3, ipcoAt, ipmaAt, props.length, assocs.length);

        Files.write(output, newData);
    }

    /**
     * Updates the 4-byte size field of a box while accounting for the "double-shift" phenomenon,
     * where the size field itself may have moved due to an injection.
     * 
     * @param newData
     *        the modified file buffer
     * @param origOff
     *        original file offset of the box
     * @param extra
     *        bytes to add to the size
     * @param ipcoAt
     *        offset where properties were injected
     * @param ipmaAt
     *        offset where associations were injected
     * @param pLen
     *        length of the property injection
     * @param aLen
     *        length of the association injection
     */
    private void updateBoxSize(byte[] newData, int origOff, int extra, int ipcoAt, int ipmaAt, int pLen, int aLen)
    {
        int pos = origOff;

        if (origOff >= ipcoAt)
        {
            pos += pLen;
        }

        if (origOff >= ipmaAt)
        {
            pos += aLen;
        }

        int oldSize = ByteBuffer.wrap(newData, pos, 4).getInt();

        ByteBuffer.wrap(newData, pos, 4).putInt(oldSize + extra);
    }

    /**
     * Increments the association count for a specific item within the 'ipma' box.
     * 
     * <p>
     * This is required because we are adding three new properties to the target item. The method
     * first calculates the 'ipma' box's new location in the shifted byte array, then iterates
     * through the item entries to find the targetID and patches the count byte.
     * </p>
     *
     * @param newData
     *        the modified file buffer containing the injected data
     * @param ipma
     *        the original parsed ItemPropertyAssociationBox for structural reference
     * @param targetID
     *        the ID of the item (usually the Primary Item ID) to update
     * @param added
     *        the number of new properties being associated (e.g., 3)
     * @param ipcoAt
     *        the file offset where the 'ipco' properties were injected
     * @param ipmaAt
     *        the file offset where the 'ipma' associations were injected
     * @param pLen
     *        the byte length of the injected properties
     * @param aLen
     *        the byte length of the injected associations
     */
    private void incrementIpmaCount(byte[] newData, ItemPropertyAssociationBox ipma, int targetID, int added, int ipcoAt, int ipmaAt, int pLen, int aLen)
    {
        int pos = (int) ipma.getStartOffset();

        if (pos >= ipcoAt)
        {
            pos += pLen;
        }

        if (pos >= ipmaAt)
        {
            pos += aLen;
        }

        /* Skip Box Header(8) + FullBox(4) + EntryCount(4) 3ithin IPMA */
        int currentPos = pos + 16;
        int idSize = (ipma.getVersion() == 1) ? 4 : 2;
        int indexSize = (ipma.isFlagSet(0x01)) ? 2 : 1;

        for (int i = 0; i < ipma.getEntryCount(); i++)
        {
            if (ipma.getItemIDAt(i) == targetID)
            {
                int countPos = currentPos + idSize;

                newData[countPos] = (byte) ((newData[countPos] & 0xFF) + added);

                return;
            }

            currentPos += idSize + 1 + (ipma.getAssociationCountAt(i) * indexSize);
        }
    }

    /**
     * Audits and updates the 'iloc' (Item Location) box to maintain data integrity.
     * 
     * This method performs two critical coordinate shifts:
     * 
     * 1. Field Position Shift: If the iloc box itself was located after an injection point, the
     * physical address where we write the offset must be shifted.
     * 
     * 2. Offset Value Shift: If the data (EXIF, mdat, etc.) that the offset points to has moved
     * down the file, the value stored in that field must be incremented.
     *
     * @param handler
     *        the box handler containing the original iloc metadata
     * @param newData
     *        the new byte array being modified
     * @param ipcoAt
     *        the file offset where the 'ipco' properties were injected
     * @param ipmaAt
     *        the file offset where the 'ipma' associations were injected
     * @param pLen
     *        the byte length of the injected properties
     * @param aLen
     *        the byte length of the injected associations
     */
    private void updateIlocOffsets(BoxHandler handler, byte[] newData, int ipcoAt, int ipmaAt, int pLen, int aLen)
    {
        ItemLocationBox iloc = handler.getILOC();

        if (iloc != null)
        {
            int size = iloc.getOffsetSize();

            for (ItemLocationEntry entry : iloc.getItems())
            {
                for (ExtentData extent : entry.getExtents())
                {
                    int fieldPos = (int) extent.getOffsetFieldFilePosition();
                    long originalAbs = extent.getAbsoluteOffset();
                    long newValue = extent.getExtentOffset();

                    // Shift the physical position of the offset field
                    if (fieldPos >= ipcoAt)
                    {
                        fieldPos += pLen;
                    }

                    if (fieldPos >= ipmaAt)
                    {
                        fieldPos += aLen;
                    }

                    // Shift the value stored in the offset field
                    if (originalAbs >= ipcoAt)
                    {
                        newValue += pLen;
                    }

                    if (originalAbs >= ipmaAt)
                    {
                        newValue += aLen;
                    }

                    // Log the transformation for debugging
                    System.out.println("Item " + entry.getItemID() + " (Offset Value) shifted: " + extent.getExtentOffset() + " -> " + newValue);

                    if (size == 8)
                    {
                        ByteBuffer.wrap(newData, fieldPos, 8).putLong(newValue);
                    }

                    else
                    {
                        ByteBuffer.wrap(newData, fieldPos, 4).putInt((int) newValue);
                    }
                }
            }
        }
    }

    /**
     * Generates the correct byte sequence for associations based on the IPMA box configuration.
     * 
     * @param ipma
     *        The existing IPMA box to match version and flags
     * @param indices
     *        array of property indices to associate
     * @param essential
     *        array indicating if each property is essential (must be understood by decoder)
     * @return a byte array containing the association entries
     */
    private byte[] createAssociationBlob(ItemPropertyAssociationBox ipma, int[] indices, boolean[] essential)
    {
        int size = (ipma.isFlagSet(0x01)) ? 2 : 1;
        byte[] b = new byte[indices.length * size];
        ByteBuffer buf = ByteBuffer.wrap(b);

        for (int i = 0; i < indices.length; i++)
        {
            int val = indices[i];

            /* Essential bit is the most significant bit of the index field */
            if (essential[i])
            {
                val |= (size == 2 ? 0x8000 : 0x80);
            }

            if (size == 2)
            {
                buf.putShort((short) val);
            }

            else
            {
                buf.put((byte) val);
            }
        }
        return b;
    }

    /**
     * Locates the exact byte position within the IPMA box to insert new associations
     * for a target Item ID.
     */
    private int findIpmaInsertionPoint(ItemPropertyAssociationBox ipma, int targetID)
    {
        /* Position starts after FullBox header (12) plus entry_count (4) within IPMA */
        int pos = (int) ipma.getStartOffset() + 16;
        int idSize = (ipma.getVersion() == 1) ? 4 : 2;
        int indexSize = (ipma.isFlagSet(0x01)) ? 2 : 1;

        for (int i = 0; i < ipma.getEntryCount(); i++)
        {
            /*
             * Version 1: item_ID (2) + association_count (1) * [essential + property_index](1)
             * Version 2: item_ID (4) + association_count (1) * [essential + property_index](2)
             */
            int len = idSize + 1 + (ipma.getAssociationCountAt(i) * indexSize);

            if (ipma.getItemIDAt(i) == targetID)
            {
                /* Return the position immediately after the last association of this item */
                return pos + len;
            }

            pos += len;
        }

        throw new RuntimeException("Target ID not found in IPMA");
    }

    /**
     * Creates an 'imir' (Image Mirroring) box payload.
     * * @param axis 0 for vertical axis (L-R flip), 1 for horizontal axis (T-B flip)
     * 
     * @return Byte array of the imir box.
     */
    private byte[] createMirrorBox(int axis)
    {
        byte[] imir = new byte[9];
        ByteBuffer buf = ByteBuffer.wrap(imir);

        buf.putInt(9);
        buf.put("imir".getBytes());
        buf.put((byte) (axis & 0x01));

        return imir;
    }

    /**
     * Creates a 'clap' (Clean Aperture) box payload.
     * 
     * @param w
     *        width numerator
     * @param h
     *        height numerator
     * @param hOff
     *        horizontal offset numerator
     * @param vOff
     *        vertical offset numerator
     * @return byte array of the clap box
     */
    private byte[] createClapBox(long w, long h, long hOff, long vOff)
    {
        byte[] clap = new byte[40];
        ByteBuffer buf = ByteBuffer.wrap(clap);

        buf.putInt(40);
        buf.put("clap".getBytes());
        buf.putInt((int) w).putInt(1);
        buf.putInt((int) h).putInt(1);
        buf.putInt((int) hOff).putInt(1);
        buf.putInt((int) vOff).putInt(1);

        return clap;
    }

    /**
     * Creates a 'pasp' (Pixel Aspect Ratio) box payload.
     * 
     * @param hSpacing
     *        relative width of a pixel
     * @param vSpacing
     *        relative height of a pixel
     * @return byte array of the pasp box
     */
    private byte[] createPaspBox(long hSpacing, long vSpacing)
    {
        byte[] pasp = new byte[16];
        ByteBuffer buf = ByteBuffer.wrap(pasp);

        buf.putInt(16);
        buf.put("pasp".getBytes());
        buf.putInt((int) hSpacing);
        buf.putInt((int) vSpacing);

        return pasp;
    }

    /**
     * Entry point for the injection process.
     */
    public static void main(String[] args)
    {
        HeifPropertyInjector injector = new HeifPropertyInjector();
        Path input = Paths.get("IMG_0830.HEIC");
        Path output = Paths.get("IMG_0830_properties_13Jan26.heic");

        try (BoxHandler handler = new BoxHandler(input))
        {
            if (handler.parseMetadata())
            {
                injector.injectProperties(handler, input, output);
                System.out.println("Success! properties injected.");
                System.out.println("New file saved to: " + output.toAbsolutePath());
            }
        }

        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}