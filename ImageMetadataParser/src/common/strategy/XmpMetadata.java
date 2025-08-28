package common.strategy;

public class XmpMetadata implements XmpStrategy<Object>
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
    public <U> Object getDirectory(U compoment)
    {
        // TODO Auto-generated method stub
        return null;
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
}