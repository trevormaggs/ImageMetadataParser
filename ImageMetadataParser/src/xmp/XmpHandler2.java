package xmp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import common.ImageHandler;
import common.ImageReadErrorException;
import logger.LogFactory;

/**
 * Handles XMP metadata extraction from JPEG APP1 segments.
 *
 * @author Trevor
 * @version 2.0 (refactored)
 * @since 27 August 2025
 */
public class XmpHandler2 implements ImageHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(XmpHandler.class);
    private final Document doc;

    /** Static map of supported XMP namespaces */
    private static final Map<String, String> NAMESPACES;
    static
    {
        Map<String, String> ns = new HashMap<>();
        ns.put("dc", "http://purl.org/dc/elements/1.1/");
        ns.put("xap", "http://ns.adobe.com/xap/1.0/");
        ns.put("xmp", "http://ns.adobe.com/xap/1.0/"); // alias for xap
        ns.put("photoshop", "http://ns.adobe.com/photoshop/1.0/");
        ns.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        ns.put("xmpMM", "http://ns.adobe.com/xap/1.0/mm/");
        NAMESPACES = Collections.unmodifiableMap(ns);
    }

    /** Namespace resolver */
    private static final NamespaceContext NAMESPACE_CONTEXT = new NamespaceContext()
    {
        @Override
        public String getNamespaceURI(String prefix)
        {
            if (prefix == null) throw new IllegalArgumentException("Prefix cannot be null");
            String uri = NAMESPACES.get(prefix);
            return uri != null ? uri : XMLConstants.NULL_NS_URI;
        }

        @Override
        public String getPrefix(String namespaceURI)
        {
            for (Map.Entry<String, String> entry : NAMESPACES.entrySet())
            {
                if (entry.getValue().equals(namespaceURI)) return entry.getKey();
            }
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI)
        {
            String prefix = getPrefix(namespaceURI);
            if (prefix == null) return Collections.<String> emptyList().iterator();
            return Collections.singletonList(prefix).iterator();
        }
    };

    /** Construct from raw XMP bytes */
    public XmpHandler2(byte[] xmpData) throws ImageReadErrorException
    {
        if (xmpData == null || xmpData.length == 0)
        {
            throw new ImageReadErrorException("XMP Data is null or empty");
        }
        this.doc = parseXmpDocument(xmpData);
        if (this.doc == null)
        {
            throw new ImageReadErrorException("Failed to parse XMP data");
        }
    }

    /** Parse XML */
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

    /** Debug: dump namespace elements */
    private void dumpNamespaceElements(String prefix)
    {
        if (doc == null) return;
        String ns = NAMESPACES.get(prefix);
        if (ns == null) return;

        NodeList nodes = doc.getElementsByTagNameNS(ns, "*");
        LOGGER.info("Dump: namespace " + prefix + " (" + ns + ") nodes found: " + nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++)
        {
            Node n = nodes.item(i);
            LOGGER.info("node[" + i + "] nodeName=" + n.getNodeName()
                    + ", localName=" + n.getLocalName()
                    + ", nsURI=" + n.getNamespaceURI()
                    + ", type=" + n.getNodeType());
            LOGGER.info("  textContent='" + (n.getTextContent() == null ? "" : n.getTextContent().replace("\n", "\\n")) + "'");
        }
    }

    /** Get all properties grouped by namespace prefix */
    public Map<String, Map<String, String>> getAllXmpProperties()
    {
        Map<String, Map<String, String>> result = new HashMap<>();
        if (doc == null) return result;

        Set<String> seenUris = new HashSet<>();
        for (Map.Entry<String, String> nsEntry : NAMESPACES.entrySet())
        {
            String prefix = nsEntry.getKey();
            String uri = nsEntry.getValue();
            if (!seenUris.add(uri)) continue;

            Map<String, String> props = new LinkedHashMap<>();
            NodeList nodes = doc.getElementsByTagNameNS(uri, "*");

            for (int i = 0; i < nodes.getLength(); i++)
            {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE)
                {
                    Element element = (Element) node;
                    String localName = element.getLocalName();

                    // Skip rdf:Description itself and go deeper
                    if ("Description".equals(localName) && NAMESPACES.get("rdf").equals(element.getNamespaceURI()))
                    {
                        NodeList children = element.getChildNodes();
                        for (int j = 0; j < children.getLength(); j++)
                        {
                            Node child = children.item(j);
                            if (child.getNodeType() == Node.ELEMENT_NODE)
                            {
                                Element childElem = (Element) child;
                                String childName = childElem.getLocalName();
                                String value = extractValue(childElem);
                                if (!value.isEmpty())
                                {
                                    props.put(childName, value);
                                }
                            }
                        }
                        continue;
                    }

                    // Normal case
                    String value = extractValue(element);
                    if (!value.isEmpty())
                    {
                        props.put(localName, value);
                    }
                }
            }

            if (!props.isEmpty())
            {
                result.put(prefix, props);
            }
        }
        return result;
    }

    /** Extracts text or rdf:Seq/Alt/Bag values */
    private static String extractValue(Node node) {
        if (node == null) return "";

        if (node.getNodeType() == Node.TEXT_NODE) {
            return node.getNodeValue().trim();
        }

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element elem = (Element) node;
            String ln = elem.getLocalName();

            if ("Seq".equals(ln) || "Alt".equals(ln) || "Bag".equals(ln)) {
                NodeList liNodes = elem.getElementsByTagNameNS(NAMESPACES.get("rdf"), "li");
                List<String> values = new ArrayList<>();
                for (int i = 0; i < liNodes.getLength(); i++) {
                    values.add(liNodes.item(i).getTextContent().trim());
                }
                return String.join(", ", values);
            }

            return elem.getTextContent().trim();
        }
        return "";
    }


    /** XPath single property lookup */
    public String getXmpPropertyValue(String namespaceUri, String localName)
    {
        if (doc == null) return "";
        try
        {
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(NAMESPACE_CONTEXT);
            String expr = String.format(
                    "//*[local-name()='%s' and namespace-uri()='%s']",
                    localName, namespaceUri);
            Node node = (Node) xpath.evaluate(expr, doc, XPathConstants.NODE);
            if (node != null)
            {
                return extractValue(node);
            }
        }
        catch (XPathExpressionException e)
        {
            LOGGER.error("XPath error [" + e.getMessage() + "]", e);
        }
        return "";
    }

    @Override
    public long getSafeFileSize()
    {
        throw new UnsupportedOperationException("Not applicable for XMP handler");
    }

    @Override
    public boolean parseMetadata()
    {
        if (doc == null) return false;
        Map<String, Map<String, String>> allProps = getAllXmpProperties();
        for (Map.Entry<String, Map<String, String>> nsEntry : allProps.entrySet())
        {
            String prefix = nsEntry.getKey();
            for (Map.Entry<String, String> entry : nsEntry.getValue().entrySet())
            {
                LOGGER.info(prefix + ":" + entry.getKey() + " = " + entry.getValue());
            }
        }
        return true;
    }
    
    private static String nsPrefix(String ns) {
        if (ns.equals("http://ns.adobe.com/xap/1.0/")) return "xap"; // core XMP
        if (ns.equals("http://ns.adobe.com/xmp/1.0/")) return "xmp"; // alias
        if (ns.equals("http://purl.org/dc/elements/1.1/")) return "dc";
        if (ns.equals("http://ns.adobe.com/exif/1.0/")) return "exif";
        if (ns.equals("http://ns.adobe.com/tiff/1.0/")) return "tiff";
        if (ns.equals("http://ns.adobe.com/photoshop/1.0/")) return "photoshop";
        if (ns.equals("http://ns.adobe.com/xap/1.0/mm/")) return "xapMM";
        if (ns.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#")) return "rdf";
        return "ns";
    }

    private static void traverse(Node node, Map<String, String> props) {
        if (node.getNodeType() != Node.ELEMENT_NODE) return;

        Element element = (Element) node;
        String ns = element.getNamespaceURI();
        String localName = element.getLocalName();

        // --- Case 1: skip rdf:Description wrapper ---
        if ("Description".equals(localName) && ns.equals(NAMESPACES.get("rdf"))) {
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                traverse(children.item(i), props);
            }
            return;
        }

        // --- Case 2: actual RDF containers ---
        if (ns.equals(NAMESPACES.get("rdf"))) {
            if ("Seq".equals(localName) || "Alt".equals(localName) ||
                "Bag".equals(localName) || "li".equals(localName)) {
                String value = extractValue(element);
                if (!value.isEmpty()) {
                    props.put("rdf:" + localName, value);
                }
            }
        } else {
            // --- Case 3: normal XMP properties ---
            String value = extractValue(element);
            if (!value.isEmpty()) {
                props.put(nsPrefix(ns) + ":" + localName, value);
            }
        }

        // --- Recurse deeper ---
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            traverse(children.item(i), props);
        }
    }

}
