package png;

import static tif.tagspecs.TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import common.DateParser;
import png.ChunkType.Category;
import tif.DirectoryIFD;
import tif.DirectoryIdentifier;
import tif.ExifMetadata;
import tif.TifParser;
import tif.tagspecs.TagPngChunk;
import tif.tagspecs.Taggable;

public class PngMetadata implements PngStrategy
{
    private final Map<Category, PngDirectory> pngMap;

    public PngMetadata()
    {
        this.pngMap = new HashMap<>();
    }

    @Override
    public void addDirectory(PngDirectory directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
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
     * Checks if the metadata contains an XMP directory.
     *
     * Note: This method re-declares the default method defined in the parent interface to
     * polymorphically enable specialised behaviour.
     *
     * @return true if XMP metadata is present, otherwise false
     */
    @Override
    public boolean hasXmpData()
    {
        // TODO IMPLEMENT IT ASAP!
        return false;
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
                    if (chunk.hasKeywordPair(TextKeyword.CREATE))
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
}