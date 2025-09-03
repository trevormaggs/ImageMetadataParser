package common;

public interface IccStrategy<T> extends MetadataStrategy<T>
{
    boolean hasIccData();
}