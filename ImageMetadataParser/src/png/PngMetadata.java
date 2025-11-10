package png;

import static tif.tagspecs.TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import common.DateParser;
import common.ImageReadErrorException;
import png.ChunkType.Category;
import tif.DirectoryIFD;
import tif.DirectoryIdentifier;
import tif.ExifMetadata;
import tif.TifParser;
import tif.tagspecs.TagPngChunk;
import tif.tagspecs.Taggable;
import xmp.XmpHandler;
import xmp.XmpHandler.XMPCoreProperty;

public class PngMetadata implements PngStrategy
{
    private final Map<Category, PngDirectory> pngMap;
    private boolean xmpFound;

    public PngMetadata()
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

        for (PngChunk chunk : directory)
        {
            /*
             * Check if the directory is TEXTUAL and contains
             * XMP metadata within one of the iTXt chunks.
             */
            if (chunk.getType() == ChunkType.iTXt && chunk.hasKeyword(TextKeyword.XML))
            {
                xmpFound = true;
                break;
            }
        }

        pngMap.putIfAbsent(directory.getCategory(), directory);
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
        return xmpFound;
    }

    public void getXmpData()
    {
        if (hasXmpData())
        {
            for (PngChunk type : getDirectory(ChunkType.Category.TEXTUAL))
            {
                if (type.getType() == ChunkType.iTXt && type.hasKeyword(TextKeyword.XML))
                {
                    // System.out.printf("%s%n", type);

                    try
                    {
                        XmpHandler xmp = new XmpHandler(type.getPayloadArray());
                        xmp.parseMetadata();

                        for (XMPCoreProperty prop : xmp)
                        {
                            System.out.printf("%-50s%-40s%-40s%n", prop.getNamespace(), prop.getPath(), prop.getValue());
                        }
                    }

                    catch (ImageReadErrorException e)
                    {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Extracts the date from PNG metadata. It first checks for embedded EXIF data, then falls back
     * to textual chunks, if available. Note, iTXT chunks may include embedded XMP data.
     *
     * @return a Date object extracted from one of the metadata segments, otherwise null if not
     *         found
     */
    @Override
    public Date extractDate()
    {
        getXmpData();

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
     * Extracts the date from PNG metadata following a priority hierarchy:
     * 1. Embedded **EXIF** data (most accurate)
     * 2. Embedded **XMP** data (reliable fallback)
     * 3. Generic **Textual** data with the 'Creation Time' keyword (final fallback)
     *
     * @return a Date object extracted from one of the metadata segments, otherwise null if not
     *         found
     */
    // @Override
    public Date extractDate2()
    {
        // 1. Check for Embedded EXIF Data (eXIf chunk)
        if (hasExifData())
        {
            // Use Category.MISC to retrieve the directory, which is cleaner than using the tag
            // object
            PngDirectory dir = getDirectory(Category.MISC);

            if (dir != null)
            {
                Optional<PngChunk> chunkOpt = dir.getFirstChunk(ChunkType.eXIf);

                if (chunkOpt.isPresent())
                {
                    // Parse the raw payload of the eXIf chunk as a TIFF EXIF segment
                    ExifMetadata exif = TifParser.parseFromExifSegment(chunkOpt.get().getPayloadArray());
                    DirectoryIFD ifd = exif.getDirectory(DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY);

                    if (ifd.containsTag(EXIF_DATE_TIME_ORIGINAL))
                    {
                        return ifd.getDate(EXIF_DATE_TIME_ORIGINAL);
                    }
                }
            }
        }

        // 2. Check for Embedded XMP Data (iTXt chunk with XMP keyword)
        // TODO: IMPLEMENTATION REQUIRED. If hasXmpData() is true, retrieve the XMP chunk,
        // parse the XML payload to get the date, and return it.
        // Example structure:
        /*
         * if (hasXmpData()) {
         * // PngDirectory textualDir = getDirectory(Category.TEXTUAL);
         * // ... logic to find XMP chunk and parse date ...
         * // return dateFromXmp;
         * }
         */

        // 3. Fallback to Generic Textual Data (e.g., tEXt or zTXt chunks with 'Creation Time')
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
                            // This is a last resort date, often less reliable than EXIF/XMP
                            return DateParser.convertToDate(chunk.getText());
                        }
                    }
                }
            }
        }

        return null;
    }
}