package xmp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import com.adobe.internal.xmp.impl.ByteBuffer;
import common.ImageHandler;
import common.ImageReadErrorException;
import logger.LogFactory;

/**
 * Handles XMP metadata extraction from JPEG APP1 segments.
 *
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
 * @author Trevor
 * @version 1.8
 * @since 27 August 2025
 */
public class XmpHandler3 implements ImageHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(XmpHandler.class);
    private static final NamespaceContext NAMESPACE_CONTEXT = loadNamespaceContext();
    private static final Map<String, String> NAMESPACES;
    private final Document doc;

    static
    {
        Map<String, String> ns = new HashMap<>();

        ns.put("dc", "http://purl.org/dc/elements/1.1/");
        ns.put("xap", "http://ns.adobe.com/xap/1.0/");
        ns.put("xmp", "http://ns.adobe.com/xap/1.0/"); // alias
        ns.put("photoshop", "http://ns.adobe.com/photoshop/1.0/");
        ns.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        ns.put("xmpMM", "http://ns.adobe.com/xap/1.0/mm/");

        NAMESPACES = Collections.unmodifiableMap(ns);
    }

    /**
     * Constructs a new XmpHandler from a list of XMP segments.
     *
     * @param xmpData
     *        raw XMP segments as a single byte array
     * @throws ImageReadErrorException
     *         if segments are null, empty, or cannot be parsed
     */
    public XmpHandler3(byte[] xmpData) throws ImageReadErrorException
    {
        if (xmpData == null || xmpData.length == 0)
        {
            throw new ImageReadErrorException("XMP Data is null or empty");
        }

        this.doc = parseXmlFromByte(xmpData);

        if (doc == null)
        {
            throw new ImageReadErrorException("Failed to parse XMP data");
        }
    }

    @Override
    public long getSafeFileSize()
    {
        throw new UnsupportedOperationException("Not applicable for XMP handler");
    }

    @Override
    public boolean parseMetadata()
    {
        if (doc == null)
        {
            return false;
        }

        parseMetadata2();

        System.out.printf("LOOK0: %s\n", test(XmpSchema.DC_CREATOR));
        System.out.printf("LOOK1: %s\n", test(XmpSchema.XAP_METADATADATE));
        System.out.printf("LOOK2: %s\n", test(XmpSchema.DC_TITLE));

        return true;
    }

    public String test(XmpSchema localName)
    {
        if (doc != null && localName != null && localName != XmpSchema.UNKNOWN)
        {
            try
            {
                XPath xpath = XPathFactory.newInstance().newXPath();
                String ns = localName.getNamespaceURI();
                String prop = localName.getPropertyName();
                String xPathExpression = String.format("//*[local-name()='%s' and namespace-uri()='%s'] | //@*[local-name()='%s' and namespace-uri()='%s']", prop, ns, prop, ns);

                xpath.setNamespaceContext(NAMESPACE_CONTEXT);

                Node node = (Node) xpath.evaluate(xPathExpression, doc, XPathConstants.NODE);

                if (node != null)
                {
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
     * Retrieves a single XMP property value by namespace URI and local name.
     *
     * @param namespaceUri
     *        the full namespace URI of the property
     * @param localName
     *        the local name of the property
     * @return the extracted value, or empty string if not found
     */
    public String getXmpPropertyValue(String namespaceUri, String localName)
    {
        if (doc == null)
        {
            return "";
        }

        try
        {
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(NAMESPACE_CONTEXT);

            String xPathExpression = String.format("//*[local-name()='%s' and namespace-uri()='%s'] | //@*[local-name()='%s' and namespace-uri()='%s']", localName, namespaceUri, localName, namespaceUri);
            // xPathExpression = "//@*";

            Node node = (Node) xpath.evaluate(xPathExpression, doc, XPathConstants.NODE);

            if (node != null)
            {
                return node.getTextContent().trim();
            }
        }

        catch (XPathExpressionException exc)
        {
            LOGGER.error("XPath expression error [" + exc.getMessage() + "]", exc);
        }

        return "";
    }

    public boolean parseMetadata2()
    {
        // Dublin Core properties
        LOGGER.info("DublinCore: creator = " + getXmpPropertyValue("http://purl.org/dc/elements/1.1/", "creator"));
        LOGGER.info("DublinCore: rights = " + getXmpPropertyValue("http://purl.org/dc/elements/1.1/", "rights"));
        LOGGER.info("DublinCore: description = " + getXmpPropertyValue("http://purl.org/dc/elements/1.1/", "description"));
        LOGGER.info("DublinCore: title = " + getXmpPropertyValue("http://purl.org/dc/elements/1.1/", "title"));

        // Core XMP properties
        LOGGER.info("XMP Core: Create Date = " + getXmpPropertyValue("http://ns.adobe.com/xap/1.0/", "CreateDate"));
        LOGGER.info("XMP Core: Modify Date = " + getXmpPropertyValue("http://ns.adobe.com/xap/1.0/", "ModifyDate"));
        LOGGER.info("XMP Core: Creator Tool = " + getXmpPropertyValue("http://ns.adobe.com/xap/1.0/", "CreatorTool"));
        LOGGER.info("XMP Core: Metadata Date = " + getXmpPropertyValue("http://ns.adobe.com/xap/1.0/", "MetadataDate"));

        // Other properties you may want to add
        LOGGER.info("XMP MediaManagement: Instance ID = " + getXmpPropertyValue("http://ns.adobe.com/xap/1.0/mm/", "InstanceID"));
        LOGGER.info("XMP Photoshop: Color Mode = " + getXmpPropertyValue("http://ns.adobe.com/photoshop/1.0/", "ColorMode"));

        return true;
    }

    /**
     * Parses the XMP byte array into an XML Document object.
     *
     * @return the parsed Document, or null if parsing fails
     */
    private Document parseXmlFromByte(byte[] input)
    {
        Document doc = null;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);
        factory.setIgnoringComments(true);
        factory.setExpandEntityReferences(false);

        try
        {
            DocumentBuilder builder = null;
            ByteBuffer buf = new ByteBuffer(input);

            builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);

            doc = builder.parse(new InputSource(buf.getByteStream()));
            doc.getDocumentElement().normalize();
        }

        catch (ParserConfigurationException | SAXException | IOException exc)
        {
            LOGGER.error("Failed to parse XMP XML [" + exc.getMessage() + "]", exc);
        }

        return doc;
    }

    private static NamespaceContext loadNamespaceContext()
    {
        NamespaceContext ns = new NamespaceContext()
        {
            @Override
            public String getNamespaceURI(String prefix)
            {
                if (prefix == null)
                {
                    throw new IllegalArgumentException("Prefix cannot be null");
                }

                String uri = NAMESPACES.get(prefix);

                // System.out.printf("uri: %s\n", uri);
                // System.out.printf("uri2: %s\n", uri2);

                return uri != null ? uri : XMLConstants.NULL_NS_URI;
            }

            @Override
            public String getPrefix(String namespaceURI)
            {
                for (Map.Entry<String, String> entry : NAMESPACES.entrySet())
                {
                    if (entry.getValue().equals(namespaceURI))
                    {
                        return entry.getKey();
                    }
                }

                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI)
            {
                String prefix = getPrefix(namespaceURI);

                if (prefix == null)
                {
                    return Collections.<String> emptyList().iterator();
                }

                List<String> single = new ArrayList<>();

                single.add(prefix);

                return single.iterator();
            }
        };

        return ns;
    }
}