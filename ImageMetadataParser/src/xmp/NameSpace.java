package xmp;

/**
 * Describes the general namespace of the schema, storing the prefix and the full URI.
 */
public enum NameSpace
{
    DC("dc", "http://purl.org/dc/elements/1.1/"),
    XAP("xap", "http://ns.adobe.com/xap/1.0/"),
    XPM("xmp", "http://ns.adobe.com/xap/1.0/"),
    XMPMM("xmpMM", "http://ns.adobe.com/xap/1.0/mm/"),
    EXIF("exif", "http://ns.adobe.com/exif/1.0/"),
    TIFF("tiff", "http://ns.adobe.com/tiff/1.0/"),
    UNKNOWN("unknown", "");

    private final String prefix;
    private final String uri;

    private NameSpace(String prefix, String uri)
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
    public static NameSpace fromNamespaceURI(String uri)
    {
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
     * Resolves a {@code NameSpace} constant from the specified prefix name.
     *
     * @param prefix
     *        the prefix to identify the namespace URI, for example: dc, xap, etc
     * @return the corresponding NameSpace, or #UNKNOWN if not recognised
     */
    public static NameSpace fromNamespacePrefix(String prefix)
    {
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