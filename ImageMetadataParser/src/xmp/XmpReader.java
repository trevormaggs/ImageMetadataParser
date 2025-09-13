package xmp;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

public class XmpReader
{
    public static void main(String[] args) throws Exception
    {
        // Parse with namespace awareness ON
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        // Replace with your .xml or extracted XMP file
        Document doc = builder.parse(new File("pool19.xmp"));

        String XAP_NS = "http://ns.adobe.com/xap/1.0/";

        NodeList nodes = doc.getElementsByTagNameNS(XAP_NS, "*");
        System.out.println("Found " + nodes.getLength() + " xap:* nodes");

        for (int i = 0; i < nodes.getLength(); i++)  
        {
            Element el = (Element) nodes.item(i);
            String name = el.getLocalName();
            String value = el.getTextContent();
            System.out.println("  " + name + " = " + value);
        }
    }
}
