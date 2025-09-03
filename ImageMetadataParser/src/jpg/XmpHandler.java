package jpg;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * Java 8 compatible, no lambdas.
 *
 * @author Trevor
 * @version 1.6
 * @since 27 August 2025
 */
public class XmpHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(XmpHandler.class);
    private final byte[] xmpBytes;
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
     * @param xmpSegments
     *        list of raw XMP APP1 segments
     * @throws ImageReadErrorException
     *         if segments are null, empty, or cannot be reconstructed
     */
    public XmpHandler(byte[] xmpData) throws ImageReadErrorException
    {
        if (xmpData == null || xmpData.length == 0)
        {
            throw new ImageReadErrorException("XMP Data is null or empty");
        }

        xmpBytes = xmpData;
    }

    /**
     * Returns the raw reconstructed XMP bytes.
     */
    public byte[] getXmpBytes()
    {
        return xmpBytes.clone();
    }

    /**
     * Parses an InputStream containing XMP XML into a Document.
     *
     * @param xmpInputStream
     *        the input stream containing the XMP XML data
     *
     * @return a parsed Document, or null if parsing fails
     */
    public Document parseXmp(InputStream xmpInputStream)
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        dbf.setNamespaceAware(true);

        try
        {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(xmpInputStream);

            doc.getDocumentElement().normalize();

            return doc;
        }

        catch (ParserConfigurationException | SAXException | IOException exc)
        {
            LOGGER.error("Failed to parse XMP XML [" + exc.getMessage() + "]", exc);
        }

        return null;
    }

    public Optional<Document> parseXmp2()
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        dbf.setNamespaceAware(true);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(xmpBytes))
        {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(bais);

            doc.getDocumentElement().normalize();

            return Optional.of(doc);
        }

        catch (ParserConfigurationException exc)
        {
            System.err.println("Parser configuration error [" + exc.getMessage() + "]");
        }

        catch (SAXException exc)
        {
            System.err.println("XML parsing error [" + exc.getMessage() + "]");
        }

        catch (IOException exc)
        {
            System.err.println("I/O error during parsing [" + exc.getMessage() + "]");
        }

        return Optional.empty();
    }

    /*
     * Dublin Core Metadata Attributes
     *
     * Dublin Core is a set of 15 metadata elements used to describe a wide range of resources. It's
     * often used within XMP (Extensible Metadata Platform) to provide a simple, yet effective, way
     * to categorize and describe digital assets like images. These elements are designed to be
     * easily understood and are universally applicable.
     *
     * Here are the 15 standard elements of the Dublin Core Metadata Element Set:
     *
     * dc:title: The name given to the resource.
     * dc:creator: An entity primarily responsible for creating the content of the resource.
     * dc:subject: The topic of the resource. This is often expressed as keywords, key phrases, or
     * classification codes.
     * dc:description: An abstract or summary of the content.
     * dc:publisher: An entity responsible for making the resource available.
     * dc:contributor: An entity responsible for making contributions to the content of the
     * resource.
     * dc:date: A point or period of time associated with an event in the lifecycle of the resource.
     * dc:type: The nature or genre of the content of the resource (e.g., image, text, video).
     * dc:format: The file format, physical medium, or dimensions of the resource.
     * dc:identifier: An unambiguous reference to the resource within a given context.
     * dc:source: A reference to a resource from which the present resource is derived.
     * dc:language: The language of the intellectual content of the resource.
     * dc:relation: A reference to a related resource.
     * dc:coverage: The spatial or temporal topic of the resource, the spatial applicability of the
     * resource, or the jurisdiction under which the resource is relevant.
     * dc:rights: Information about rights held in and over the resource.
     *
     * These elements are often prefixed with dc: to indicate that they belong to the Dublin Core
     * namespace when included in an XMP packet. This allows for clear, semantic metadata that is
     * machine-readable and easy for applications to interpret.
     */

    /**
     * Builds a map of properties within the Dublin Core namespace identified in the specified
     * document.
     *
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
                properties.put(element.getTagName(), element.getTextContent().trim());
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
    public Optional<String> getXmpPropertyValue(Document doc, String namespaceUri, String localName)
    {
        try
        {
            XPath xpath = XPathFactory.newInstance().newXPath();

            xpath.setNamespaceContext(NAMESPACE_CONTEXT);

            String xPathExpression = String.format("//*[local-name()='%s' and namespace-uri()='%s'] | //@*[local-name()='%s' and namespace-uri()='%s']", localName, namespaceUri, localName, namespaceUri);

            Node node = (Node) xpath.evaluate(xPathExpression, doc, XPathConstants.NODE);

            if (node != null)
            {
                return Optional.ofNullable(node.getTextContent());
            }
        }

        catch (XPathExpressionException e)
        {
            System.err.println("XPath expression error: " + e.getMessage());
        }

        return Optional.empty();
    }
}