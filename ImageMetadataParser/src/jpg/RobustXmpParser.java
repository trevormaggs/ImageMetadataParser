package jpg;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.namespace.NamespaceContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;

/**
 * A robust class for parsing XMP metadata from an InputStream.
 * It uses the Document Object Model (DOM) and XPath for navigation.
 */
public class RobustXmpParser
{
    /**
     * Parses an XML stream and returns an Optional containing the Document object.
     * Handles various parsing and I/O exceptions gracefully.
     *
     * @param xmpInputStream
     *        The input stream containing the XMP XML data.
     * @return An Optional containing the parsed Document, or Optional.empty() if parsing fails.
     */
    public Optional<Document> parseXmp(InputStream xmpInputStream)
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        // This is crucial for handling XMP namespaces correctly
        dbf.setNamespaceAware(true);

        try
        {
            // Create a DocumentBuilder and parse the stream
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(xmpInputStream);

            // Normalize the document to clean up its structure (e.g., merge text nodes)
            doc.getDocumentElement().normalize();

            return Optional.of(doc);
        }

        catch (ParserConfigurationException pce)
        {
            // Internal error with the XML parser setup
            System.err.println("Parser configuration error: " + pce.getMessage());
        }

        catch (SAXException se)
        {
            // The XML is not well-formed
            System.err.println("XML parsing error: " + se.getMessage());
        }

        catch (IOException ioe)
        {
            // An error occurred while reading the stream
            System.err.println("I/O error during parsing: " + ioe.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Retrieves the value of a specific XMP property using XPath.
     * It correctly handles namespaces by mapping URIs to their prefixes.
     *
     * @param doc
     *        The parsed XML Document object.
     * @param namespaceUri
     *        The full namespace URI of the property (e.g., http://purl.org/dc/elements/1.1/).
     * @param localName
     *        The local name of the property (e.g., creator).
     * @return An Optional containing the property's text content, or Optional.empty() if not found.
     */
    public Optional<String> getXmpPropertyValue(Document doc, String namespaceUri, String localName)
    {
        try
        {
            XPath xpath = XPathFactory.newInstance().newXPath();

            // Create a custom NamespaceContext to map URIs to prefixes for XPath evaluation.
            // This is required to correctly evaluate namespaced elements.
            NamespaceContext nsContext = new NamespaceContext()
            {
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

                // This method is required to map a URI back to a prefix for XPath.
                public String getPrefix(String uri)
                {
                    if ("http://purl.org/dc/elements/1.1/".equals(uri)) return "dc";

                    if ("http://ns.adobe.com/xap/1.0/".equals(uri)) return "xmp";

                    if ("http://ns.adobe.com/photoshop/1.0/".equals(uri)) return "photoshop";

                    if ("http://ns.adobe.com/xap/1.0/mm/".equals(uri)) return "xmpMM";

                    if ("http://www.w3.org/1999/02/22-rdf-syntax-ns#".equals(uri)) return "rdf";

                    return null;
                }

                // Required but not used in this example.
                public Iterator<?> getPrefixes(String uri)
                {
                    return null;
                }
            };

            xpath.setNamespaceContext(nsContext);

            // Correctly get the prefix from the namespace URI before using it.
            String prefix = nsContext.getPrefix(namespaceUri);
            if (prefix == null)
            {
                System.err.println("Unknown namespace URI: " + namespaceUri);
                return Optional.empty();
            }

            // Construct the XPath expression dynamically using the prefix and local name
            Node node = (Node) xpath.evaluate("//rdf:Description[@rdf:about]//*/" + prefix + ":" + localName, doc, XPathConstants.NODE);

            if (node != null)
            {
                // Return the text content of the found node
                return Optional.ofNullable(node.getTextContent());
            }

        }
        catch (XPathExpressionException e)
        {
            // The XPath expression is not valid
            System.err.println("XPath expression error: " + e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Displays all properties within the Dublin Core namespace found in the document.
     *
     * @param doc
     *        The parsed XML Document object.
     */
    public void displayDublinCore(Document doc)
    {
        // Get all elements within the Dublin Core namespace
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
                    System.out.println("  Name: " + element.getLocalName());
                    System.out.println("  Value: " + element.getTextContent().trim());
                }
            }
            
            System.out.println("----------------------------");
        }

        else
        {
            System.out.println("No Dublin Core metadata found.");
        }
    }
}