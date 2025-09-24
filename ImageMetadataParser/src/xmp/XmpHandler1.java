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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPIterator;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.impl.ByteBuffer;
import com.adobe.internal.xmp.properties.XMPPropertyInfo;
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
public class XmpHandler1 implements ImageHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(XmpHandler.class);
    private static final NamespaceContext NAMESPACE_CONTEXT = loadNamespaceContext();
    private static final Map<String, String> NAMESPACES;
    //private final Document doc;
    private final XMPMeta xmpMeta;

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
    public XmpHandler1(byte[] xmpData) throws ImageReadErrorException
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

        // visitAllNodes(doc);

        try
        {
            XMPMeta xmp = XMPMetaFactory.parseFromBuffer(xmpData);

            if (xmp == null)
            {
                throw new ImageReadErrorException("Failed to parse XMP data");
            }

            this.xmpMeta = xmp;
        }

        catch (XMPException exc)
        {
            // TODO: log it and return
            exc.printStackTrace();
        }
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

    /** Returns all Dublin Core properties. */
    public Map<String, String> getDublinCoreProperties()
    {
        return getPropertiesByNamespace("dc");
    }

    /** Returns all core XMP properties. */
    public Map<String, String> getCoreXMPProperties()
    {
        return getPropertiesByNamespace("xmp");
        // return getPropertiesByNamespace("xap"); // or "xmp", both resolve
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
        
        XMPIterator iter;
        
        try
        {
            iter = xmpMeta.iterator();
            
            while (iter.hasNext())
            {
                Object o = iter.next();

                if (o instanceof XMPPropertyInfo)
                {
                    XMPPropertyInfo prop = (XMPPropertyInfo) o;

                    String ns = "Namespace: " + prop.getNamespace();
                    String ph = "Path: " + prop.getPath();
                    String va = "Value: " + prop.getValue();

                    System.out.printf("%-50s%-40s%-40s%n", ns, ph, va);
                }
            }
        }
        
        catch (XMPException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
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

        Map<String, String> properties = new HashMap<>();
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

    public void visitAllNodes(Node node)
    {
        // Base case: if the node is null, return.
        if (node == null)
        {
            return;
        }

        // Perform your desired action on the current node.
        System.out.println("Visiting node: " + node);
        //

        // Get the list of child nodes.
        NodeList nodeList = node.getChildNodes();

        // Recursively visit each child node.
        for (int i = 0; i < nodeList.getLength(); i++)
        {
            Node currentNode = nodeList.item(i);

            visitAllNodes(currentNode);
        }
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