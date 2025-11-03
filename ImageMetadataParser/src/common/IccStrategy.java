package common;

public interface IccStrategy<D extends Directory<?>> extends MetadataStrategy<D>
{
    public boolean hasIccData();
}