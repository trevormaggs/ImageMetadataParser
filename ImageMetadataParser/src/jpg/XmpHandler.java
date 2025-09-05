package jpg;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
 * @version 1.7
 * @since 27 August 2025
 */
public class XmpHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(XmpHandler.class);
    private Document doc;

    private static final NamespaceContext NAMESPACE_CONTEXT = new NamespaceContext()
    {
        @Override
        public String getNamespaceURI(String prefix)
        {
            if (prefix == null)
            {
                throw new IllegalArgumentException("Prefix cannot be null");
            }

            if ("dc".equals(prefix))
            {
                return "http://purl.org/dc/elements/1.1/";
            }
            if ("xmp".equals(prefix))
            {
                return "http://ns.adobe.com/xap/1.0/";
            }
            if ("photoshop".equals(prefix))
            {
                return "http://ns.adobe.com/photoshop/1.0/";
            }
            if ("rdf".equals(prefix))
            {
                return "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
            }
            if ("xmpMM".equals(prefix))
            {
                return "http://ns.adobe.com/xap/1.0/mm/";
            }

            return null;
        }

        @Override
        public String getPrefix(String namespaceURI)
        {
            if ("http://purl.org/dc/elements/1.1/".equals(namespaceURI))
            {
                return "dc";
            }
            if ("http://ns.adobe.com/xap/1.0/".equals(namespaceURI))
            {
                return "xmp";
            }
            if ("http://ns.adobe.com/photoshop/1.0/".equals(namespaceURI))
            {
                return "photoshop";
            }
            if ("http://www.w3.org/1999/02/22-rdf-syntax-ns#".equals(namespaceURI))
            {
                return "rdf";
            }
            if ("http://ns.adobe.com/xap/1.0/mm/".equals(namespaceURI))
            {
                return "xmpMM";
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

            else
            {
                return Collections.singleton(prefix).iterator();
            }
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

        doc = null;
        parseXmpDocument(xmpData);
    }

    /**
     * Returns the completed Document object wrapped in an Optional resource, containing the XML
     * data.
     */
    public Optional<Document> getXmlDocument()
    {
        return (doc == null ? Optional.empty() : Optional.of(doc));
    }

    /**
     * Parses the XMP byte array into an XML Document object.
     *
     * @return an Optional containing the parsed Document, or Optional.empty() if parsing fails
     */
    private void parseXmpDocument(byte[] xmpBytes)
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(xmpBytes))
        {
            DocumentBuilder db = dbf.newDocumentBuilder();

            doc = db.parse(bais);
            doc.getDocumentElement().normalize();
        }

        catch (ParserConfigurationException | SAXException | IOException exc)
        {
            LOGGER.error("Failed to parse XMP XML [" + exc.getMessage() + "]", exc);
        }
    }

    /**
     * Builds a map of properties within the Dublin Core namespace identified in the specified
     * document.
     *
     * @param doc
     *        the parsed XML Document object
     * @return a populated map
     */
    public Map<String, String> getDublinCoreProperties(Document doc)
    {
        Map<String, String> properties = new HashMap<>();
        NodeList nodes = doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "*");

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

    /**
     * Retrieves a single XMP property value by namespace URI and local name.
     *
     * @param doc
     *        the parsed XML Document object
     * @param namespaceUri
     *        the full name-space URI of the property
     * @param localName
     *        the local name of the property
     *
     * @return the extracted value of the specified local name
     * @see https://howtodoinjava.com/java/xml/java-xpath-tutorial-example
     */
    public String getXmpPropertyValue(Document doc, String namespaceUri, String localName)
    {
        try
        {
            XPath xpath = XPathFactory.newInstance().newXPath();

            xpath.setNamespaceContext(NAMESPACE_CONTEXT);

            String xPathExpression = String.format("//*[local-name()='%s' and namespace-uri()='%s'] | //@*[local-name()='%s' and namespace-uri()='%s']", localName, namespaceUri, localName, namespaceUri);

            Node node = (Node) xpath.evaluate(xPathExpression, doc, XPathConstants.NODE);

            if (node != null)
            {
                return node.getTextContent();
            }
        }

        catch (XPathExpressionException exc)
        {
            LOGGER.error("XPath expression error [" + exc.getMessage() + "]", exc);
        }

        return null;
    }
}