package common.strategy;

public interface MetadataStrategy<T> extends Iterable<T>
{
    public  void addDirectory(T directory);
    public  boolean removeDirectory(T directory);
    public  boolean isEmpty();
    public boolean hasMetadata();
}
