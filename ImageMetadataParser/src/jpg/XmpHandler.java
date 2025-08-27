package jpg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
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
 *
 * @author Trevor Maggs
 * @version 1.5
 * @since 27 August 2025
 */
public class XmpHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(XmpHandler.class);
    private final Optional<byte[]> optSegments;

    /**
     * Constructs a new instance to process a list of un-constructed XMP bytes.
     *
     * @param xmpSegments
     * @throws ImageReadErrorException
     *
     */
    public XmpHandler(List<byte[]> xmpSegments) throws ImageReadErrorException
    {
        if (xmpSegments == null || xmpSegments.isEmpty())
        {
            throw new ImageReadErrorException("XMP Data is null or empty");
        }

        this.optSegments = reconstructXmpSegments(xmpSegments);
    }

    /**
     * Reconstructs a complete XMP metadata block by concatenating multiple raw XMP segments.
     *
     * <p>
     * The Extensible Metadata Platform (XMP) specification allows XMP data to be stored across
     * multiple APP1 segments within a JPEG file. This method reassembles these fragments into a
     * single, cohesive byte array for parsing.
     * </p>
     *
     * @param segments
     *        the list of byte arrays, each representing a raw APP1 segment containing XMP data.
     *
     * @return an Optional containing the concatenated byte array, or returns Optional.empty() if no
     *         segments are available
     */
    private static Optional<byte[]> reconstructXmpSegments(List<byte[]> segments)
    {
        int xmpIdentifierLength = JpgParserAdvanced.XMP_IDENTIFIER.length;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
        {
            for (byte[] seg : segments)
            {
                // Remove the XMP_IDENTIFIER header before writing to the stream
                // to prevent corrupted parsing. Only payload data.
                baos.write(seg, xmpIdentifierLength, seg.length - xmpIdentifierLength);
            }

            return Optional.of(baos.toByteArray());
        }

        catch (IOException exc)
        {
            LOGGER.error("Failed to concatenate XMP segments", exc);
        }

        return Optional.empty();
    }

    private static NamespaceContext getNamespaceContextResource()
    {
        return new NamespaceContext()
        {
            @Override
            public String getNamespaceURI(String prefix)
            {
                if (prefix == null)
                {
                    throw new IllegalArgumentException("Prefix cannot be null");
                }

                switch (prefix)
                {
                    case "dc":
                        return "http://purl.org/dc/elements/1.1/";
                    case "xmp":
                        return "http://ns.adobe.com/xap/1.0/";
                    case "photoshop":
                        return "http://ns.adobe.com/photoshop/1.0/";
                    case "rdf":
                        return "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
                    case "xmpMM":
                        return "http://ns.adobe.com/xap/1.0/mm/";
                    default:
                        return null;
                }
            }

            @Override
            public String getPrefix(String uri)
            {
                if ("http://purl.org/dc/elements/1.1/".equals(uri))
                {
                    return "dc";
                }
                if ("http://ns.adobe.com/xap/1.0/".equals(uri))
                {
                    return "xmp";
                }
                if ("http://ns.adobe.com/photoshop/1.0/".equals(uri))
                {
                    return "photoshop";
                }
                if ("http://ns.adobe.com/xap/1.0/mm/".equals(uri))
                {
                    return "xmpMM";
                }
                if ("http://www.w3.org/1999/02/22-rdf-syntax-ns#".equals(uri))
                {
                    return "rdf";
                }
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String uri)
            {
                // TODO: develop it to iterate the specified URI.
                return null;
            }
        };
    }

    /**
     * Parses an XML stream and returns an Optional containing the Document populated object.
     *
     * @param xmpInputStream
     *        the input stream containing the XMP XML data
     *
     * @return an Optional containing the parsed Document, or Optional.empty() if parsing fails
     */
    public Optional<Document> parseXmp(InputStream xmpInputStream)
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        dbf.setNamespaceAware(true);

        try
        {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(xmpInputStream);

            doc.getDocumentElement().normalize();

            return Optional.of(doc);
        }

        catch (ParserConfigurationException exc)
        {
            LOGGER.error("Parser configuration error detected [" + exc.getMessage() + "]");
        }

        catch (SAXException exc)
        {
            LOGGER.error("XML parsing error detected [" + exc.getMessage() + "]");
        }

        catch (IOException exc)
        {
            LOGGER.error("I/O error during parsing occurred [" + exc.getMessage() + "]");
        }

        return Optional.empty();
    }

    // Change to return as list
    /**
     * Builds a map of properties within the Dublin Core name-space identified in the specified
     * document.
     *
     * @param doc
     *        the parsed XML Document object
     */
    public void displayDublinCore(Document doc)
    {
        NodeList dcElements = doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "*");

        if (dcElements.getLength() > 0)
        {
            System.out.println("--- Dublin Core Metadata ---");

            for (int i = 0; i < dcElements.getLength(); i++)
            {
                Node node = dcElements.item(i);

                if (node.getNodeType() == Node.ELEMENT_NODE)
                {
                    Element element = (Element) node;
                    // System.out.println(" Name: " + element.getLocalName());
                    // System.out.println(" Value: " + element.getTextContent().trim());

                    System.out.printf("%s -> %s\n", element.getTagName(), element.getTextContent().trim());
                }
            }

            System.out.println("----------------------------");
        }

        else
        {
            System.out.println("No Dublin Core metadata found.");
        }
    }

    /**
     * Retrieves the value of a specific XMP property using XPath. It handles name-spaces by mapping
     * URIs to their prefixes.
     *
     * @param doc
     *        the parsed XML Document object
     * @param namespaceUri
     *        the full name-space URI of the property
     * @param localName
     *        the local name of the property
     *
     * @return an Optional containing the property's text content, or Optional.empty() if not
     *         available
     * @see https://howtodoinjava.com/java/xml/java-xpath-tutorial-example
     */
    public Optional<String> getXmpPropertyValue(Document doc, String namespaceUri, String localName)
    {
        try
        {
            XPath xpath = XPathFactory.newInstance().newXPath();
            String xPathExpression = String.format("//*[local-name()='%s' and namespace-uri()='%s']", localName, namespaceUri);

            // The NamespaceContext is essential for XPath to understand prefixes like "dc"
            xpath.setNamespaceContext(getNamespaceContextResource());

            Node node = (Node) xpath.evaluate(xPathExpression, doc, XPathConstants.NODE);

            if (node != null)
            {
                return Optional.ofNullable(node.getTextContent());
            }
        }

        catch (XPathExpressionException exc)
        {
            System.err.println("XPath expression error [" + exc.getMessage() + "]");
        }

        return Optional.empty();
    }
}