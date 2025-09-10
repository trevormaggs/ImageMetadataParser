package png;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import common.BaseMetadata;
import common.Metadata;
import png.ChunkType.Category;
import tif.MetadataTIF;
import tif.TagEntries.Taggable;

/**
 * A concrete metadata container for PNG files, supporting both embedded Exif data (from eXIf
 * chunks) and textual data (from tEXt, zTXt, iTXt chunks). This class serves as a composite
 * component in the composite design pattern for metadata leaves.
 *
 * @param <T>
 *        the type of metadata unit stored in the PNG structure
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class MetadataPNG<T extends BaseMetadata> implements Metadata<T>
{
    private final List<T> components;

    /**
     * Constructs an empty PNG metadata container.
     */
    public MetadataPNG()
    {
        this.components = new ArrayList<>();

        /*
         * Some examples of exiftool usages
         *
         * exiftool -time:all -a -G0:1 -s testPNGimage.png
         * exiftool.exe -overwrite_original -alldates="2012:10:07 11:15:45" testPNGimage.png
         * exiftool.exe "-FileModifyDate<PNG:CreationTime" testPNGimage.png
         *
         * exiftool "-PNG:CreationTime=2015:07:14 01:15:27" testPNGimage.png
         * exiftool -filemodifydate="2024:08:10 00:00:00" -createdate="2024:08:10 00:00:00"
         * "-PNG:CreationTime<FileModifyDate" testPNGimage.png
         */
    }

    /**
     * Checks if this PNG metadata contains textual data in its directories. This refers to tEXt,
     * zTXt, or iTXt chunks within the PNG file.
     *
     * @return true if any {@link ChunkDirectory} of Category.TEXTUAL is found
     */
    public boolean hasTextualData()
    {
        for (T dir : components)
        {
            if (dir instanceof ChunkDirectory)
            {
                ChunkDirectory cd = (ChunkDirectory) dir;

                if (cd.getDirectoryCategory() == Category.TEXTUAL)
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Adds a metadata directory to this PNG metadata container.
     *
     * @param directory
     *        the directory to add
     */
    @Override
    public void addDirectory(T directory)
    {
        components.add(directory);
    }

    /**
     * Retrieves a metadata directory based on the given component key.
     *
     * If multiple matches exist, the first encountered match is returned.
     *
     * <p>
     * The match strategy depends on the component type:
     * </p>
     *
     * <ul>
     * <li>{@link Taggable} → finds a {@link ChunkDirectory} containing the tag</li>
     * <li>{@link TextKeyword} → finds a {@link ChunkDirectory} containing the keyword</li>
     * <li>Other → matches the component class type directly</li>
     * </ul>
     *
     * @param <U>
     *        the type of the component being used to look up the directory
     * @param component
     *        the lookup key or element
     *
     * @return a matching metadata directory, or null if not found
     */
    @Override
    public <U> T getDirectory(U component)
    {
        if (component instanceof ChunkType.Category)
        {
            ChunkType.Category category = (ChunkType.Category) component;

            for (T dir : components)
            {
                if (dir instanceof ChunkDirectory && ((ChunkDirectory) dir).getDirectoryCategory() == category)
                {
                    return dir;
                }
            }
        }

        else if (component instanceof Taggable)
        {
            /* Using TagPngChunk enum constants */
            Taggable tag = (Taggable) component;

            for (T dir : components)
            {
                if (dir instanceof ChunkDirectory && ((ChunkDirectory) dir).contains(tag))
                {
                    return dir;
                }
            }
        }

        else if (component instanceof TextKeyword)
        {
            /* Using TextKeyword class */
            TextKeyword keyword = (TextKeyword) component;

            for (T dir : components)
            {
                if (dir instanceof ChunkDirectory && ((ChunkDirectory) dir).existsTextualKeyword(keyword))
                {
                    return dir;
                }
            }
        }

        else if (component instanceof Class<?>)
        {
            /* Using class resources, i.e. MetadataTIF.class */
            Class<?> clazz = (Class<?>) component;

            for (T dir : components)
            {
                if (clazz.isInstance(dir))
                {
                    return dir;
                }
            }
        }

        return null;
    }

    /**
     * Checks if there are no metadata directories.
     *
     * @return true if this metadata container is empty
     */
    @Override
    public boolean isEmpty()
    {
        return components.isEmpty();
    }

    /**
     * Checks whether any metadata exists.
     *
     * @return true if at least one metadata directory is present
     */
    @Override
    public boolean hasMetadata()
    {
        return !isEmpty();
    }

    /**
     * Determines if any {@link MetadataTIF} directory contains embedded Exif data.
     *
     * @return true if Exif data exists
     */
    @Override
    public boolean hasExifData()
    {
        for (T dir : components)
        {
            if (dir instanceof MetadataTIF)
            {
                return ((MetadataTIF) dir).hasExifData();
            }
        }

        return false;
    }

    /**
     * Returns an iterator over all metadata directories.
     *
     * @return an iterator over metadata units
     */
    @Override
    public Iterator<T> iterator()
    {
        return components.iterator();
    }

    /**
     * Returns a raw string representation of all chunks in all directories.
     *
     * @return a line-by-line listing of all chunk contents
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        for (T dir : this)
        {
            if (dir instanceof ChunkDirectory)
            {
                ChunkDirectory chunks = ((ChunkDirectory) dir);

                for (PngChunk chunk : chunks)
                {
                    sb.append(chunk).append(System.lineSeparator());
                }
            }
        }

        return sb.toString();
    }
}