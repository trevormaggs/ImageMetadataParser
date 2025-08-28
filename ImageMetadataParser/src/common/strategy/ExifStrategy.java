package common.strategy;

public interface ExifStrategy<T> extends MetadataStrategy<T>, Iterable<T>
{
    boolean hasExifData();
}