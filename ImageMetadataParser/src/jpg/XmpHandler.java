package jpg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

            if ("dc".equals(prefix)) return "http://purl.org/dc/elements/1.1/";
            if ("xmp".equals(prefix)) return "http://ns.adobe.com/xap/1.0/";
            if ("photoshop".equals(prefix)) return "http://ns.adobe.com/photoshop/1.0/";
            if ("rdf".equals(prefix)) return "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
            if ("xmpMM".equals(prefix)) return "http://ns.adobe.com/xap/1.0/mm/";

            return null;
        }

        @Override
        public String getPrefix(String namespaceURI)
        {
            if ("http://purl.org/dc/elements/1.1/".equals(namespaceURI)) return "dc";
            if ("http://ns.adobe.com/xap/1.0/".equals(namespaceURI)) return "xmp";
            if ("http://ns.adobe.com/photoshop/1.0/".equals(namespaceURI)) return "photoshop";
            if ("http://www.w3.org/1999/02/22-rdf-syntax-ns#".equals(namespaceURI)) return "rdf";
            if ("http://ns.adobe.com/xap/1.0/mm/".equals(namespaceURI)) return "xmpMM";

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
    public XmpHandler(List<byte[]> xmpSegments) throws ImageReadErrorException
    {
        if (xmpSegments == null || xmpSegments.isEmpty())
        {
            throw new ImageReadErrorException("XMP Data is null or empty");
        }

        this.xmpBytes = reconstructXmpSegments(xmpSegments);

        if (this.xmpBytes == null)
        {
            throw new ImageReadErrorException("Failed to reconstruct XMP segments");
        }
    }

    /**
     * Returns the raw reconstructed XMP bytes.
     */
    public byte[] getXmpBytes()
    {
        return xmpBytes.clone();
    }

    /**
     * Reconstructs a single XMP byte array by concatenating multiple APP1 segments.
     * 
     * <p>
     * The Extensible Metadata Platform (XMP) specification allows XMP data to be stored across
     * multiple APP1 segments within a JPEG file. This method reassembles these fragments into a
     * single, cohesive byte array for parsing.
     * </p>
     * 
     * @param segments
     *        the list of byte arrays, each representing a raw APP1 segment containing XMP data
     * 
     * @return a populated byte array, or null if no segments are available
     */
    private static byte[] reconstructXmpSegments(List<byte[]> segments)
    {
        final int xmpIdentifierLength = JpgParserAdvanced.XMP_IDENTIFIER.length;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
        {
            for (byte[] seg : segments)
            {
                if (seg.length < xmpIdentifierLength)
                {
                    throw new IndexOutOfBoundsException("Segment length too short, data may be corrupted");
                }

                // Remove the XMP_IDENTIFIER header before writing to the stream
                // to prevent corrupted parsing. Only payload data.
                baos.write(seg, xmpIdentifierLength, seg.length - xmpIdentifierLength);
            }

            return baos.toByteArray();
        }

        catch (IOException exc)
        {
            LOGGER.error("Failed to concatenate XMP segments", exc);
        }

        return null;
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

    /**
     * Builds a map of properties within the Dublin Core namespace identified in the specified
     * document.
     * 
     * @return a populated map
     */
    public Map<String, String> getDublinCoreProperties(Document doc)
    {
        Map<String, String> properties = new HashMap<String, String>();
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
    public String getXmpPropertyValue(Document doc, String namespaceUri, String localName)
    {
        try
        {
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(NAMESPACE_CONTEXT);

            String expression = String.format("//*[local-name()='%s' and namespace-uri()='%s']", localName, namespaceUri);                        
            Node node = (Node) xpath.evaluate(expression, doc, XPathConstants.NODE);

            if (node != null && node.getTextContent() != null)
            {
                return node.getTextContent().trim();
            }
        }
        
        catch (XPathExpressionException exc)
        {
            LOGGER.error("XPath evaluation failed", exc);
        }
        
        return null;
    }
}