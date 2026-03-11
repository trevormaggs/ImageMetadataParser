package tif;

/**
 * Enumerates and identifies Image File Directory (IFD) types found within TIFF and EXIF data
 * structures.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public enum DirectoryIdentifier
{
    // IFD_BASELINE_DIRECTORY("IFD"),
    IFD_DIRECTORY_IFD0("IFD0"),
    IFD_DIRECTORY_IFD1("IFD1"),
    IFD_DIRECTORY_IFD2("IFD2"),
    IFD_DIRECTORY_IFD3("IFD3"),

    IFD_EXIF_SUBIFD_DIRECTORY("Exif SubIFD"),
    IFD_DIRECTORY_SUBIFD("SubIFD"),
    IFD_GPS_DIRECTORY("GPS IFD"),
    EXIF_INTEROP_DIRECTORY("Interop IFD"),
    EXIF_DIRECTORY_MAKER_NOTES("Maker Notes"),
    IFD_DIRECTORY_UNKNOWN("Unknown");

    public static final DirectoryIdentifier IFD_ROOT_DIRECTORY = IFD_DIRECTORY_IFD0;
    public static final DirectoryIdentifier IFD_THUMBNAIL_DIRECTORY = IFD_DIRECTORY_IFD1;
    private final String description;

    /**
     * This private constructor is implicitly called to initialise every directory with a distinct
     * enumeration value given.
     *
     * @param description
     *        the name that describes the specified directory
     */
    private DirectoryIdentifier(final String description)
    {
        this.description = description;
    }

    /**
     * Gets a human-readable description of the directory
     * 
     * @return the name of the Directory, for example, IFD0, IFD1 etc
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Determines if this directory is part of the primary sequential TIFF chain.
     * 
     * @return {@code true} for root-level IFDs (IFD0-IFD3)
     */
    public boolean isMainChain()
    {
        return this.ordinal() <= IFD_DIRECTORY_IFD3.ordinal();
    }

    /**
     * Retrieves the next sequential directory type in the TIFF chain. For example, moving from IFD0
     * to IFD1.
     * 
     * @param dirType
     *        the current directory type being processed
     * @return the next {@link DirectoryIdentifier}, or {@code null} if the chain ends
     * 
     * @throws IllegalArgumentException
     *         if the directory is null or non-sequential
     */
    public static DirectoryIdentifier getNextDirectoryType(DirectoryIdentifier dirType)
    {
        if (dirType == null || !dirType.isMainChain())
        {
            String desc = (dirType == null) ? "null" : dirType.getDescription();

            throw new IllegalArgumentException(String.format("Directory %s is not part of the sequential chain", desc));
        }

        int nextOrdinal = dirType.ordinal() + 1;

        if (nextOrdinal < values().length && values()[nextOrdinal].isMainChain())
        {
            return values()[nextOrdinal];
        }

        return null;
    }
}