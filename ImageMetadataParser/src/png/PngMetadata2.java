package png;

import static tif.tagspecs.TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import common.DateParser;
import common.ImageReadErrorException;
import logger.LogFactory;
import png.ChunkType.Category;
import tif.DirectoryIFD;
import tif.DirectoryIdentifier;
import tif.ExifMetadata;
import tif.TifParser;
import tif.tagspecs.TagPngChunk;
import tif.tagspecs.Taggable;
import xmp.XmpDirectory;
import xmp.XmpHandler;
import xmp.XmpHandler.XMPCoreProperty;

/**
 * Implements the {@link PngStrategy} interface to provide a comprehensive view and extraction
 * capability for metadata embedded within a PNG file. This class aggregates various PNG chunk
 * directories, managing and prioritising embedded metadata standards like EXIF and XMP for accurate
 * data extraction.
 *
 * <p>
 * It organises metadata into directories based on chunk category, for example: TEXTUAL, MISC, etc
 * and pro-actively parses embedded XMP data when a relevant iTXt chunk is added.
 * </p>
 *
 * @author Gemini
 */
public class PngMetadata2 implements PngStrategy
{
    private static final LogFactory LOGGER = LogFactory.getLogger(PngMetadata2.class);
    private static final String XMP_CREATE_DATE = "CreateDate";
    private static final String XMP_DATE_TIME_ORIGINAL = "DateTimeOriginal";
    private final Map<Category, PngDirectory> pngMap;
    private XmpDirectory xmpDir;

    /**
     * Constructs an empty {@code PngMetadata} object, initialising the internal map for storing PNG
     * directories.
     */
    public PngMetadata2()
    {
        this.pngMap = new HashMap<>();
    }

    /**
     * Adds a new directory to the PNG collection, organised by its category. It also checks if the
     * directory contains XMP metadata within an iTXt chunk and updates the internal XMP-found flag
     * accordingly.
     *
     * @param directory
     *        the {@link PngDirectory} to add. Must not be null
     * @throws NullPointerException
     *         if the directory parameter is null
     */
    @Override
    public void addDirectory(PngDirectory directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
        }

        pngMap.putIfAbsent(directory.getCategory(), directory);

        if (xmpDir == null && directory.getCategory() == Category.TEXTUAL)
        {
            for (PngChunk chunk : directory)
            {
                if (chunk.getType() == ChunkType.iTXt && chunk.hasKeyword(TextKeyword.XML))
                {
                    xmpDir = readXmpData(chunk.getPayloadArray());

                    if (xmpDir != null && xmpDir.size() > 0)
                    {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Removes a directory from the collection based on its category.
     *
     * @param directory
     *        the {@link PngDirectory} whose category is used for removal. Must not be null
     * @return true if a directory was removed, otherwise false
     * 
     * @throws NullPointerException
     *         if the directory parameter is null.
     */
    @Override
    public boolean removeDirectory(PngDirectory directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
        }

        return pngMap.remove(directory.getCategory(), directory);
    }

    /**
     * Retrieves a {@link PngDirectory} associated with the specified chunk category.
     *
     * @param category
     *        the {@link ChunkType.Category} identifier
     * @return the corresponding {@link PngDirectory}, or {@code null} if not present.
     */
    @Override
    public PngDirectory getDirectory(ChunkType.Category category)
    {
        return pngMap.get(category);
    }

    /**
     * Retrieves a {@link PngDirectory} based on a tag that specifies a PNG chunk.
     *
     * @param tag
     *        the {@link Taggable} object, expected to be a {@link TagPngChunk}
     * @return the corresponding {@link PngDirectory}, or {@code null} if the tag is null or not a
     *         PNG chunk tag
     */
    @Override
    public PngDirectory getDirectory(Taggable tag)
    {
        if (tag != null && tag instanceof TagPngChunk)
        {
            return getDirectory(((TagPngChunk) tag).getChunkType().getCategory());
        }

        return null;
    }

    /**
     * Checks if the metadata collection is empty.
     *
     * @return true if the collection is empty, otherwise false
     */
    @Override
    public boolean isEmpty()
    {
        return pngMap.isEmpty();
    }

    /**
     * Checks if the PNG image contains any metadata directories.
     *
     * @return true if the collection is not empty, otherwise false
     */
    @Override
    public boolean hasMetadata()
    {
        return !isEmpty();
    }

    /**
     * Checks if the metadata contains a directory for textual chunks (tEXt, zTXt, iTXt).
     *
     * @return true if textual data directory is present, otherwise false
     */
    @Override
    public boolean hasTextualData()
    {
        return pngMap.containsKey(Category.TEXTUAL);
    }

    /**
     * Checks if the metadata contains an embedded EXIF profile (eXIf chunk).
     *
     * @return true if EXIF metadata is present, otherwise false
     */
    @Override
    public boolean hasExifData()
    {
        PngDirectory directory = pngMap.get(Category.MISC);

        if (directory != null)
        {
            return directory.getFirstChunk(ChunkType.eXIf).isPresent();
        }

        return false;
    }

    /**
     * Checks if the metadata contains an XMP directory. Note, XMP data is typically embedded in an
     * iTXt chunk.
     *
     * @return true if XMP metadata is present and non-empty, otherwise false
     */
    @Override
    public boolean hasXmpData()
    {
        return (xmpDir != null && xmpDir.size() > 0);
    }

    /**
     * Extracts the date from PNG metadata following a priority hierarchy:
     *
     * <ol>
     * <li>Embedded <b>EXIF</b> data (most accurate, from {@code DateTimeOriginal})</li>
     * <li>Embedded <b>XMP</b> data (reliable fallback, from {@code CreateDate} or
     * {@code DateTimeOriginal})</li>
     * <li>Generic <b>Textual</b> data with the 'Creation Time' keyword (final fallback)</li>
     * </ol>
     *
     * @return a {@link Date} object extracted from one of the metadata segments, otherwise
     *         {@code null} if not found.
     */
    @Override
    public Date extractDate()
    {
        if (hasExifData())
        {
            PngDirectory dir = getDirectory(TagPngChunk.CHUNK_TAG_EXIF_PROFILE);

            if (dir != null)
            {
                Optional<PngChunk> chunkOpt = dir.getFirstChunk(ChunkType.eXIf);

                if (chunkOpt.isPresent())
                {
                    ExifMetadata exif = TifParser.parseFromExifSegment(chunkOpt.get().getPayloadArray());
                    DirectoryIFD ifd = exif.getDirectory(DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);

                    // Null check for ifd added for safety during EXIF parsing
                    if (ifd != null && ifd.containsTag(EXIF_DATE_TIME_ORIGINAL))
                    {
                        return ifd.getDate(EXIF_DATE_TIME_ORIGINAL);
                    }
                }
            }
        }

        if (hasXmpData())
        {
            for (XMPCoreProperty prop : xmpDir)
            {
                String path = prop.getPath();
                String value = prop.getValue();

                if (path.contains(XMP_DATE_TIME_ORIGINAL) || path.contains(XMP_CREATE_DATE))
                {
                    Date date = DateParser.convertToDate(value);

                    if (date != null)
                    {
                        return date;
                    }
                }
            }
        }

        if (hasTextualData())
        {
            PngDirectory dir = getDirectory(ChunkType.Category.TEXTUAL);

            if (dir != null)
            {
                for (PngChunk chunk : dir)
                {
                    if (chunk.hasKeyword(TextKeyword.CREATE))
                    {
                        if (!chunk.getText().isEmpty())
                        {
                            return DateParser.convertToDate(chunk.getText());
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Returns an iterator over the {@link PngDirectory} values in this metadata collection.
     *
     * @return an {@link Iterator} over the directories
     */
    @Override
    public Iterator<PngDirectory> iterator()
    {
        return pngMap.values().iterator();
    }

    /**
     * Generates a string representation of the PNG metadata, listing all categories and their
     * associated directory contents.
     *
     * @return a formatted string containing all metadata details
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Category, PngDirectory> entry : pngMap.entrySet())
        {
            sb.append(entry.getKey()).append(System.lineSeparator());
            sb.append(entry.getValue()).append(System.lineSeparator());
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    /**
     * Reads metadata from the specified XMP segment into a directory containing every available XMP
     * property.
     *
     * @param data
     *        an array of bytes extracted from the XMP segment within the iTXt chunk
     * @return a newly created {@link XmpDirectory} containing a collection of XMP properties, or an
     *         empty one on failure
     */
    private XmpDirectory readXmpData(byte[] data)
    {
        XmpDirectory dir = new XmpDirectory();

        try
        {
            XmpHandler xmp = new XmpHandler(data);
            xmp.parseMetadata();

            for (XMPCoreProperty prop : xmp)
            {
                dir.add(prop);
            }
        }

        catch (ImageReadErrorException exc)
        {
            LOGGER.error("Failed to parse XMP segment from iTXt chunk", exc);
        }

        return dir;
    }
}