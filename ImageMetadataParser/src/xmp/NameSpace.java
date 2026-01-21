package xmp;

/**
 * Defines the standard XMP namespaces used to qualify properties within an XMP metadata packet.
 * Each constant encapsulates a preferred prefix and its corresponding absolute URI as defined by
 * the XMP Specification and the ISO 16684-1 standard.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 25 September 2025
 */
public enum NameSpace
{
    /** Dublin Core schema. */
    DC("dc", "http://purl.org/dc/elements/1.1/"),

    /** XMP Basic schema. */
    XPM("xmp", "http://ns.adobe.com/xap/1.0/"),

    /** XMP Media Management schema. */
    XMPMM("xmpMM", "http://ns.adobe.com/xap/1.0/mm/"),

    /** EXIF schema for XMP. */
    EXIF("exif", "http://ns.adobe.com/exif/1.0/"),

    /** TIFF schema for XMP. */
    TIFF("tiff", "http://ns.adobe.com/tiff/1.0/"),

    /** Placeholder for unrecognised or custom namespaces. */
    UNKNOWN("unknown", "");

    private final String prefix;
    private final String uri;

    private NameSpace(String prefix, String uri)
    {
        this.uri = uri;
        this.prefix = prefix;
    }

    /**
     * Retrieves the preferred abbreviated prefix for this namespace.
     *
     * @return the namespace prefix, for example: "dc", "xmp", "exif"
     */
    public String getPrefix()
    {
        return prefix;
    }

    /**
     * Retrieves the unique Resource Identifier (URI) for this namespace.
     *
     * @return the absolute namespace URI string
     */
    public String getURI()
    {
        return uri;
    }

    /**
     * Resolves a {@code NameSpace} constant based on its absolute URI.
     *
     * @param uri
     *        the namespace URI string to lookup
     * @return the matching {@code NameSpace} constant, or {@link #UNKNOWN} if the URI is not
     *         recognised
     */
    public static NameSpace fromNamespaceURI(String uri)
    {
        if (uri == null) return UNKNOWN;

        for (NameSpace ns : NameSpace.values())
        {
            if (ns.uri.equals(uri))
            {
                return ns;
            }
        }

        return UNKNOWN;
    }

    /**
     * Resolves a {@code NameSpace} constant based on its preferred prefix.
     *
     * @param prefix
     *        the prefix string to lookup, for example: dc, xap, etc
     * @return the matching {@code NameSpace} constant, or {@link #UNKNOWN} if the prefix is not recognised
     */
    public static NameSpace fromNamespacePrefix(String prefix)
    {
        if (prefix == null) return UNKNOWN;

        for (NameSpace ns : NameSpace.values())
        {
            if (ns.prefix.equals(prefix))
            {
                return ns;
            }
        }

        return UNKNOWN;
    }
}