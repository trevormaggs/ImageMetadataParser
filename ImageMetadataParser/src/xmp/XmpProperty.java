package xmp;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Represents a fixed set of known XMP properties and their corresponding namespaces. This enum
 * provides a type-safe way to handle XMP schema names, enabling efficient lookup and preventing
 * errors from misspelled strings.
 * 
 * <p>
 * This registry covers the most common schemas found in digital imaging, including:
 * </p>
 * 
 * <ul>
 * <li><b>DC:</b> Dublin Core (Standard descriptive metadata)</li>
 * <li><b>XMP:</b> XMP Basic (General tool and workflow metadata)</li>
 * <li><b>TIFF/EXIF:</b> Binary-to-XMP mirrored properties</li>
 * </ul>
 * 
 * @author Trevor Maggs
 * @version 1.0
 * @since 21 January 2026
 */
public enum XmpProperty
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

    XMP_CREATEDATE("CreateDate", NameSpace.XPM),
    XMP_CREATORTOOL("CreatorTool", NameSpace.XPM),
    XMP_METADATADATE("MetadataDate", NameSpace.XPM),
    XMP_MODIFYDATE("ModifyDate", NameSpace.XPM),
    XMP_ADVISORY("Advisory", NameSpace.XPM),
    XMP_BASEURL("BaseURL", NameSpace.XPM),
    XMP_IDENTIFIER("Identifier", NameSpace.XPM),
    XMP_LABEL("Label", NameSpace.XPM),
    XMP_NICKNAME("Nickname", NameSpace.XPM),
    XMP_RATING("Rating", NameSpace.XPM),
    XMP_THUMBNAILS("Thumbnails", NameSpace.XPM),

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
    TIFF_RESOLUTION_UNIT("ResolutionUnit", NameSpace.TIFF),
    TIFF_DATE_TIME("DateTime", NameSpace.TIFF),
    TIFF_IMAGE_DESCRIPTION("ImageDescription", NameSpace.TIFF),
    TIFF_MAKE("Make", NameSpace.TIFF),
    TIFF_MODEL("Model", NameSpace.TIFF),
    TIFF_SOFTWARE("Software", NameSpace.TIFF),

    // Used for camera and capture settings
    EXIF_DATE_TIME_ORIGINAL("DateTimeOriginal", NameSpace.EXIF),
    EXIF_DATE_TIME_DIGITIZED("DateTimeDigitized", NameSpace.EXIF),
    EXIF_EXPOSURE_TIME("ExposureTime", NameSpace.EXIF),
    EXIF_FNUMBER("FNumber", NameSpace.EXIF),
    EXIF_ISO_SPEED_RATINGS("ISOSpeedRatings", NameSpace.EXIF),
    EXIF_SHUTTER_SPEED_VALUE("ShutterSpeedValue", NameSpace.EXIF),
    EXIF_APERTURE_VALUE("ApertureValue", NameSpace.EXIF),
    EXIF_BRIGHTNESS_VALUE("BrightnessValue", NameSpace.EXIF),
    EXIF_FLASH("Flash", NameSpace.EXIF),
    EXIF_FOCAL_LENGTH("FocalLength", NameSpace.EXIF),
    EXIF_COLOUR_SPACE("ColorSpace", NameSpace.EXIF),
    EXIF_PIXEL_XDIMENSION("PixelXDimension", NameSpace.EXIF),
    EXIF_PIXEL_YDIMENSION("PixelYDimension", NameSpace.EXIF),

    UNKNOWN("unknown", NameSpace.UNKNOWN);

    private final String propName;
    private final NameSpace schema;
    private static final Map<String, XmpProperty> NAME_LOOKUP = new HashMap<>();

    static
    {
        for (XmpProperty type : values())
        {
            if (type.schema != NameSpace.UNKNOWN)
            {
                String key = String.format("%s:%s", type.getSchemaPrefix(), type.getPropertyName()).toLowerCase(Locale.ROOT);

                NAME_LOOKUP.put(key, type);
            }
        }
    }

    private XmpProperty(String propName, NameSpace schema)
    {
        this.propName = propName;
        this.schema = schema;
    }

    /**
     * Returns the local property name within the schema, for example: {@code creator} or
     * {@code CreateDate}.
     *
     * @return the local property name string
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
     * Returns the canonical qualified property path for this schema constant.
     * 
     * <p>
     * For example: {@code dc:creator} or {@code xmp:CreateDate}. This path is used as the unique
     * key for metadata lookups.
     * </p>
     *
     * @return the qualified path (prefix:name), or an empty string if UNKNOWN
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
     * Resolves an {@link XmpProperty} constant from a specified qualified property path.
     *
     * @param qualifiedPath
     *        the property path, for example: "dc:format", case-insensitive
     * @return the matching {@code XmpProperty} constant, or {@link #UNKNOWN} if not found
     */
    public static XmpProperty fromQualifiedPath(String qualifiedPath)
    {
        if (qualifiedPath == null)
        {
            return UNKNOWN;
        }

        return NAME_LOOKUP.getOrDefault(qualifiedPath.toLowerCase(Locale.ROOT), UNKNOWN);
    }
}