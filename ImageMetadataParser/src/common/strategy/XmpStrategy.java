package common.strategy;

public interface XmpStrategy<T> extends MetadataStrategy<T>
{
    boolean hasXmpData();
}