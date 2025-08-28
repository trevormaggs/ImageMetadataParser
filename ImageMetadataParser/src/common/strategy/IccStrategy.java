package common.strategy;

public interface IccStrategy<T> extends MetadataStrategy<T>
{
    boolean hasIccData();
}