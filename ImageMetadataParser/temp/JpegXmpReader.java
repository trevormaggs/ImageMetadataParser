package xmp;

import java.io.File;
import com.adobe.internal.xmp.XMPIterator;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.properties.XMPPropertyInfo;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.xmp.XmpDirectory;

public class JpegXmpReader
{
    public static void main(String[] args) throws Exception
    {
        File jpegFile = new File("img\\pool19.jpg");

        // Step 1: Extract metadata from JPEG
        Metadata metadata = ImageMetadataReader.readMetadata(jpegFile);

        // Step 2: Find XMP directory
        XmpDirectory xmpDir = metadata.getFirstDirectoryOfType(XmpDirectory.class);
        if (xmpDir == null)
        {
            System.out.println("No XMP data found in file.");
            return;
        }

        // Step 3: Parse XMP with Adobe toolkit
        XMPMeta xmpMeta = XMPMetaFactory.parseFromBuffer(xmpDir.getXMPMeta().serializeToBuffer());

        // Step 4: Iterate all schemas/namespaces/properties
        XMPIterator iter = xmpMeta.iterator();
        while (iter.hasNext())
        {
            Object o = iter.next();
            if (o instanceof XMPPropertyInfo)
            {
                XMPPropertyInfo prop = (XMPPropertyInfo) o;
                System.out.println(
                        "Namespace: " + prop.getNamespace() +
                                " | Path: " + prop.getPath() +
                                " | Value: " + prop.getValue());
            }
        }
    }
}
