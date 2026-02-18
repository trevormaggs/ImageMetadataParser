package common;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.common.ImageMetadata.ImageMetadataItem;

public class MetadataDisplay
{
    public static void main(String[] args)
    {
        try
        {
            File file = new File("img/pool19.jpg");

            // 1. Get the metadata from the file
            ImageMetadata metadata = Imaging.getMetadata(file);

            if (metadata != null)
            {
                // 2. Extract a list of all metadata items (tags)
                List<? extends ImageMetadataItem> items = metadata.getItems();

                // 3. Loop through and display each item
                for (ImageMetadataItem item : items)
                {
                    System.out.println(item.toString());
                }
            }

            else
            {
                System.out.println("No metadata found for this file.");
            }

            try (BufferedReader br = Files.newBufferedReader(Paths.get("TIFF6tagname.txt"), StandardCharsets.UTF_8))
            {
                String line;

                while ((line = br.readLine()) != null)
                {
                    line = line.trim();

                    // Remove any comments
                    line = line.replaceAll("\\s*#.*", "");

                    // Skip any blank lines
                    if (line.length() > 0)
                    {                        
                        String regex = "(?<=[a-z0-9])(?=[A-Z])";
                        
                        line = line.replaceAll(regex, "_").toUpperCase();
                        //line = line.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
                        
                        System.out.printf("%s\n", line);
                    }
                }
            }

            catch (IOException exc)
            {
                exc.printStackTrace();
            }

        }

        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}