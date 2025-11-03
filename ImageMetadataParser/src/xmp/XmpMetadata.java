package xmp;

import java.util.Iterator;
import tif.DirectoryIFD;

public class XmpMetadata implements XmpStrategy<DirectoryIFD>
{
    @Override
    public void addDirectory(DirectoryIFD directory)
    {
    }

    @Override
    public boolean removeDirectory(DirectoryIFD directory)
    {
        return false;
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public boolean hasMetadata()
    {
        return false;
    }

    @Override
    public boolean hasXmpData()
    {
        return false;
    }

    @Override
    public Iterator<DirectoryIFD> iterator()
    {
        // TODO Auto-generated method stub
        return null;
    }
}