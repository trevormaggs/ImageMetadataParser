package tif.tagspecs;

import java.util.HashMap;
import java.util.Map;
import logger.LogFactory;
import tif.DirectoryIdentifier;

/**
 * Central registry for resolving raw Tag IDs and Directory types into Taggable objects.
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

        /* Register all Taggable tag sets for efficient Tag ID search and capture */
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
     * Resolves a tag ID to a Taggable instance based on its parent directory.
     * 
     * @param id
     *        the tag ID read from the file
     * @param directory
     *        the IFD type currently being parsed
     * @return a known Taggable constant, or TagIFD_Unknown if not found
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

        // Fallback to your custom Unknown implementation
        return new TagIFD_Unknown(id, directory);
    }
}