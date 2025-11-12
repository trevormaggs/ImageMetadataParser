package xmp;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * 
 * exiftool -XMP:Description="Construction Progress" XMPimage.png
 * </code>
 *
 * @author Trevor
 * @version 1.8
 * @since 9 November 2025
 */
public class XmpHandler implements ImageHandler, Iterable<XMPCoreProperty>
{
    private static final LogFactory LOGGER = LogFactory.getLogger(XmpHandler.class);
    private static final Pattern REGEX_DIGIT = Pattern.compile("\\[\\d+\\]");
    private static final String REGEX_PATH = "^\\s*(\\w+):(.+)$";

    private final Map<String, XMPCoreProperty> propertyMap;

    /**
     * Represents a single, immutable XMP property record.
     *
     * Each {@code XMPCoreProperty} encapsulates the namespace URI, cleaned property path, and the
     * property value. It is immutable and self-contained.
     *
     * @author Trevor Maggs
     * @since 10 November 2025
     */
    public final static class XMPCoreProperty
    {
        private final String namespace;
        private final String path;
        private final String value;
        private final String prefix;
        private final String name;

        /**
         * Constructs an immutable {@code XMPCoreProperty} instance to hold a single record.
         *
         * @param namespace
         *        the namespace URI of the property
         * @param path
         *        the path of the property (e.g., dc:creator)
         * @param value
         *        the value of the property
         */
        public XMPCoreProperty(String namespace, String path, String value)
        {
            this.namespace = namespace;
            this.path = path;
            this.value = value;
            this.prefix = path.matches(REGEX_PATH) ? path.replaceAll(REGEX_PATH, "$1") : path;
            this.name = path.matches(REGEX_PATH) ? path.replaceAll(REGEX_PATH, "$2") : path;
        }

        /**
         * @return the namespace URI of the property
         */
        public String getNamespace()
        {
            return namespace;
        }

        /**
         * @return the path of the property
         */
        public String getPath()
        {
            return path;
        }

        /**
         * @return the short identifier of the path
         */
        public String getPrefix()
        {
            return prefix;
        }

        /**
         * @return the property name of the path
         */
        public String getName()
        {
            return name;
        }

        /**
         * @return the value of the property
         */
        public String getValue()
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
            sb.append(String.format("  %-20s %s%n", "[Path]", getPath()));
            sb.append(String.format("  %-20s %s%n", "[Value]", getValue()));
            sb.append(String.format("  %-20s %s%n", "[Prefix]", getPrefix()));
            sb.append(String.format("  %-20s %s%n", "[Property Name]", getName()));

            return sb.toString();
        }
    }

    /**
     * Constructs a new XmpHandler from a list of XMP segments.
     *
     * @param input
     *        raw XMP segments as a single byte array
     * 
     * @throws ImageReadErrorException
     *         if segments are null, empty, or cannot be parsed
     */
    public XmpHandler(byte[] input) throws ImageReadErrorException
    {
        if (input == null || input.length == 0)
        {
            throw new ImageReadErrorException("XMP Data is null or empty");
        }

        this.propertyMap = new LinkedHashMap<>();

        try
        {
            readPropertyData(input);
        }

        catch (XMPException exc)
        {
            throw new ImageReadErrorException("Failed to parse XMP data: " + exc.getMessage(), exc);
        }
    }

    /**
     * If the parsing of the XMP segment was successful in constructor, it will return true.
     *
     * @return true if one or more XMP properties were successfully extracted, otherwise false
     */
    @Override
    public boolean parseMetadata()
    {
        return (propertyMap.size() > 0);
    }

    /**
     * Parses the raw XMP byte array and populates the property map. Employs the Adobe XMP SDK
     * (XMPCore library) for efficient iteration and property extraction.
     * 
     * The logic handles structural nodes by tracking the last known namespace URI.
     * 
     * @param data
     *        an array of bytes containing raw XMP data
     * 
     * @throws XMPException
     *         if parsing fails
     */
    private void readPropertyData(byte[] data) throws XMPException
    {
        String nsTracker = "";
        XMPMeta xmpMeta = XMPMetaFactory.parseFromBuffer(data);

        if (xmpMeta != null)
        {
            XMPIterator iter = xmpMeta.iterator();

            while (iter.hasNext())
            {
                Object obj = iter.next();

                if (obj instanceof XMPPropertyInfo)
                {
                    XMPPropertyInfo info = (XMPPropertyInfo) obj;

                    String ns = info.getNamespace();
                    String path = info.getPath();
                    String value = info.getValue();

                    if (path == null || value == null || value.isEmpty())
                    {
                        if (ns != null && !ns.isEmpty())
                        {
                            nsTracker = ns;
                        }

                        continue;
                    }

                    String finalNs = nsTracker;

                    if (ns != null && !ns.isEmpty())
                    {
                        finalNs = ns;
                    }

                    Matcher matcher = REGEX_DIGIT.matcher(path);
                    String cleanedPath = matcher.replaceAll("");
                    propertyMap.put(cleanedPath, new XMPCoreProperty(finalNs, cleanedPath, value));
                }
            }
        }

        else
        {
            LOGGER.warn("XMP metadata could not be parsed and XMPMetaFactory returned null.");
        }
    }

    /**
     * Returns an iterator over the extracted XMP properties. The properties are returned in the
     * order they appeared in the original XMP payload.
     * 
     * @return an iterator of {@link XMPCoreProperty} objects
     */
    @Override
    public Iterator<XMPCoreProperty> iterator()
    {
        return propertyMap.values().iterator();
    }
}