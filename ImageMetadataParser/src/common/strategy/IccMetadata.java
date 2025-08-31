package common.strategy;

import java.util.Iterator;

public class IccMetadata implements IccStrategy<Object>
{
    @Override
    public void addDirectory(Object directory)
    {
    }

    @Override
    public boolean removeDirectory(Object directory)
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
    public boolean hasIccData()
    {
        return false;
    }

    @Override
    public Iterator<Object> iterator()
    {
        // TODO Auto-generated method stub
        return null;
    }
}