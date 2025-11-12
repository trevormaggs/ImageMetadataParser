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

    XPM_CREATEDATE("CreateDate", NameSpace.XPM),
    XPM_CREATORTOOL("CreatorTool", NameSpace.XPM),
    XPM_METADATADATE("MetadataDate", NameSpace.XPM),
    XPM_MODIFYDATE("ModifyDate", NameSpace.XPM),
    XPM_ADVISORY("Advisory", NameSpace.XPM),
    XPM_BASEURL("BaseURL", NameSpace.XPM),
    XPM_IDENTIFIER("Identifier", NameSpace.XPM),
    XPM_LABEL("Label", NameSpace.XPM),
    XPM_NICKNAME("Nickname", NameSpace.XPM),
    XPM_RATING("Rating", NameSpace.XPM),
    XPM_THUMBNAILS("Thumbnails", NameSpace.XPM),

    XMPMM_DOCUMENTID("DocumentID", NameSpace.XMPMM),
    XMPMM_INSTANCEID("InstanceID", NameSpace.XMPMM),
    XMPMM_ORIGINALDOCUMENTID("OriginalDocumentID", NameSpace.XMPMM),
    XMPMM_HISTORY("History", NameSpace.XMPMM),
    XMPMM_DERIVEDFROM("DerivedFrom", NameSpace.XMPMM),
    XMPMM_RENDITIONCLASS("RenditionClass", NameSpace.XMPMM),
    XMPMM_VERSIONID("VersionID", NameSpace.XMPMM),
    XMPMM_VERSIONS("Versions", NameSpace.XMPMM),
    XMPMM_INGREDIENTS("Ingredients", NameSpace.XMPMM),
    
    // Often used to store basic image data like resolution and orientation
    TIFF_ORIENTATION("Orientation", NameSpace.TIFF),
    TIFF_XRESOLUTION("XResolution", NameSpace.TIFF),
    TIFF_YRESOLUTION("YResolution", NameSpace.TIFF),
    TIFF_RESOLUTIONUNIT("ResolutionUnit", NameSpace.TIFF),
    TIFF_DATETIME("DateTime", NameSpace.TIFF),
    TIFF_IMAGEDESCRIPTION("ImageDescription", NameSpace.TIFF),
    TIFF_MAKE("Make", NameSpace.TIFF),
    TIFF_MODEL("Model", NameSpace.TIFF),
    TIFF_SOFTWARE("Software", NameSpace.TIFF),

    // Used for camera and capture settings
    EXIF_DATETIMEORIGINAL("DateTimeOriginal", NameSpace.EXIF),
    EXIF_DATETIMEDIGITIZED("DateTimeDigitized", NameSpace.EXIF),
    EXIF_EXPOSURETIME("ExposureTime", NameSpace.EXIF),
    EXIF_FNUMBER("FNumber", NameSpace.EXIF),
    EXIF_ISOSPEEDRATINGS("ISOSpeedRatings", NameSpace.EXIF),
    EXIF_SHUTTERSPEEDVALUE("ShutterSpeedValue", NameSpace.EXIF),
    EXIF_APERTUREVALUE("ApertureValue", NameSpace.EXIF),
    EXIF_BRIGHTNESSVALUE("BrightnessValue", NameSpace.EXIF),
    EXIF_FLASH("Flash", NameSpace.EXIF),
    EXIF_FOCALLENGTH("FocalLength", NameSpace.EXIF),
    EXIF_COLORSPACE("ColorSpace", NameSpace.EXIF),
    EXIF_PIXELXDIMENSION("PixelXDimension", NameSpace.EXIF),
    EXIF_PIXELYDIMENSION("PixelYDimension", NameSpace.EXIF),

    UNKNOWN("unknown", NameSpace.UNKNOWN);

    private final String propName;
    private final NameSpace schema;
    private static final Map<String, XmpSchema> NAME_LOOKUP = new HashMap<>();

    static
    {
        for (XmpSchema type : values())
        {
            if (type.schema != NameSpace.UNKNOWN)
            {
                String key = String.format("%s:%s", type.getSchemaPrefix(), type.getPropertyName()).toLowerCase(Locale.ROOT);

                NAME_LOOKUP.put(key, type);
            }
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
     * @return the abbreviated schema name, for example: DC, XAP, etc
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
     * Returns the canonical qualified property path for this schema constant, for example:
     * "dc:creator" or "xap:CreateDate".
     *
     * @return the canonical qualified path
     */
    public String getQualifiedPath()
    {
        if (this == UNKNOWN)
        {
            return "";
        }

        return String.format("%s:%s", getSchemaPrefix(), getPropertyName());
    }

    /**
     * Resolves an {@code XmpSchema} from the specified qualified property path.
     *
     * @param qualifiedPath
     *        the property path (case-insensitive), for example: "dc:format", "xap:CreateDate", etc
     * @return the corresponding XmpSchema, or #UNKNOWN if it is not recognised
     */
    public static XmpSchema fromQualifiedPath(String qualifiedPath)
    {
        if (qualifiedPath == null)
        {
            return UNKNOWN;
        }

        return NAME_LOOKUP.getOrDefault(qualifiedPath.toLowerCase(Locale.ROOT), UNKNOWN);
    }
}