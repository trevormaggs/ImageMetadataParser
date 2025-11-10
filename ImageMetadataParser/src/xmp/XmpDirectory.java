package xmp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import common.Directory;
import xmp.XmpHandler.XMPCoreProperty;

/**
 * Encapsulates a collection of {@link XMPCoreProperty} objects to manage XMP data.
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 10 November 2025
 */
public class XmpDirectory implements Directory<XMPCoreProperty>
{
    private final List<XMPCoreProperty> props;

    /**
     * Constructs a new {@code XmpDirectory} to manage a collection of {@link XMPCoreProperty}
     * properties.
     */
    public XmpDirectory()
    {
        this.props = new ArrayList<>();
    }

    @Override
    public void add(XMPCoreProperty entry)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean remove(XMPCoreProperty entry)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean contains(XMPCoreProperty entry)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public int size()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean isEmpty()
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Iterator<XMPCoreProperty> iterator()
    {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Returns a string representation of this directory, which is the concatenation of the string
     * representations of all contained {@link XMPCoreProperty} objects, each on a new line.
     *
     * @return a multi-line string representing the properties in the directory
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        for (XMPCoreProperty prop : props)
        {
            sb.append(prop).append(System.lineSeparator());
        }

        return sb.toString();
    }
}