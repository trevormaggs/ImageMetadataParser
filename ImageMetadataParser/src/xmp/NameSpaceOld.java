package xmp;

/**
 * Defines the standard XMP namespaces used to qualify properties within an XMP metadata packet.
 * Each constant encapsulates a preferred prefix and its corresponding absolute URI as defined by
 * the XMP Specification and the ISO 16684-1 standard.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 21 January 2026
 */
public enum NameSpaceOld
{
    DC("dc", "http://purl.org/dc/elements/1.1/"),
    // XAP("xap", "http://ns.adobe.com/xap/1.0/"),
    XPM("xmp", "http://ns.adobe.com/xap/1.0/"),
    XMPMM("xmpMM", "http://ns.adobe.com/xap/1.0/mm/"),
    EXIF("exif", "http://ns.adobe.com/exif/1.0/"),
    TIFF("tiff", "http://ns.adobe.com/tiff/1.0/"),
    UNKNOWN("unknown", "");

    private final String prefix;
    private final String uri;

    private NameSpaceOld(String prefix, String uri)
    {
        this.uri = uri;
        this.prefix = prefix;
    }

    /**
     * Gets an abbreviated string of this constant's prefix name.
     *
     * @return the prefix, for example: dc, xap, etc
     */
    public String getPrefix()
    {
        return prefix;
    }

    /**
     * Gets the namespace URI for this {@code NameSpace} constant.
     *
     * @return the URI, for example: http://purl.org/dc/elements/1.1/, etc
     */
    public String getURI()
    {
        return uri;
    }

    /**
     * Resolves a {@code NameSpace} constant from the specified namespace URI.
     *
     * @param uri
     *        the namespace URI, for example: http://purl.org/dc/elements/1.1/, etc
     * @return the corresponding NameSpace, or #UNKNOWN if not recognised
     */
    public static NameSpaceOld fromNamespaceURI(String uri)
    {
        for (NameSpaceOld ns : NameSpaceOld.values())
        {
            if (ns.uri.equals(uri))
            {
                return ns;
            }
        }

        return UNKNOWN;
    }

    /**
     * Resolves a {@code NameSpace} constant from the specified prefix name.
     *
     * @param prefix
     *        the prefix to identify the namespace URI, for example: dc, xap, etc
     * @return the corresponding NameSpace, or #UNKNOWN if not recognised
     */
    public static NameSpaceOld fromNamespacePrefix(String prefix)
    {
        for (NameSpaceOld ns : NameSpaceOld.values())
        {
            if (ns.prefix.equals(prefix))
            {
                return ns;
            }
        }

        return UNKNOWN;
    }
}