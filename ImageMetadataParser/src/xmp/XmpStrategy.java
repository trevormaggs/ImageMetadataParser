package xmp;

import common.MetadataStrategy;

public interface XmpStrategy<T> extends MetadataStrategy<T>
{
    boolean hasXmpData();
}