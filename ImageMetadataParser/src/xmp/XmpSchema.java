package xmp;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Represents a fixed set of known XMP properties and their corresponding namespaces. This enum
 * provides a type-safe way to handle XMP schema names, enabling efficient lookup and preventing
 * errors from misspelled strings.
 * 
 * @author Trevor Maggs
 * @version 1.0
 * @since 25 September 2025
 */
public enum XmpSchema
{
    DC_CONTRIBUTOR("contributor", NameSpace.DC),
    DC_COVERAGE("coverage", NameSpace.DC),
    DC_CREATOR("creator", NameSpace.DC),
    DC_DATE("date", NameSpace.DC),
    DC_DESCRIPTION("description", NameSpace.DC),
    DC_FORMAT("format", NameSpace.DC),
    DC_IDENTIFIER("identifier", NameSpace.DC),
    DC_LANGUAGE("language", NameSpace.DC),
    DC_PUBLISHER("publisher", NameSpace.DC),
    DC_RELATION("relation", NameSpace.DC),
    DC_RIGHTS("rights", NameSpace.DC),
    DC_SOURCE("source", NameSpace.DC),
    DC_SUBJECT("subject", NameSpace.DC),
    DC_TITLE("title", NameSpace.DC),
    DC_TYPE("type", NameSpace.DC),

    XAP_CREATEDATE("CreateDate", NameSpace.XAP),
    XAP_CREATORTOOL("CreatorTool", NameSpace.XAP),
    XAP_METADATADATE("MetadataDate", NameSpace.XAP),
    XAP_MODIFYDATE("ModifyDate", NameSpace.XAP),
    XAP_ADVISORY("Advisory", NameSpace.XAP),
    XAP_BASEURL("BaseURL", NameSpace.XAP),
    XAP_IDENTIFIER("Identifier", NameSpace.XAP),
    XAP_LABEL("Label", NameSpace.XAP),
    XAP_NICKNAME("Nickname", NameSpace.XAP),
    XAP_RATING("Rating", NameSpace.XAP),
    XAP_THUMBNAILS("Thumbnails", NameSpace.XAP),

    XMPMM_DOCUMENTID("DocumentID", NameSpace.XMPMM),
    XMPMM_INSTANCEID("InstanceID", NameSpace.XMPMM),
    XMPMM_ORIGINALDOCUMENTID("OriginalDocumentID", NameSpace.XMPMM),
    XMPMM_HISTORY("History", NameSpace.XMPMM),
    XMPMM_DERIVEDFROM("DerivedFrom", NameSpace.XMPMM),
    XMPMM_RENDITIONCLASS("RenditionClass", NameSpace.XMPMM),
    XMPMM_VERSIONID("VersionID", NameSpace.XMPMM),
    XMPMM_VERSIONS("Versions", NameSpace.XMPMM),
    XMPMM_INGREDIENTS("Ingredients", NameSpace.XMPMM),

    UNKNOWN("unknown", NameSpace.UNKNOWN);

    private final String propName;
    private final NameSpace schema;
    private static final Map<String, XmpSchema> NAME_LOOKUP = new HashMap<>();

    static
    {
        for (XmpSchema type : values())
        {
            // Ensures case-insensitive lookup
            NAME_LOOKUP.put(type.propName.toLowerCase(Locale.ROOT), type);
        }
    }

    private XmpSchema(String propName, NameSpace schema)
    {
        this.propName = propName;
        this.schema = schema;
    }

    /**
     * Returns the local property name of the schema, for example: {@code creator} or
     * {@code CreateDate}.
     *
     * @return the local property name
     */
    public String getPropertyName()
    {
        return propName;
    }

    /**
     * Returns the associated namespace constant, for example: DC, XAP, etc.
     *
     * @return the schema namespace constant
     */
    public NameSpace getNameSpaceConstant()
    {
        return schema;
    }

    /**
     * Returns the abbreviated prefix name of the schema.
     *
     * @return the abbreviated schema name, e.g., {@code dc} or {@code xap}
     */
    public String getSchemaPrefix()
    {
        return schema.getPrefix();
    }

    /**
     * Returns the full URI of the associated XMP namespace.
     *
     * @return the full namespace URI
     */
    public String getNamespaceURI()
    {
        return schema.getURI();
    }

    /**
     * Resolves an {@code XmpSchema} from the specified property name. This uses a pre-populated
     * map for efficient lookup, making it O(1) on average.
     *
     * @param name
     *        the property name (case-insensitive), for example: {@code format}, {@code CreateDate},
     *        etc
     * @return the corresponding {@code XmpSchema}, or {@link #UNKNOWN} if it is not recognised
     */
    public static XmpSchema fromPropertyName(String propName)
    {
        if (propName == null)
        {
            return UNKNOWN;
        }

        return NAME_LOOKUP.getOrDefault(propName.toLowerCase(Locale.ROOT), UNKNOWN);
    }
}