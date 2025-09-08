package xmp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import common.ImageReadErrorException;
import logger.LogFactory;

/**
 * Handles XMP metadata extraction from JPEG APP1 segments.
 *
 * @author Trevor
 * @version 1.8
 * @since 27 August 2025
 */
public class XmpHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(XmpHandler.class);
    private final Document doc;

    // Static map of supported XMP namespaces
    private static final Map<String, String> NAMESPACES;

    static
    {
        Map<String, String> ns = new HashMap<>();

        ns.put("dc", "http://purl.org/dc/elements/1.1/");
        ns.put("xmp", "http://ns.adobe.com/xap/1.0/");
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

            return NAMESPACES.get(prefix);
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
                return Collections.emptyIterator();
            }

            List<String> single = new ArrayList<>();

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
     *         if segments are null, empty, or cannot be reconstructed
     */
    public XmpHandler(byte[] xmpData) throws ImageReadErrorException
    {
        if (xmpData == null || xmpData.length == 0)
        {
            throw new ImageReadErrorException("XMP Data is null or empty");
        }
        this.doc = parseXmpDocument(xmpData);
    }

    /**
     * Returns the completed Document object wrapped in an Optional resource, containing the XML
     * data.
     */
    public Optional<Document> getXmlDocument()
    {
        return Optional.ofNullable(doc);
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
            return null;
        }
    }

    /**
     * Builds a map of properties within the Dublin Core namespace identified in the specified
     * document.
     *
     * @return a populated map, or empty if no document
     */
    public Map<String, String> getDublinCoreProperties()
    {
        if (doc == null)
        {
            return Collections.emptyMap();
        }

        Map<String, String> properties = new HashMap<>();
        NodeList nodes = doc.getElementsByTagNameNS(NAMESPACES.get("dc"), "*");

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

    public Map<String, String> getCoreXMPProperties()
    {
        if (doc == null)
        {
            return Collections.emptyMap();
        }

        Map<String, String> properties = new HashMap<>();
        NodeList nodes = doc.getElementsByTagNameNS(NAMESPACES.get("xmp"), "*");

        for (int i = 0; i < nodes.getLength(); i++)
        {
            Node node = nodes.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE)
            {
                Element element = (Element) node;
                properties.put(element.getLocalName(), element.getTextContent().trim());

                System.out.printf("LOOK: %s%n", element.getTextContent());
            }
        }

        return properties;
    }

    /**
     * Retrieves a single XMP property value by namespace URI and local name.
     *
     * @param namespaceUri
     *        the full namespace URI of the property
     * @param localName
     *        the local name of the property
     *
     * @return the extracted value, or empty if not found
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
}