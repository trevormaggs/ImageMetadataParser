package common;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.common.ImageMetadata.ImageMetadataItem;
import java.io.File;
import java.util.List;

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
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}