package batch;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import heif.BoxHandler;
import heif.boxes.Box;
import heif.boxes.ItemPropertiesBox;

public class HeifPropertyInjector
{
    public static void main(String[] args)
    {
        Path input = Paths.get("IMG_0830.HEIC");
        Path output = Paths.get("IMG_0830_mirrored.heic");

        // We use your BoxHandler to get the offsets correctly
        try (BoxHandler handler = new BoxHandler(input))
        {
            if (!handler.parseMetadata())
            {
                System.err.println("Failed to parse HEIF structure.");
                return;
            }

            HeifPropertyInjector injector = new HeifPropertyInjector();

            injector.injectMirrorProperty(handler, input, output);

            System.out.println("Success! 'imir' injected and container sizes updated.");
            System.out.println("New file saved to: " + output.toAbsolutePath());

        }

        catch (Exception e)
        {
            System.err.println("Error during injection: " + e.getMessage());

            e.printStackTrace();
        }
    }

    /**
     * Surgically inserts the imir box and updates parent box headers.
     */
    public void injectMirrorProperty(BoxHandler handler, Path input, Path output) throws IOException
    {
        byte[] data = Files.readAllBytes(input);

        // 1. Locate the boxes using the handler we just parsed
        ItemPropertiesBox iprp = handler.getIPRP();
        if (iprp == null) throw new IOException("Could not find 'iprp' box.");

        Box ipco = iprp.getBoxList().stream().filter(b -> "ipco".equals(b.getFourCC())).findFirst().orElseThrow(() -> new IOException("Could not find 'ipco' box inside 'iprp'."));

        // The point to insert is the end of ipco
        int insertAt = (int) (ipco.getOffset() + ipco.getBoxSize());

        // 2. Prepare imir box (9 bytes)
        byte[] imir = {0, 0, 0, 9, 'i', 'm', 'i', 'r', 0x01}; // 0x01 = horizontal flip

        // 3. Construct new byte array
        byte[] newData = new byte[data.length + imir.length];

        // Part A: Everything before the end of ipco
        System.arraycopy(data, 0, newData, 0, insertAt);
        // Part B: The new imir box
        System.arraycopy(imir, 0, newData, insertAt, imir.length);
        // Part C: Everything after
        System.arraycopy(data, insertAt, newData, insertAt + imir.length, data.length - insertAt);

        // 4. Update the Size headers (Big Endian)
        // We must update every box in the chain: ipco -> iprp -> meta
        updateBoxSize(newData, (int) ipco.getOffset(), imir.length);
        updateBoxSize(newData, (int) iprp.getOffset(), imir.length);

        // Find the meta box (root container)
        // Note: Your handler likely stores the meta box in its heifBoxMap
        // We need its offset to update the total file hierarchy
        Box meta = handler.iterator().next(); // Usually the first root box is ftyp, but let's be
                                              // safe:
        for (Box b : handler)
        {
            if ("meta".equals(b.getFourCC()))
            {
                updateBoxSize(newData, (int) b.getOffset(), imir.length);
                break;
            }
        }

        Files.write(output, newData);
    }

    private void updateBoxSize(byte[] data, int offset, int extra)
    {
        // Read the current 4-byte size
        int oldSize = ByteBuffer.wrap(data, offset, 4).getInt();
        // Write back the new size
        ByteBuffer.wrap(data, offset, 4).putInt(oldSize + extra);
    }
}