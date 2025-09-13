package xmp;

import java.io.ByteArrayInputStream;
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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import common.ImageHandler;
import common.ImageReadErrorException;
import logger.LogFactory;

/**
 * Handles XMP metadata extraction from JPEG APP1 segments.
 *
 * @author Trevor
 * @version 1.8
 * @since 27 August 2025
 */
public class XmpHandler1 implements ImageHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(XmpHandler.class);
    private final Document doc;

    /** Static map of supported XMP namespaces */
    private static final Map<String, String> NAMESPACES;

    static
    {
        Map<String, String> ns = new HashMap<String, String>();

        ns.put("dc", "http://purl.org/dc/elements/1.1/");
        ns.put("xap", "http://ns.adobe.com/xap/1.0/");
        ns.put("xmp", "http://ns.adobe.com/xap/1.0/"); // alias
        ns.put("photoshop", "http://ns.adobe.com/photoshop/1.0/");
        ns.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        ns.put("xmpMM", "http://ns.adobe.com/xap/1.0/mm/");

        NAMESPACES = Collections.unmodifiableMap(ns);
    }

    private static final NamespaceContext NAMESPACE_CONTEXT = new NamespaceContext()
    {
        @Override
        public String getNamespaceURI(String prefix)
        {
            if (prefix == null)
            {
                throw new IllegalArgumentException("Prefix cannot be null");
            }

            String uri = NAMESPACES.get(prefix);

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

            List<String> single = new ArrayList<String>();

            single.add(prefix);

            return single.iterator();
        }
    };

    /**
     * Constructs a new XmpHandler from a list of XMP segments.
     *
     * @param xmpData
     *        raw XMP segments as a single byte array
     * @throws ImageReadErrorException
     *         if segments are null, empty, or cannot be parsed
     */
    public XmpHandler1(byte[] xmpData) throws ImageReadErrorException
    {
        if (xmpData == null || xmpData.length == 0)
        {
            throw new ImageReadErrorException("XMP Data is null or empty");
        }

        Document parsed = parseXmpDocument(xmpData);

        if (parsed == null)
        {
            throw new ImageReadErrorException("Failed to parse XMP data");
        }

        this.doc = parsed;
    }

    /**
     * Parses the XMP byte array into an XML Document object.
     *
     * @return the parsed Document, or null if parsing fails
     */
    private Document parseXmpDocument(byte[] xmpBytes)
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(xmpBytes))
        {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document parsed = db.parse(bais);

            parsed.getDocumentElement().normalize();

            return parsed;
        }

        catch (ParserConfigurationException | SAXException | IOException exc)
        {
            LOGGER.error("Failed to parse XMP XML [" + exc.getMessage() + "]", exc);
        }

        return null;
    }

    /** Helper method for extracting properties by namespace prefix. */
    private Map<String, String> getPropertiesByNamespace(String prefix)
    {
        if (doc == null)
        {
            return Collections.emptyMap();
        }

        String ns = NAMESPACES.get(prefix);

        if (ns == null)
        {
            return Collections.emptyMap();
        }

        Map<String, String> properties = new HashMap<String, String>();
        NodeList nodes = doc.getElementsByTagNameNS(ns, "*");

        for (int i = 0; i < nodes.getLength(); i++)
        {
            Node node = nodes.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE)
            {
                Element element = (Element) node;
                properties.put(element.getLocalName(), element.getTextContent().trim());
            }
        }

        return properties;
    }

    /** Returns all Dublin Core properties. */
    public Map<String, String> getDublinCoreProperties()
    {
        return getPropertiesByNamespace("dc");
    }

    /** Returns all core XMP properties. */
    public Map<String, String> getCoreXMPProperties()
    {
        // return getPropertiesByNamespace("xmp");
        return getPropertiesByNamespace("xap"); // or "xmp", both resolve
    }

    /** Returns all XMP Media Management properties. */
    public Map<String, String> getXMPMediaManagementProperties()
    {
        return getPropertiesByNamespace("xmpMM");
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

    @Override
    public long getSafeFileSize()
    {
        throw new UnsupportedOperationException("Not applicable for XMP handler");
    }

    /**
     * Parses metadata and logs extracted properties.
     * Returns true if metadata is present.
     */
    @Override
    public boolean parseMetadata()
    {
        if (doc == null)
        {
            return false;
        }

        Map<String, String> dcMap = getDublinCoreProperties();
        for (Map.Entry<String, String> entry : dcMap.entrySet())
        {
            LOGGER.info("DublinCore: " + entry.getKey() + " = " + entry.getValue());
        }

        Map<String, String> xmpMap = getCoreXMPProperties();
        for (Map.Entry<String, String> entry : xmpMap.entrySet())
        {
            LOGGER.info("XMP Core: " + entry.getKey() + " = " + entry.getValue());
        }

        Map<String, String> mmMap = getXMPMediaManagementProperties();
        for (Map.Entry<String, String> entry : mmMap.entrySet())
        {
            LOGGER.info("XMP MediaManagement: " + entry.getKey() + " = " + entry.getValue());
        }

        return true;
    }
}
