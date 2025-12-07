package png;

import static tif.tagspecs.TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL;
import java.nio.ByteOrder;
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
import tif.TifMetadata;
import tif.TifParser;
import xmp.XmpDirectory;
import xmp.XmpHandler;
import xmp.XmpProperty;

/**
 * Implements the {@link PngMetadataStrategy} interface to provide a comprehensive view and
 * extraction
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
public class PngMetadata implements PngMetadataStrategy
{
    private static final LogFactory LOGGER = LogFactory.getLogger(PngMetadata.class);
    private final Map<Category, PngDirectory> pngMap;
    private ByteOrder byteOrder;
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
     * Constructs a new {@code PngMetadata} object, respecting the byte order for correctly
     * interpreting the raw data.
     * 
     * @param byteOrder
     *        the byte order either {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     * 
     * @throws NullPointerException
     *         if the specified byte order is null
     */
    public PngMetadata(ByteOrder byteOrder)
    {
        this();

        if (byteOrder == null)
        {
            throw new NullPointerException("ByteOrder is null");
        }

        this.byteOrder = byteOrder;
    }

    /**
     * Returns the byte order, indicating how data values will be interpreted correctly.
     *
     * @return either {@code ByteOrder.BIG_ENDIAN} or {@code ByteOrder.LITTLE_ENDIAN}
     */
    @Override
    public ByteOrder getByteOrder()
    {
        return byteOrder;
    }

    /**
     * Adds a new directory to the PNG collection, organised by its category.
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
    }

    /**
     * Reads metadata from the specified XMP payload into a directory containing every available XMP
     * property.
     *
     * @param payload
     *        an array of bytes extracted from the XMP segment within the iTXt chunk
     */
    @Override
    public void addXmpDirectory(byte[] payload)
    {
        try
        {
            XmpHandler xmp = new XmpHandler(payload);

            if (xmp.parseMetadata())
            {
                this.xmpDir = xmp.getXmpDirectory();
            }
        }

        catch (ImageReadErrorException exc)
        {
            LOGGER.error("Failed to parse XMP segment from iTXt chunk", exc);
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
     * Checks if the metadata collection is empty.
     *
     * @return true if the collection is empty, otherwise false
     */
    @Override
    public boolean isEmpty()
    {
        return !hasMetadata();
    }

    /**
     * Checks if the PNG image contains any metadata directories.
     *
     * @return true if the collection is not empty, otherwise false
     */
    @Override
    public boolean hasMetadata()
    {
        return (!pngMap.isEmpty() || hasXmpData());
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
     * Retrieves a {@link PngDirectory} associated with the specified chunk category.
     *
     * @param category
     *        the {@link ChunkType.Category} identifier
     * @return the corresponding {@link PngDirectory}, or null if not present.
     */
    @Override
    public PngDirectory getDirectory(ChunkType.Category category)
    {
        return pngMap.get(category);
    }

    /**
     * Returns an {@link XmpDirectory} only if there exists XMP metadata.
     *
     * @return an instance of the XmpDirectory if present, otherwise null if none was decoded. To
     *         avoid processing null, checking with the {@link #hasXmpData()} method first is
     *         recommended
     */
    @Override
    public XmpDirectory getXmpDirectory()
    {
        return xmpDir;
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
            return (directory.getFirstChunk(ChunkType.eXIf) != null);
        }

        return false;
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
     * <p>
     * Extracts the date from PNG metadata following a priority hierarchy:
     * </p>
     *
     * <ol>
     * <li>Embedded <b>EXIF</b> data (most accurate, from {@code DateTimeOriginal})</li>
     * <li>Embedded <b>XMP</b> data (reliable fallback, from {@code CreateDate} or
     * {@code DateTimeOriginal})</li>
     * <li>Generic <b>Textual</b> data with the 'Creation Time' keyword (final fallback)</li>
     * </ol>
     *
     * @return a {@link Date} object extracted from one of the metadata segments, otherwise null if
     *         not found
     */
    @Override
    public Date extractDate()
    {
        if (hasExifData())
        {
            try
            {
                PngDirectory dir = getDirectory(Category.MISC);
                PngChunk chunk = dir.getFirstChunk(ChunkType.eXIf);
                TifMetadata exif = TifParser.parseFromIfdSegment(chunk.getPayloadArray());
                DirectoryIFD ifd = exif.getDirectory(DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);

                if (ifd != null && ifd.contains(EXIF_DATE_TIME_ORIGINAL))
                {
                    return ifd.getDate(EXIF_DATE_TIME_ORIGINAL);
                }
            }

            catch (RuntimeException exc)
            {
                LOGGER.warn("Failed to parse EXIF data from eXIf chunk. Falling back to XMP");
            }
        }

        if (hasXmpData())
        {
            Optional<String> opt = xmpDir.getValueByPath(XmpProperty.EXIF_DATE_TIME_ORIGINAL);

            if (opt.isPresent())
            {
                Date date = DateParser.convertToDate(opt.get());

                if (date != null)
                {
                    return date;
                }
            }

            opt = xmpDir.getValueByPath(XmpProperty.XMP_CREATEDATE);

            if (opt.isPresent())
            {
                Date date = DateParser.convertToDate(opt.get());

                if (date != null)
                {
                    return date;
                }
            }
        }

        if (hasTextualData())
        {
            PngDirectory dir = getDirectory(ChunkType.Category.TEXTUAL);

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

        return null;
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
}