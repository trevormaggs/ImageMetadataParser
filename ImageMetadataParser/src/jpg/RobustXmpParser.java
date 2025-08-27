package jpg;

// This version is updated to read JPG files and print all dc data without using lambda expressions.

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * A robust class for parsing XMP metadata from an InputStream.
 * It is updated to read JPG files and print all dc data without using lambda expressions.
 */
public class RobustXmpParser
{

    private static final byte[] XMP_IDENTIFIER = "http://ns.adobe.com/xap/1.0/\0".getBytes();
    private static final int SEGMENT_MARKER = 0xFF;
    private static final int APP1_MARKER = 0xE1;

    /**
     * Reads and consolidates all XMP segments from a JPEG file.
     *
     * @param jpgPath
     *        The path to the JPEG file.
     * @return An Optional containing the consolidated XMP data, or empty if none is found.
     */
    public Optional<byte[]> readXmpSegments(Path jpgPath)
    {
        List<byte[]> xmpSegments = new ArrayList<byte[]>();

        try (InputStream is = Files.newInputStream(jpgPath))
        {
            while (true)
            {
                int marker = is.read();

                if (marker == -1)
                {
                    break; // End of file
                }

                if (marker == SEGMENT_MARKER)
                {
                    int segmentType = is.read();

                    if (segmentType == APP1_MARKER)
                    {
                        int length = ((is.read() & 0xFF) << 8) | (is.read() & 0xFF);
                        byte[] payload = new byte[length - 2];
                        is.read(payload);

                        // Check if the payload is an XMP segment
                        if (payload.length >= XMP_IDENTIFIER.length && Arrays.equals(Arrays.copyOfRange(payload, 0, XMP_IDENTIFIER.length), XMP_IDENTIFIER))
                        {
                            xmpSegments.add(payload);
                        }
                    }

                    else
                    {
                        // Skip other segments
                        int length = ((is.read() & 0xFF) << 8) | (is.read() & 0xFF);
                        long skippedBytes = is.skip(length - 2);

                        // Add a check to prevent infinite loop on malformed files
                        if (skippedBytes < length - 2)
                        {
                            break;
                        }
                    }
                }
            }
        }

        catch (IOException e)
        {
            e.printStackTrace();
            return Optional.empty();
        }

        return reconstructXmpSegments(xmpSegments);
    }

    /**
     * Reconstructs a complete XMP data block by concatenating multiple raw XMP segments.
     *
     * @param segments
     *        The list of byte arrays, each representing a raw XMP segment.
     * @return An Optional containing the concatenated byte array, or Optional.empty() if no
     *         segments are available.
     */
    private Optional<byte[]> reconstructXmpSegments(List<byte[]> segments)
    {
        if (segments.isEmpty())
        {
            return Optional.empty();
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
        {
            for (byte[] seg : segments)
            {
                // Skip the identifier string and the null terminator
                baos.write(seg, XMP_IDENTIFIER.length, seg.length - XMP_IDENTIFIER.length);
            }

            return Optional.of(baos.toByteArray());
        }

        catch (IOException e)
        {
            e.printStackTrace();

            return Optional.empty();
        }
    }

    /**
     * Parses an XML stream and returns an Optional containing the Document object.
     *
     * @param xmpInputStream
     *        The input stream containing the XMP XML data.
     * @return An Optional containing the parsed Document, or Optional.empty() if parsing fails.
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

        catch (ParserConfigurationException pce)
        {
            System.err.println("Parser configuration error: " + pce.getMessage());
        }

        catch (SAXException se)
        {
            System.err.println("XML parsing error: " + se.getMessage());
        }

        catch (IOException ioe)
        {
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
     *        The full namespace URI of the property.
     * @param localName
     *        The local name of the property.
     * @return An Optional containing the property's text content, or Optional.empty() if not found.
     */

    public Optional<String> getXmpPropertyValue(Document doc, String namespaceUri, String localName)
    {
        try
        {
            XPath xpath = XPathFactory.newInstance().newXPath();

            // The NamespaceContext is essential for XPath to understand prefixes like "dc"
            NamespaceContext nsContext = new NamespaceContext()
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
                    if ("http://purl.org/dc/elements/1.1/".equals(uri)) return "dc";
                    if ("http://ns.adobe.com/xap/1.0/".equals(uri)) return "xmp";
                    if ("http://ns.adobe.com/photoshop/1.0/".equals(uri)) return "photoshop";
                    if ("http://ns.adobe.com/xap/1.0/mm/".equals(uri)) return "xmpMM";
                    if ("http://www.w3.org/1999/02/22-rdf-syntax-ns#".equals(uri)) return "rdf";
                    return null;
                }

                @Override
                public Iterator<String> getPrefixes(String uri)
                {
                    // Not strictly needed for this use case, can return null or an empty iterator
                    return null;
                }
            };

            // This is the line that makes the magic happen: it tells the XPath engine
            // how to map prefixes to URIs.
            xpath.setNamespaceContext(nsContext);

            // A more robust XPath expression that searches for a specific element name
            // within a given namespace. This works regardless of the prefix the
            // document actually uses, as the NamespaceContext handles the mapping.
            String xPathExpression = String.format("//*[local-name()='%s' and namespace-uri()='%s']", localName, namespaceUri);

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

    public Optional<String> getXmpPropertyValue2(Document doc, String namespaceUri, String localName)
    {
        try
        {
            XPath xpath = XPathFactory.newInstance().newXPath();

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

                public String getPrefix(String uri)
                {
                    if ("http://purl.org/dc/elements/1.1/".equals(uri)) return "dc";
                    if ("http://ns.adobe.com/xap/1.0/".equals(uri)) return "xmp";
                    if ("http://ns.adobe.com/photoshop/1.0/".equals(uri)) return "photoshop";
                    if ("http://ns.adobe.com/xap/1.0/mm/".equals(uri)) return "xmpMM";
                    if ("http://www.w3.org/1999/02/22-rdf-syntax-ns#".equals(uri)) return "rdf";

                    return null;
                }

                public Iterator<?> getPrefixes(String uri)
                {
                    return null;
                }
            };

            xpath.setNamespaceContext(nsContext);

            String prefix = nsContext.getPrefix(namespaceUri);

            if (prefix == null)
            {
                return Optional.empty();
            }

            Node node = (Node) xpath.evaluate("//rdf:Description[@rdf:about]//*/" + prefix + ":" + localName, doc, XPathConstants.NODE);

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

    /**
     * Displays all properties within the Dublin Core namespace found in the document.
     *
     * @param doc
     *        The parsed XML Document object.
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

    /**
     * Main method to demonstrate the functionality of the parser.
     * It reads a JPG file, extracts the XMP data, and prints the Dublin Core metadata.
     */
    public static void main(String[] args)
    {
        if (args.length < 1)
        {
            System.out.println("Usage: java RobustXmpParser <path_to_jpg_file>");
            // return;
        }

        Path jpgFile = Paths.get("img\\test.jpg");
        RobustXmpParser parser = new RobustXmpParser();

        System.out.println("Reading XMP data from: " + jpgFile);

        Optional<byte[]> xmpBytesOptional = parser.readXmpSegments(jpgFile);

        if (xmpBytesOptional.isPresent())
        {
            System.out.printf("jpgFile: %s\n", jpgFile);

            System.out.println("Found XMP data, attempting to parse...");

            byte[] xmpBytes = xmpBytesOptional.get();

            try (ByteArrayInputStream bais = new ByteArrayInputStream(xmpBytes))
            {
                Optional<Document> docOptional = parser.parseXmp(bais);

                if (docOptional.isPresent())
                {
                    // parser.displayDublinCore(docOptional.get());
                    String creator = parser.getXmpPropertyValue(docOptional.get(), "http://purl.org/dc/elements/1.1/", "creator").orElse("BOOM");                    
                    System.out.printf("creator %s\n", creator);
                }

                else
                {
                    System.out.println("Failed to parse XMP data.");
                }
            }

            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        else
        {
            System.out.println("No XMP data found in the file.");
        }
    }
}