package tif;

/**
 * This program enumerates Image File Directory (IFD) types typically found within TIFF image files.
 *
 * @author Trevor Maggs
 * @version 1.1
 * @since 13 August 2025
 */
public enum DirectoryIdentifier
{
    IFD_DIRECTORY_IFD0("IFD0", true),
    IFD_DIRECTORY_IFD1("IFD1", true),
    IFD_DIRECTORY_IFD2("IFD2", true),
    IFD_DIRECTORY_IFD3("IFD3", true),
    IFD_EXIF_SUBIFD_DIRECTORY("Exif SubIFD", false),
    IFD_DIRECTORY_SUBIFD("SubIFD", false),
    IFD_GPS_DIRECTORY("GPS IFD", false),
    EXIF_INTEROP_DIRECTORY("Interop IFD", false),
    EXIF_DIRECTORY_MAKER_NOTES("Maker Notes", false),
    IFD_DIRECTORY_UNKNOWN("Unknown", false);

    public static final DirectoryIdentifier IFD_ROOT_DIRECTORY = IFD_DIRECTORY_IFD0;
    public static final DirectoryIdentifier IFD_THUMBNAIL_DIRECTORY = IFD_DIRECTORY_IFD1;
    private final String description;
    private final boolean mainChain;

    /**
     * This private constructor is implicitly called to initialise every directory with a distinct
     * enumeration value given.
     *
     * @param description
     *        the name that describes the specified directory
     * @param mainChain
     *        a {@code true} value to indicate linking to the next chain of IFD directory
     */
    private DirectoryIdentifier(final String description, final boolean mainChain)
    {
        this.description = description;
        this.mainChain = mainChain;
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
     * Determines if this directory is part of the primary sequential chain.
     * 
     * @return {@code true} if this is a root-level IFD (IFD0, IFD1, etc.) used for main images or
     *         thumbnail segments
     */
    public boolean isMainChain()
    {
        return mainChain;
    }
    /**
     * Finds the next available IFD structure from the current directory.
     * 
     * @param dirType
     *        the type of directory being read
     * @return the next available DirectoryIdentifier
     */
    public static DirectoryIdentifier getNextDirectoryType(DirectoryIdentifier dirType)
    {
        if (dirType == null || !dirType.isMainChain())
        {
            String desc = (dirType == null) ? "null" : dirType.getDescription();

            throw new IllegalArgumentException(String.format("Directory type %s does not link sequentially to the next IFD", desc));
        }

        switch (dirType)
        {
            case IFD_DIRECTORY_IFD0:
                return IFD_DIRECTORY_IFD1;
            case IFD_DIRECTORY_IFD1:
                return IFD_DIRECTORY_IFD2;
            case IFD_DIRECTORY_IFD2:
                return IFD_DIRECTORY_IFD3;
            case IFD_DIRECTORY_IFD3:
                throw new IllegalStateException("Maximum TIFF IFD level (IFD3) reached");
            default:
                return IFD_DIRECTORY_UNKNOWN;
        }
    }
}