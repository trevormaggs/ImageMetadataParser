package common.strategy;

public interface MetadataStrategy<T>
{
    public void addDirectory(T directory);
    public boolean removeDirectory(T directory);
    public <U> T getDirectory(U component);
    public boolean isEmpty();
    public boolean hasMetadata();
}
