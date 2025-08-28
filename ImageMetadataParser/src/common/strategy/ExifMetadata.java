package common.strategy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import tif.DirectoryIFD;
import tif.DirectoryIdentifier;

public class ExifMetadata implements ExifStrategy<DirectoryIFD>
{
    private final Map<DirectoryIdentifier, DirectoryIFD> ifdMap;

    public ExifMetadata()
    {
        ifdMap = new HashMap<>();
    }

    /**
     * Checks whether a specific directory type is present.
     *
     * @param dir
     *        the directory identifier to check
     *
     * @return true if the directory is stored; otherwise false
     */
    public boolean isDirectoryPresent(DirectoryIdentifier dir)
    {
        return ifdMap.containsKey(dir);
    }

    @Override
    public void addDirectory(DirectoryIFD directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
        }

        ifdMap.put(directory.getDirectoryType(), directory);
    }

    @Override
    public boolean removeDirectory(DirectoryIFD directory)
    {
        if (directory == null)
        {
            throw new NullPointerException("Directory cannot be null");
        }

        return (ifdMap.remove(directory.getDirectoryType()) != null);

    }

    @Override
    public <U> DirectoryIFD getDirectory(U component)
    {
        if (component instanceof DirectoryIdentifier)
        {
            return ifdMap.get(component);
        }

        return null;
    }

    @Override
    public boolean hasExifData()
    {
        return isDirectoryPresent(DirectoryIdentifier.EXIF_DIRECTORY_SUBIFD);
    }

    @Override
    public boolean isEmpty()
    {
        return ifdMap.isEmpty();
    }

    @Override
    public boolean hasMetadata()
    {
        return (!isEmpty());
    }

    /**
     * Returns an iterator over all stored directories.
     *
     * @return an iterator of {@link DirectoryIFD} instances
     */
    @Override
    public Iterator<DirectoryIFD> iterator()
    {
        return ifdMap.values().iterator();
    }
}