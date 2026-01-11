package batch;

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

public class HeifPropertyInjector
{
    public static void main(String[] args)
    {
        HeifPropertyInjector injector = new HeifPropertyInjector();
        Path input = Paths.get("IMG_0830.HEIC");
        Path output = Paths.get("IMG_0830_properties_11Jan26.heic");

        // Using BoxHandler code to get the offsets correctly
        try (BoxHandler handler = new BoxHandler(input))
        {
            if (!handler.parseMetadata())
            {
                System.err.println("Failed to parse HEIF structure.");
                return;
            }

            injector.injectProperties(handler, input, output);

            System.out.println("Success! 'imir' injected and container sizes updated.");
            System.out.println("New file saved to: " + output.toAbsolutePath());
        }

        catch (Exception e)
        {
            System.err.println("Error during injection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void injectProperties(BoxHandler handler, Path input, Path output) throws IOException
    {
        byte[] data = Files.readAllBytes(input);

        // 1. Identify the insertion point (end of 'ipco')
        ItemPropertiesBox iprp = handler.getIPRP();
        if (iprp == null) throw new IOException("Missing 'iprp' box.");

        Box ipco = iprp.getBoxList().stream()
                .filter(b -> "ipco".equals(b.getFourCC()))
                .findFirst()
                .orElseThrow(() -> new IOException("Missing 'ipco' box."));

        int insertAt = (int) (ipco.getStartOffset() + ipco.getBoxSize());

        // 2. Generate property payloads using helpers
        byte[] imir = createMirrorBox(0); // 0 for vertical axis flip
        byte[] clap = createClapBox(4032, 3024, 0, 0); // Define display area

        int totalShift = imir.length + clap.length;

        // 3. Construct new binary data
        byte[] newData = new byte[data.length + totalShift];
        ByteBuffer buffer = ByteBuffer.wrap(newData);

        buffer.put(data, 0, insertAt);
        buffer.put(imir);
        buffer.put(clap);
        buffer.put(data, insertAt, data.length - insertAt);

        // 4. Update the hierarchy of container sizes
        updateBoxSize(newData, (int) ipco.getStartOffset(), totalShift);
        updateBoxSize(newData, (int) iprp.getStartOffset(), totalShift);

        for (Box b : handler)
        {
            if ("meta".equals(b.getFourCC()))
            {
                updateBoxSize(newData, (int) b.getStartOffset(), totalShift);
                break;
            }
        }

        // 5. Adjust internal iloc pointers
        updateIlocOffsets(handler, newData, insertAt, totalShift);

        Files.write(output, newData);
    }

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

    private void updateIlocOffsets(BoxHandler handler, byte[] newData, int shiftPoint, int shiftAmount)
    {
        ItemLocationBox iloc = handler.getILOC();

        if (iloc == null)
        {
            return;
        }

        int size = iloc.getOffsetSize();

        for (ItemLocationEntry entry : iloc.getItems())
        {
            for (ExtentData extent : entry.getExtents())
            {
                if (extent.getExtentOffset() >= shiftPoint)
                {
                    int fieldPos = (int) extent.getOffsetFieldFilePosition();

                    // If the iloc box was located after our injection, adjust the target position
                    if (fieldPos >= shiftPoint)
                    {
                        fieldPos += shiftAmount;
                    }

                    long newOffset = extent.getExtentOffset() + shiftAmount;

                    if (size == 8)
                    {
                        ByteBuffer.wrap(newData, fieldPos, 8).putLong(newOffset);
                    }

                    else
                    {
                        ByteBuffer.wrap(newData, fieldPos, 4).putInt((int) newOffset);
                    }
                }
            }
        }
    }

    private void updateBoxSize(byte[] data, int offset, int extra)
    {
        int oldSize = ByteBuffer.wrap(data, offset, 4).getInt();

        ByteBuffer.wrap(data, offset, 4).putInt(oldSize + extra);
    }
}