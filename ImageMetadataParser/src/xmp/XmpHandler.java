package xmp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPIterator;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.properties.XMPPropertyInfo;
import common.ImageHandler;
import common.ImageReadErrorException;
import logger.LogFactory;

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
 * @since 27 August 2025
 */
public class XmpHandler implements ImageHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(XmpHandler.class);
    private static final NamespaceContext NAMESPACE_CONTEXT = loadNamespaceContext();
    private final byte[] xmpData;
    private Document doc;

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
        if (doc == null)
        {
            //testDump();
            
            doc = parseXmlFromByte(this.xmpData);

            if (doc == null)
            {
                throw new ImageReadErrorException("Failed to parse XMP data");
            }

            // testDump();
        }

        return true;
    }

    /**
     * Retrieves a single XMP property value by its XmpSchema definition.
     *
     * @param localName
     *        the XmpSchema constant defining the property
     * @return the extracted value, or empty string if not found
     */
    public String getXmpPropertyValue(XmpSchema localName)
    {
        if (doc != null && localName != null && localName != XmpSchema.UNKNOWN)
        {
            try
            {
                XPath xpath = XPathFactory.newInstance().newXPath();
                String prefix = localName.getSchemaPrefix();
                String prop = localName.getPropertyName();

                /*
                 * Looks for property as an attribute OR element
                 * nested within an rdf:Description node.
                 */
                String xPathExpression = String.format("//rdf:Description/@%s:%s | //rdf:Description/%s:%s", prefix, prop, prefix, prop);

                xpath.setNamespaceContext(NAMESPACE_CONTEXT);

                Node node = (Node) xpath.evaluate(xPathExpression, doc, XPathConstants.NODE);

                if (node != null)
                {
                    /*
                     * Important note: for attributes, node.getTextContent() is
                     * the attribute value, while for elements, node.getTextContent()
                     * is the element's text.
                     */
                    return node.getTextContent().trim();
                }
            }

            catch (XPathExpressionException exc)
            {
                LOGGER.error("XPath expression error [" + exc.getMessage() + "]", exc);
            }
        }

        return "";
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

    /**
     * Creates a custom {@code NamespaceContext} for use with XPath, mapping XMP prefixes (from
     * {@code NameSpace} enum) and the essential RDF prefix to their full URIs. This allows XPath
     * expressions to use standard prefixes, for example: {@code rdf:Description}.
     *
     * @return a populated NamespaceContext instance
     */
    private static NamespaceContext loadNamespaceContext()
    {
        final String RDF_PREFIX = "rdf";
        final String RDF_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

        NamespaceContext ns = new NamespaceContext()
        {
            @Override
            public String getNamespaceURI(String prefix)
            {
                if (prefix == null)
                {
                    throw new IllegalArgumentException("Prefix cannot be null");
                }

                if (prefix.equals(RDF_PREFIX))
                {
                    return RDF_URI;
                }

                NameSpace ns = NameSpace.fromNamespacePrefix(prefix);

                return (ns == NameSpace.UNKNOWN ? XMLConstants.NULL_NS_URI : ns.getURI());
            }

            @Override
            public String getPrefix(String namespaceURI)
            {
                if (namespaceURI == null)
                {
                    throw new IllegalArgumentException("NamespaceURI cannot be null");
                }

                if (namespaceURI.equals(RDF_URI))
                {
                    return RDF_PREFIX;
                }

                NameSpace ns = NameSpace.fromNamespaceURI(namespaceURI);

                return (ns == NameSpace.UNKNOWN ? null : ns.getPrefix());
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI)
            {
                String prefix = getPrefix(namespaceURI);

                if (prefix == null)
                {
                    return Collections.<String> emptyList().iterator();
                }

                return Collections.singletonList(prefix).iterator();
            }
        };

        return ns;
    }

    /**
     * Parses the XMP byte array into an XML Document object.
     *
     * @param payload
     *        byte array containing XMP structured data
     * @return the parsed Document, or null if parsing fails
     */
    private static Document parseXmlFromByte(byte[] payload)
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);
        factory.setIgnoringComments(true);
        factory.setExpandEntityReferences(false);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(payload))
        {
            Document doc;
            DocumentBuilder builder = factory.newDocumentBuilder();

            builder.setErrorHandler(null);
            doc = builder.parse(bais);
            doc.getDocumentElement().normalize();

            return doc;
        }

        catch (ParserConfigurationException | SAXException | IOException exc)
        {
            LOGGER.error("Failed to parse XMP XML [" + exc.getMessage() + "]", exc);
        }

        return null;
    }

    public Map<String, String> readPropertyData()
    {
        Map<String, String> propertyValueByPath = new LinkedHashMap<>();

        try
        {
            XMPMeta xmpMeta = XMPMetaFactory.parseFromBuffer(xmpData);

            if (xmpMeta != null)
            {
                XMPIterator iter = xmpMeta.iterator();

                while (iter.hasNext())
                {
                    Object o = iter.next();

                    if (o instanceof XMPPropertyInfo)
                    {
                        XMPPropertyInfo prop = (XMPPropertyInfo) o;

                        String path = prop.getPath();
                        String value = prop.getValue();

                        if (path != null && value != null)
                        {
                            propertyValueByPath.put(path, value);
                        }
                    }
                }
            }
        }

        catch (XMPException exc)
        {
            exc.printStackTrace();
        }

        return propertyValueByPath;
    }
}