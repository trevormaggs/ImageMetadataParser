package png;

import static tif.tagspecs.TagIFD_Exif.*;
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
import xmp.XmpDirectory;
import xmp.XmpHandler;
import xmp.XmpProperty;
import xmp.XmpHandler.XmpRecord;

/**
 * Implements the {@link PngStrategy} interface to provide a comprehensive view and extraction
 * capability for metadata embedded within a PNG file. This class aggregates various PNG chunk
 * directories, managing and prioritising embedded metadata standards like EXIF and XMP for accurate
 * data extraction.
 *
 * <p>
 * It organises metadata into directories based on chunk category, for example: TEXTUAL, MISC, etc
 * and pro-actively parses embedded XMP data if an iTXt chunk is detected.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 12 November 2025
 */
public class PngMetadata implements PngStrategy
{
    private static final LogFactory LOGGER = LogFactory.getLogger(PngMetadata.class);
    private final Map<Category, PngDirectory> pngMap;
    private XmpDirectory xmpDir;

    /**
     * Constructs an empty {@code PngMetadata} object, initialising the internal map for storing PNG
     * directories.
     */
    public PngMetadata()
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
                if (chunk instanceof TextualChunk)
                {
                    TextualChunk textualChunk = (TextualChunk) chunk;

                    if (chunk.getType() == ChunkType.iTXt && textualChunk.hasKeyword(TextKeyword.XML))
                    {
                        xmpDir = readXmpData(chunk.getPayloadArray());
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

        return (pngMap.remove(directory.getCategory()) != null);
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
    @SuppressWarnings("deprecation")
    @Override
    public Date extractDate()
    {
        if (hasExifData())
        {
            PngDirectory dir = getDirectory(Category.MISC);

            if (dir != null)
            {
                Optional<PngChunk> chunkOpt = dir.getFirstChunk(ChunkType.eXIf);

                if (chunkOpt.isPresent())
                {
                    ExifMetadata exif = TifParser.parseFromExifSegment(chunkOpt.get().getPayloadArray());
                    DirectoryIFD ifd = exif.getDirectory(DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);

                    if (ifd != null && ifd.containsTag(EXIF_DATE_TIME_ORIGINAL))
                    {
                        // return ifd.getDate(EXIF_DATE_TIME_ORIGINAL);
                    }
                }
            }
        }

        if (hasXmpData())
        {
            Optional<String> opt = xmpDir.getValueByPath(XmpProperty.XPM_CREATEDATE);

            if (opt.isPresent())
            {
                Date date = DateParser.convertToDate(opt.get());

                if (date != null)
                {
                    System.out.printf("date %s\t%s%n", opt.get(), date);
                    return date;
                }
            }

            else
            {
                opt = xmpDir.getValueByName("xmp", "CreateDate");

                if (opt.isPresent())
                {
                    Date date = DateParser.convertToDate(opt.get());

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
                    if (chunk instanceof TextualChunk)
                    {
                        TextualChunk textualChunk = (TextualChunk) chunk;

                        if (textualChunk.hasKeyword(TextKeyword.CREATE))
                        {
                            String text = textualChunk.getText();

                            if (!text.isEmpty())
                            {
                                return DateParser.convertToDate(text);
                            }
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
     * @return a newly created {@link XmpDirectory} containing a collection of XMP properties, if
     *         present, otherwise null on failure
     */
    private XmpDirectory readXmpData(byte[] data)
    {
        try
        {
            XmpHandler xmp = new XmpHandler(data);

            if (xmp.parseMetadata())
            {
                XmpDirectory dir = new XmpDirectory();

                for (XmpRecord prop : xmp)
                {
                    dir.add(prop);
                }

                return dir;
            }
        }

        catch (ImageReadErrorException exc)
        {
            LOGGER.error("Failed to parse XMP segment from iTXt chunk", exc);
        }

        return null;
    }
}