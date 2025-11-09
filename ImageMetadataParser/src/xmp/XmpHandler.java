package xmp;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPIterator;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.properties.XMPPropertyInfo;
import common.ImageHandler;
import common.ImageReadErrorException;
import logger.LogFactory;
import xmp.XmpHandler.XMPCoreProperty;

/**
 * Handles XMP metadata extraction from the raw XMP payload (an XML packet). This payload can be
 * sourced from various file types, including JPEG (APP1 segment), TIFF, WebP, PNG, and DNG.
 *
 * <code>
 * c:\apps\exiftool-13.36_64>exiftool -XMP:All -a -u -g1 pool19.JPG
 * ---- XMP-x ----
 * XMP Toolkit : Image::ExifTool 13.29
 * ---- XMP-rdf ----
 * About : uuid:faf5bdd5-ba3d-11da-ad31-d33d75182f1b
 * ---- XMP-dc ----
 * Creator : Gemma Emily Maggs
 * Description : Trevor
 * Title : Trevor
 * ---- XMP-exif ----
 * Date/Time Original : 2011:10:07 22:59:20
 * ---- XMP-xmp ----
 * Create Date : 2011:10:07 22:59:20
 * Modify Date : 2011:10:07 22:59:20
 * </code>
 *
 * @author Trevor
 * @version 1.8
 * @since 9 November 2025
 */
public class XmpHandler implements ImageHandler, Iterable<XMPCoreProperty>
{
    private static final LogFactory LOGGER = LogFactory.getLogger(XmpHandler.class);
    private final byte[] xmpData;
    private Map<String, XMPCoreProperty> propertyMap;

    /**
     * Represents a single Image File Directory (IFD) entry within a TIFF structure.
     *
     * Each {@code EntryIFD} encapsulates metadata such as tag ID, data type, count, raw bytes, and
     * a parsed object representation. It is immutable and self-contained.
     *
     *
     * @author Trevor Maggs
     * @since 21 June 2025
     */
    public final static class XMPCoreProperty
    {
        private final String namespace;
        private final String path;
        private final String value;

        /**
         * Constructs an immutable {@code EntryIFD} instance from raw bytes.
         *
         * @param namespace
         *        the namespace of the property
         * @param path
         *        the path of the property
         * @param value
         *        the value of the property
         */
        public XMPCoreProperty(String namespace, String path, String value)
        {
            this.namespace = namespace;
            this.path = path;
            this.value = value;
        }

        /**
         * @return the namespace of the property
         */
        public String getNamespace()
        {
            return namespace;
        }

        /**
         * @return the path of the property
         */
        public String getPropertyPath()
        {
            return path;
        }

        /**
         * @return the value of the property
         */
        public String getPropertyValue()
        {
            return value;
        }

        /**
         * @return formatted string describing the entry’s key characteristics
         */
        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();

            sb.append(String.format("  %-20s %s%n", "[Namespace]", getNamespace()));
            sb.append(String.format("  %-20s %s%n", "[Property Path]", getPropertyPath()));
            sb.append(String.format("  %-20s %s%n", "[Property Value]", getPropertyValue()));

            return sb.toString();
        }
    }

    /**
     * Constructs a new XmpHandler from a list of XMP segments.
     *
     * @param inputData
     *        raw XMP segments as a single byte array
     *
     * @throws ImageReadErrorException
     *         if segments are null, empty, or cannot be parsed
     */
    public XmpHandler(byte[] inputData) throws ImageReadErrorException
    {
        if (inputData == null || inputData.length == 0)
        {
            throw new ImageReadErrorException("XMP Data is null or empty");
        }

        this.xmpData = inputData;
        this.propertyMap = new LinkedHashMap<>();
    }

    /**
     * Always return a zero value.
     *
     * @return always zero
     */
    @Override
    public long getSafeFileSize()
    {
        return 0L;
    }

    /**
     * Parses the stored XMP byte array into an XML Document object.
     *
     * @return true when the parsing is successful
     *
     * @throws ImageReadErrorException
     *         if parsing of the XMP data fails
     */
    @Override
    public boolean parseMetadata() throws ImageReadErrorException
    {
        // testDump();
        readPropertyData(this.xmpData);

        return (propertyMap.size() > 0);
    }

    private void readPropertyData(byte[] data)
    {
        try
        {
            XMPMeta xmpMeta = XMPMetaFactory.parseFromBuffer(data);

            if (xmpMeta != null)
            {
                XMPIterator iter = xmpMeta.iterator();

                while (iter.hasNext())
                {
                    Object o = iter.next();

                    if (o instanceof XMPPropertyInfo)
                    {
                        XMPPropertyInfo info = (XMPPropertyInfo) o;

                        String ns = "Namespace: " + info.getNamespace();
                        String path = "Path: " + info.getPath();
                        String value = "Value: " + info.getValue();

                        ns = info.getNamespace();
                        path = info.getPath();
                        value = info.getValue();

                        
                        if (path != null && value != null)
                        {
                            //System.out.printf("%s%n", value);
                            
                            XMPCoreProperty prop = new XMPCoreProperty(ns, path, value);

                            propertyMap.put(path, prop);
                            System.out.printf("%-50s%-40s%-40s%n", ns, path, value);
                            // System.out.printf("%s%n", prop);
                        }
                    }
                }
            }
        }

        catch (XMPException exc)
        {
            exc.printStackTrace();
        }
    }

    /**
     * Utility method to dump all properties using the Adobe XMP SDK (XMPCore library). This is
     * useful for debugging and validation against the DOM/XPath method.
     */
    public void testDump()
    {
        try
        {
            XMPMeta xmpMeta = XMPMetaFactory.parseFromBuffer(xmpData);
            XMPIterator iter = xmpMeta.iterator();

            while (iter.hasNext())
            {
                Object o = iter.next();

                if (o instanceof XMPPropertyInfo)
                {
                    XMPPropertyInfo prop = (XMPPropertyInfo) o;

                    String ns = "Namespace: " + prop.getNamespace();
                    String ph = "Path: " + prop.getPath();
                    String va = "Value: " + prop.getValue();

                    System.out.printf("%-50s%-40s%-40s%n", ns, ph, va);
                }
            }
        }

        catch (XMPException exc)
        {
            exc.printStackTrace();
        }
    }

    @Override
    public Iterator<XMPCoreProperty> iterator()
    {
        return propertyMap.values().iterator();
    }
}