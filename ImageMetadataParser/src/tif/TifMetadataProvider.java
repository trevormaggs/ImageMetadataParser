package tif;

import common.Metadata;
import xmp.XmpDirectory;

/**
 * Provides an specialised interface for accessing and managing TIFF-based metadata structures.
 * 
 * <p>
 * This interface extends the base {@link Metadata} contract to support the unique hierarchical
 * nature of TIFF files, allowing for the retrieval of specific Image File Directories (IFDs) such
 * as EXIF, GPS, or Interoperability sub-directories.
 * </p>
 * 
 * <p>
 * Beyond standard IFD structures, this provider also acts as a container for {@link XmpDirectory}
 * segments, which are often embedded within TIFF and JPG files as XML-based metadata packets.
 * </p>
 * 
 * @author Trevor Maggs
 * @version 1.1
 * @see DirectoryIFD
 * @see DirectoryIdentifier
 */
public interface TifMetadataProvider extends Metadata<DirectoryIFD>
{
    /**
     * Retrieves a specific Image File Directory (IFD) based on its identity.
     *
     * @param dirKey
     *        the {@link DirectoryIdentifier} representing the directory to find (i.e., IFD0,
     *        EXIF_SUBIFD, or GPS)
     * @return the requested {@link DirectoryIFD} instance, or {@code null} if the directory does
     *         not exist in the current metadata set
     */
    public DirectoryIFD getDirectory(DirectoryIdentifier dirKey);

    /**
     * Adds an XMP metadata directory.
     *
     * <p>
     * In TIFF-based formats, XMP is typically stored in a single global tag (ID 0x02BC). This
     * method allows the parsed XMP object to be attached to the primary metadata container for
     * consolidated access.
     * </p>
     * 
     * @param dir
     *        the {@link XmpDirectory} containing parsed XML metadata
     */
    public void addXmpDirectory(XmpDirectory dir);

    /**
     * Returns the XMP metadata directory.
     * 
     * @return the {@link XmpDirectory} instance, or {@code null} if no XMP data was found or added
     */
    public XmpDirectory getXmpDirectory();
}