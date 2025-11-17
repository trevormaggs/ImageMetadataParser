package tif;

/**
 * This program enumerates Image File Directory (IFD) types typically found within TIFF image files.
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
    // IFD_JPEG_INTERCHANGE_FORMAT("JPEG Thumbnail IFD"), <- Need to check
    IFD_DIRECTORY_UNKNOWN("Unknown");

    public static final DirectoryIdentifier IFD_BASELINE_DIRECTORY = IFD_DIRECTORY_IFD0;
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
     * Gets the description of the directory.
     * 
     * @return the name of the Directory, for example, IFD0, IFD1 etc
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Finds the next available IFD structure from the current directory. For instance, going from
     * IFD0 to IFD1. At the time of its development, it only supports up to IFD3 maximum.
     *
     * @param dirType
     *        the type of directory being read
     * @return the next available DirectoryIdentifier, or the current DirectoryIdentifier if the
     *         maximum IFD number has been reached
     * 
     * @throws IllegalArgumentException
     *         if the input value is TIFF_DIRECTORY_IFD3, which is the maximum
     */
    public static DirectoryIdentifier getNextDirectoryType(DirectoryIdentifier dirType)
    {
        switch (dirType)
        {
            case IFD_DIRECTORY_IFD0:
                return IFD_DIRECTORY_IFD1;
            case IFD_DIRECTORY_IFD1:
                return IFD_DIRECTORY_IFD2;
            case IFD_DIRECTORY_IFD2:
                return IFD_DIRECTORY_IFD3;
            case IFD_DIRECTORY_IFD3:
                throw new IllegalStateException("Maximum TIFF IFD level (IFD3) reached. Cannot seek to the next sequential IFD");
            default:
                throw new IllegalArgumentException(String.format("Directory type %s does not link sequentially to a next main IFD.", dirType.getDescription()));
        }
    }
}