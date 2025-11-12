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

public class PngMetadata3 implements PngStrategy
{
    private static final LogFactory LOGGER = LogFactory.getLogger(PngMetadata3.class);
    private final Map<Category, PngDirectory> pngMap;
    private XmpDirectory xmpDir;

    public PngMetadata3()
    {
        this.pngMap = new HashMap<>();
    }

    /**
     * Adds a new directory to the PNG collection, organised by its category. It also checks if the
     * directory contains XMP metadata within an iTXt chunk and updates the internal XMP-found flag
     * accordingly.
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

    @Override
    public boolean removeDirectory(PngDirectory directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
        }

        return pngMap.remove(directory.getCategory(), directory);
    }

    @Override
    public PngDirectory getDirectory(ChunkType.Category category)
    {
        return pngMap.get(category);
    }

    @Override
    public PngDirectory getDirectory(Taggable tag)
    {
        if (tag != null && tag instanceof TagPngChunk)
        {
            return getDirectory(((TagPngChunk) tag).getChunkType().getCategory());
        }

        return null;
    }

    @Override
    public boolean isEmpty()
    {
        return pngMap.isEmpty();
    }

    @Override
    public boolean hasMetadata()
    {
        return !isEmpty();
    }

    @Override
    public boolean hasTextualData()
    {
        return pngMap.containsKey(Category.TEXTUAL);
    }

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
     * Checks if the metadata contains an XMP directory. Note, an iTXt chunk typically embeds XMP
     * data.
     *
     * @return true if XMP metadata is present, otherwise false
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
     * <li>Embedded <b>EXIF</b> data (most accurate)</li>
     * <li>Embedded <b>XMP</b> data (reliable fallback)</li>
     * <li>Generic <b>Textual</b> data with the 'Creation Time' keyword (final fallback)</li>
     * </ol>
     * 
     * Note, iTXT chunks may include embedded XMP data.
     * 
     * @return a Date object extracted from one of the metadata segments, otherwise null if not
     *         found
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

                    if (ifd.containsTag(EXIF_DATE_TIME_ORIGINAL))
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

                if (path.contains("CreateDate") || path.contains("DateTimeOriginal"))
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

    @Override
    public Iterator<PngDirectory> iterator()
    {
        return pngMap.values().iterator();
    }

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
     * @return a newly created XmpDirectory containing a collection of XMP properties
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