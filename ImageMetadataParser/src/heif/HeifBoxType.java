package heif;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import common.ByteValueConverter;

/**
 * Defines all supported HEIF (High Efficiency Image File) box types.
 *
 * <p>
 * This enum uses 32-bit integer identifiers (FourCC) for O(1) resolution, avoiding the memory
 * pressure of string-based lookups during intensive file parsing.
 * </p>
 * 
 * @see <a href="http://standards.iso.org/ittf/PubliclyAvailableStandards/index.html">ISO/IEC
 *      14496-12 (ISOBMFF)</a>
 */

public enum HeifBoxType
{
    UUID("uuid", BoxCategory.ATOMIC),
    FILE_TYPE("ftyp", BoxCategory.ATOMIC),
    PRIMARY_ITEM("pitm", BoxCategory.ATOMIC),
    ITEM_PROPERTY_ASSOCIATION("ipma", BoxCategory.ATOMIC),
    ITEM_PROTECTION("ipro", BoxCategory.CONTAINER),
    ITEM_DATA("idat", BoxCategory.ATOMIC),
    ITEM_INFO("iinf", BoxCategory.ATOMIC),
    ITEM_INFO_ENTRY("infe", BoxCategory.ATOMIC),
    ITEM_REFERENCE("iref", BoxCategory.CONTAINER),
    ITEM_LOCATION("iloc", BoxCategory.ATOMIC),
    HANDLER("hdlr", BoxCategory.ATOMIC),
    HVC1("hvc1", BoxCategory.CONTAINER),
    IMAGE_SPATIAL_EXTENTS("ispe", BoxCategory.ATOMIC),
    AUXILIARY_TYPE_PROPERTY("auxC", BoxCategory.ATOMIC),
    IMAGE_ROTATION("irot", BoxCategory.ATOMIC),
    IMAGE_MIRRORING("imir", BoxCategory.ATOMIC),
    CLEAN_APERTURE("clap", BoxCategory.ATOMIC),
    COLOUR_INFO("colr", BoxCategory.ATOMIC),
    PIXEL_INFO("pixi", BoxCategory.ATOMIC),
    METADATA("meta", BoxCategory.CONTAINER),
    ITEM_PROPERTIES("iprp", BoxCategory.CONTAINER),
    ITEM_PROPERTY_CONTAINER("ipco", BoxCategory.CONTAINER),
    DATA_INFORMATION("dinf", BoxCategory.CONTAINER),
    DATA_REFERENCE("dref", BoxCategory.CONTAINER),
    MEDIA_DATA("mdat", BoxCategory.ATOMIC),
    PIXEL_ASPECT_RATIO("pasp", BoxCategory.ATOMIC),
    THUMBNAIL_REFERENCE("thmb", BoxCategory.ATOMIC),
    UNKNOWN("unknown", BoxCategory.UNKNOWN);

    /*
     * Type,Name,Category,Purpose
     * free,Free Space,ATOMIC,Skippable padding or unused space.
     * skip,Skip,ATOMIC,Similar to free; used to reserve space for metadata.
     * cdsc,Content Description,ATOMIC,A common reference type used inside iref.
     * dimg,Derived Image,ATOMIC,Reference type for derived images (like tiles or grids).
     */

    /**
     * Describes the general role of the box in the file structure.
     */
    public enum BoxCategory
    {
        /**
         * An individual box containing data fields.
         */
        ATOMIC,

        /**
         * A container box holding child boxes.
         */
        CONTAINER,

        /**
         * An unknown or unsupported box type.
         */
        UNKNOWN;
    }

    private final String typeName;
    private final int typeInt;
    private final BoxCategory category;
    private final byte[] typeBytes;
    private static final Map<Integer, HeifBoxType> TYPEINT_LOOKUP = new HashMap<>();

    static
    {
        for (HeifBoxType type : values())
        {
            TYPEINT_LOOKUP.put(type.typeInt, type);
        }
    }

    /**
     * Constructs a {@code HeifBoxType} enum constant with its 4-character identifier and category.
     *
     * @param typeName
     *        the 4-character box type, for example, {@code ftyp}
     * @param category
     *        the box's structural category
     */
    private HeifBoxType(String typeName, BoxCategory category)
    {
        this.typeName = typeName;
        this.category = category;
        this.typeBytes = typeName.getBytes(StandardCharsets.US_ASCII);
        this.typeInt = ByteValueConverter.toInteger(typeBytes, BoxHandler.HEIF_BYTE_ORDER);
    }

    /**
     * Returns the 32-bit integer representation of the FourCC (Four-Character Code).
     * *
     * <p>
     * This integer is derived from the big-endian interpretation of the 4-character box type
     * identifier. It is used for high-performance lookups.
     * </p>
     *
     * @return the 32-bit integer identifier for this box type
     */
    public int getTypeInt()
    {
        return typeInt;
    }

    /**
     * Returns the 4-character string identifier for this box type.
     *
     * @return the box type string, for example, {@code ftyp}, {@code meta}, {@code idat} etc
     */
    public String getTypeName()
    {
        return typeName;
    }

    /**
     * Returns the category of this box (ATOMIC, CONTAINER, or UNKNOWN).
     *
     * @return the box category
     */
    public BoxCategory getBoxCategory()
    {
        return category;
    }

    /**
     * Returns the original 4 bytes identifying this box type.
     *
     * @return an array of 4 bytes
     */
    public byte[] getTypeBytes()
    {
        return typeBytes;
    }

    /**
     * Checks if the type of this box name matches the specified case-sensitive string. This
     * method is generally used for internal comparisons within the enum. For external lookup,
     * {@link #fromTypeInt(int)} is recommended.
     *
     * @param name
     *        the box name to compare
     * 
     * @return true if the names match
     */
    public boolean equalsTypeName(String name)
    {
        return typeName.equals(name);
    }

    /**
     * Resolves a {@code HeifBoxType} from a 32-bit integer.
     * 
     * <p>
     * The integer must represent the FourCC in Big Endian order (i.e. {@code ftyp} becomes
     * 0x66747970). This provides O(1) lookup without the overhead of String allocation or character
     * decoding.
     * </p>
     *
     * @param code
     *        the 32-bit integer representation of the box type
     * @return the matching {@code HeifBoxType}, or {@link #UNKNOWN} if not supported
     */
    public static HeifBoxType fromTypeInt(int code)
    {
        return TYPEINT_LOOKUP.getOrDefault(code, UNKNOWN);
    }
}