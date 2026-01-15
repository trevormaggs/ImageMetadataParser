package common;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import heif.BoxHandler;
import heif.boxes.Box;
import heif.boxes.ItemLocationBox;
import heif.boxes.ItemLocationBox.ExtentData;
import heif.boxes.ItemLocationBox.ItemLocationEntry;
import heif.boxes.ItemPropertiesBox;
import heif.boxes.ItemPropertiesBox.ItemPropertyContainerBox;
import heif.boxes.ItemPropertyAssociationBox;

public class HeifPropertyInjector2
{
    /**
     * Creates an 'imir' (Image Mirroring) box payload.
     * 
     * @param axis
     *        0 for vertical axis (L-R flip), 1 for horizontal axis (T-B flip)
     */
    private byte[] createMirrorBox(int axis)
    {
        byte[] imir = new byte[9];
        ByteBuffer buf = ByteBuffer.wrap(imir);

        buf.putInt(9); // Size
        buf.put("imir".getBytes()); // Type
        buf.put((byte) (axis & 0x01)); // Axis (last bit)

        return imir;
    }

    /**
     * Creates a 'clap' (Clean Aperture) box payload.
     */
    private byte[] createClapBox(long w, long h, long hOff, long vOff)
    {
        byte[] clap = new byte[40];
        ByteBuffer buf = ByteBuffer.wrap(clap);

        buf.putInt(40); // Size
        buf.put("clap".getBytes()); // Type
        buf.putInt((int) w).putInt(1); // Width Numerator/Denominator
        buf.putInt((int) h).putInt(1); // Height N/D
        buf.putInt((int) hOff).putInt(1); // Horizontal Offset N/D
        buf.putInt((int) vOff).putInt(1); // Vertical Offset N/D

        return clap;
    }

    /**
     * Creates a 'pasp' (Pixel Aspect Ratio) box payload.
     * 
     * @param hSpacing
     *        relative width of a pixel
     * @param vSpacing
     *        relative height of a pixel
     */
    private byte[] createPaspBox(long hSpacing, long vSpacing)
    {
        byte[] pasp = new byte[16];
        ByteBuffer buf = ByteBuffer.wrap(pasp);

        buf.putInt(16); // Size
        buf.put("pasp".getBytes()); // Type
        buf.putInt((int) hSpacing);
        buf.putInt((int) vSpacing);

        return pasp;
    }

    private void updateBoxSize(byte[] data, int offset, int extra)
    {
        long oldSize = Integer.toUnsignedLong(ByteBuffer.wrap(data, offset, 4).getInt());

        ByteBuffer.wrap(data, offset, 4).putInt((int) (oldSize + extra));
    }

    private void incrementIpmaCount(byte[] newData, ItemPropertyAssociationBox ipma, int targetID, int added, int propShift)
    {
        int basePos = (int) ipma.getStartOffset() + propShift;

        // Safety check: Is this still an 'ipma' box?
        Utils.validateBoxBounds(newData, basePos, "ipma");

        int currentPos = basePos + 16; // Skip header and entry_count

        int idSize = (ipma.getVersion() == 1) ? 4 : 2;
        int indexSize = (ipma.isFlagSet(0x01)) ? 2 : 1;

        for (int i = 0; i < ipma.getEntryCount(); i++)
        {
            // Safety: Ensure we don't read past the buffer end
            if (!Utils.isSafeRange(newData, currentPos, idSize + 1))
            {
                throw new RuntimeException("Truncated IPMA entry data at " + currentPos);
            }

            if (ipma.getItemIDAt(i) == targetID)
            {
                int countPos = currentPos + idSize;
                int oldCount = newData[countPos] & 0xFF;
                newData[countPos] = (byte) (oldCount + added);

                return;
            }

            currentPos += idSize + 1 + (ipma.getAssociationCountAt(i) * indexSize);
        }
    }

    private void updateIlocOffsets(BoxHandler handler, byte[] newData, int shiftPoint, int shiftAmount)
    {
        ItemLocationBox iloc = handler.getILOC();

        if (iloc == null)
        {
            return;
        }

        int offsetSize = iloc.getOffsetSize();

        for (ItemLocationEntry entry : iloc.getItems())
        {
            for (ExtentData extent : entry.getExtents())
            {
                long currentDataOffset = extent.getAbsoluteOffset();
                int fieldPos = (int) extent.getOffsetFieldFilePosition();

                // 1. Shift the POSITION of the iloc field if the iloc box moved
                if (fieldPos >= shiftPoint)
                {
                    fieldPos += shiftAmount;
                }

                // 2. Shift the VALUE of the offset if the data it points to moved
                // This handles XMP, mdat, and everything else sitting after the injection
                if (currentDataOffset >= shiftPoint)
                {
                    long newOffsetValue = extent.getExtentOffset() + shiftAmount;

                    writeOffsetToBuffer(newData, fieldPos, newOffsetValue, offsetSize);

                    // Debug log to see exactly what is being moved
                    System.out.println("Item " + entry.getItemID() + " (Offset Value) shifted: " + currentDataOffset + " -> " + newOffsetValue);
                }
                else
                {
                    // If it's before the injection point (like a very early thumbnail or config),
                    // we leave the value alone.
                    System.out.println("Item " + entry.getItemID() + " (Offset Value) kept static.");
                }
            }
        }
    }

    /**
     * Writes the new offset into the byte array at the correct position,
     * respecting whether the file uses 4-byte or 8-byte offsets.
     */
    private void writeOffsetToBuffer(byte[] data, int position, long newOffset, int offsetSize)
    {
        if (offsetSize == 8)
        {
            // 64-bit offset
            ByteBuffer.wrap(data, position, 8).putLong(newOffset);
        }

        else if (offsetSize == 4)
        {
            // 32-bit offset
            ByteBuffer.wrap(data, position, 4).putInt((int) newOffset);
        }

        else
        {
            // This is rare in HEIC, but some files use 0 or 2.
            // If your parser supports them, add cases here.
            System.err.println("Unsupported offset size: " + offsetSize);
        }
    }

    private int findIpmaInsertionPoint(ItemPropertyAssociationBox ipma, int targetID)
    {
        // Start after FullBox header (12) and entry_count (4)
        int currentPos = (int) ipma.getStartOffset() + 16;

        int idSize = (ipma.getVersion() == 1) ? 4 : 2;
        int indexSize = (ipma.isFlagSet(0x01)) ? 2 : 1;

        for (int i = 0; i < ipma.getEntryCount(); i++)
        {
            int itemID = ipma.getItemIDAt(i);
            int assocCount = ipma.getAssociationCountAt(i);

            // Calculate the size of this specific entry in bytes
            int entrySize = idSize + 1 + (assocCount * indexSize);

            if (itemID == targetID)
            {
                // Return the position immediately after the last association of this item
                return currentPos + entrySize;
            }

            currentPos += entrySize;
        }

        throw new RuntimeException("Target Item ID " + targetID + " not found in IPMA.");
    }

    public void injectProperties(BoxHandler handler, Path input, Path output) throws IOException
    {
        byte[] data = Files.readAllBytes(input);

        int primaryItemID = (int) handler.getPITM().getItemID();
        ItemPropertiesBox iprp = handler.getIPRP();
        ItemPropertyContainerBox ipco = iprp.getItemPropertyContainerBox();
        ItemPropertyAssociationBox ipma = iprp.getItemPropertyAssociationBox();

        int firstNewIndex = ipco.getBoxList().size() + 1;
        int secondNewIndex = firstNewIndex + 1;
        int thirdNewIndex = secondNewIndex + 1;
        int[] newIndices = {firstNewIndex, secondNewIndex, thirdNewIndex};

        int ipcoInsertAt = (int) (ipco.getStartOffset() + ipco.getBoxSize());
        int ipmaInsertAt = findIpmaInsertionPoint(ipma, primaryItemID);

        byte[] imir = createMirrorBox(1); // Vertical flip
        byte[] clap = createClapBox(3024, 4032, 0, 0);
        byte[] pasp = createPaspBox(1, 1); // Square pixels (1:1)

        // Combine all three boxes into one buffer
        byte[] props = new byte[imir.length + clap.length + pasp.length];

        ByteBuffer propsBuf = ByteBuffer.wrap(props);
        propsBuf.put(imir).put(clap).put(pasp);

        // Make imir and pasp non-essential, and clap essential (0x80)
        boolean[] essentialFlags = {false, true, false};
        // Create associations
        byte[] newAssocs = createAssociationBlob(ipma, newIndices, essentialFlags);

        int propShift = props.length;
        int assocShift = newAssocs.length;
        int totalShift = props.length + newAssocs.length;

        // 3. STITCH NEW FILE
        byte[] newData = new byte[data.length + totalShift];
        ByteBuffer buffer = ByteBuffer.wrap(newData);

        buffer.put(data, 0, ipcoInsertAt);
        buffer.put(props);
        buffer.put(data, ipcoInsertAt, ipmaInsertAt - ipcoInsertAt);
        buffer.put(newAssocs);
        buffer.put(data, ipmaInsertAt, data.length - ipmaInsertAt);

        updateBoxSize(newData, (int) ipco.getStartOffset(), propShift);
        updateBoxSize(newData, (int) ipma.getStartOffset() + propShift, assocShift);
        updateBoxSize(newData, (int) iprp.getStartOffset(), totalShift);

        for (Box b : handler)
        {
            if ("meta".equals(b.getFourCC()))
            {
                updateBoxSize(newData, (int) b.getStartOffset(), totalShift);
                break;
            }
        }

        updateIlocOffsets(handler, newData, ipcoInsertAt, totalShift);
        incrementIpmaCount(newData, ipma, primaryItemID, 3, propShift); // Incrementing by 3 now

        Files.write(output, newData);
    }

    /**
     * Generates the correct byte sequence for associations based on the IPMA box configuration.
     * * @param ipma The existing IPMA box to match settings from.
     * 
     * @param propertyIndices
     *        The indices of the properties to associate.
     * @param essential
     *        A boolean array of the same length indicating if the property is essential.
     */
    private byte[] createAssociationBlob(ItemPropertyAssociationBox ipma, int[] propertyIndices, boolean[] essential)
    {
        int indexSize = (ipma.isFlagSet(0x01)) ? 2 : 1;
        byte[] blob = new byte[propertyIndices.length * indexSize];
        ByteBuffer buf = ByteBuffer.wrap(blob);

        for (int i = 0; i < propertyIndices.length; i++)
        {
            int index = propertyIndices[i];

            // The essential bit is the most significant bit of the index field
            if (essential[i])
            {
                int essentialBit = (indexSize == 2) ? 0x8000 : 0x80;
                index |= essentialBit;
            }

            if (indexSize == 2)
            {
                buf.putShort((short) index);
            }
            else
            {
                buf.put((byte) index);
            }
        }

        return blob;
    }

    public void verifyInjection(Path filePath)
    {
        System.out.println("\n--- Verifying Injection for: " + filePath.getFileName() + " ---");

        try (BoxHandler handler = new BoxHandler(filePath))
        {
            if (!handler.parseMetadata())
            {
                System.err.println("Verification Failed: Could not parse the new file.");
                return;
            }

            int primaryID = (int) handler.getPITM().getItemID();
            ItemPropertyAssociationBox ipma = (ItemPropertyAssociationBox) handler.getIPRP().getItemPropertyAssociationBox();

            // Use your existing logBoxInfo to see the internal state
            ipma.logBoxInfo();

            // Specific check for our new properties
            int[] indices = ipma.getPropertyIndicesArray(primaryID);

            System.out.println("Item " + primaryID + " is now associated with " + indices.length + " properties.");

            // Check the last two indices (our newly injected ones)
            if (indices.length >= 2)
            {
                int imirIndex = indices[indices.length - 2];
                int clapIndex = indices[indices.length - 1];

                System.out.println("Mirror Property Index: " + imirIndex);
                System.out.println("Crop Property Index: " + clapIndex);
            }
        }

        catch (Exception e)
        {
            System.err.println("Verification Error: " + e.getMessage());
        }
    }

    public static void main(String[] args)
    {
        HeifPropertyInjector2 injector = new HeifPropertyInjector2();
        Path input = Paths.get("IMG_0830.HEIC");
        Path output = Paths.get("IMG_0830_properties_13Jan26.heic");

        // Using BoxHandler code to get the offsets correctly
        try (BoxHandler handler = new BoxHandler(input))
        {
            if (!handler.parseMetadata())
            {
                System.err.println("Failed to parse HEIF structure.");
                return;
            }

            injector.injectProperties(handler, input, output);
            injector.verifyInjection(output);

            System.out.println("Success! transformative properties injected and container sizes updated.");
            System.out.println("New file saved to: " + output.toAbsolutePath());
        }

        catch (Exception e)
        {
            System.err.println("Error during injection: " + e.getMessage());
            e.printStackTrace();
        }
    }
}