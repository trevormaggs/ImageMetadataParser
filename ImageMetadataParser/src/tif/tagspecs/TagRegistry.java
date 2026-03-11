package tif.tagspecs;

import java.util.HashMap;
import java.util.Map;
import logger.LogFactory;
import tif.DirectoryIdentifier;

/**
 * A central repository for mapping numerical Tag IDs and their associated
 * {@link DirectoryIdentifier} to {@link Taggable} enum constants.
 * 
 * <p>
 * This registry handles the contextual nature of the TIFF specification, where the same Tag ID may
 * represent different metadata depending on the directory (IFD) it resides in. For example, ID
 * {@code 0x0001} represents <i>GPSLatitudeRef</i> in a GPS IFD, but <i>InteropIndex</i> in an
 * Interoperability IFD.
 * </p>
 * 
 * <p>
 * The registry also implements "Main Chain" normalisation, mapping all sequential Image File
 * Directories (IFD0, IFD1, etc.) to the {@code IFD_ROOT_DIRECTORY} bucket for baseline and
 * extension tag resolution.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.1
 */
public final class TagRegistry
{
    private static final LogFactory LOGGER = LogFactory.getLogger(TagRegistry.class);
    private static final Map<DirectoryIdentifier, Map<Integer, Taggable>> TAG_REGISTRY = new HashMap<>();

    static
    {
        for (DirectoryIdentifier dir : DirectoryIdentifier.values())
        {
            TAG_REGISTRY.put(dir, new HashMap<>());
        }

        /*
         * Populate the registry with all supported tag sets. Note: Tag sets
         * are registered based on their internal DirectoryIdentifier.
         */
        register(TagIFD_Baseline.values());
        register(TagIFD_Extension.values());
        register(TagIFD_Exif.values());
        register(TagIFD_GPS.values());
        register(TagIFD_Private.values());
        register(TagExif_Interop.values());

        if (LOGGER.isDebugEnabled())
        {
            for (Map.Entry<DirectoryIdentifier, Map<Integer, Taggable>> entry : TAG_REGISTRY.entrySet())
            {
                for (Taggable tag : entry.getValue().values())
                {
                    LOGGER.debug(String.format("Registered: %-10s | 0x%04X | %s", entry.getKey(), tag.getNumberID(), tag));
                }
            }
        }
    }

    /**
     * Registers an array of {@link Taggable} constants into the global registry.
     *
     * <p>
     * Maps each tag to its respective directory-specific map based on the associated
     * {@link DirectoryIdentifier}.
     * </p>
     *
     * @param tags
     *        the array of tags to register
     */
    private static void register(Taggable[] tags)
    {
        for (Taggable tag : tags)
        {
            Map<Integer, Taggable> dirMap = TAG_REGISTRY.get(tag.getDirectoryType());

            if (dirMap != null)
            {
                dirMap.put(tag.getNumberID(), tag);
            }
        }
    }

    /**
     * Resolves a numerical tag ID to its corresponding {@code Taggable} definition within the
     * context of a specific directory.
     * 
     * <p>
     * If the specified {@code directory} is part of the <i>Main Chain (IFD0-IFD3)</i>, the lookup
     * is automatically redirected to use {@code IFD_ROOT_DIRECTORY} definitions.
     * </p>
     * 
     * @param id
     *        the unsigned 16-bit Tag ID read from the TIFF structure
     * @param directory
     *        the directory context (IFD) where the tag was encountered
     * @return the associated {@code Taggable} constant, otherwise a {@link TagIFD_Unknown} instance
     *         representing the undefined tag
     */
    public static Taggable resolve(int id, DirectoryIdentifier directory)
    {
        DirectoryIdentifier lookupKey = directory.isMainChain() ? DirectoryIdentifier.IFD_ROOT_DIRECTORY : directory;
        Map<Integer, Taggable> directoryMap = TAG_REGISTRY.get(lookupKey);

        if (directoryMap != null)
        {
            Taggable tag = directoryMap.get(id);

            if (tag != null)
            {
                return tag;
            }
        }

        // Fallback
        return new TagIFD_Unknown(id, directory);
    }
}