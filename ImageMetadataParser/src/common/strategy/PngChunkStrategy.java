package common.strategy;

public interface PngChunkStrategy<T> extends MetadataStrategy<T>
{
    public boolean hasTextualData();
    public boolean hasExifData();
}