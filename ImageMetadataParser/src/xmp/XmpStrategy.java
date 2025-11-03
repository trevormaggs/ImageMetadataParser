package xmp;

import common.Directory;
import common.MetadataStrategy;

public interface XmpStrategy<D extends Directory<?>> extends MetadataStrategy<D>
{
    public boolean hasXmpData();
}