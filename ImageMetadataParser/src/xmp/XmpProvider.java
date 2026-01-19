package xmp;

import common.Directory;
import common.Metadata;

public interface XmpProvider<D extends Directory<?>> extends Metadata<D>
{
    public boolean hasXmpData();
}