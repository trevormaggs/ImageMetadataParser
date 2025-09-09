package common;

public interface Directory<T> extends Iterable<T>
{
    public boolean add(T entry);
    public boolean contains(T entry);
    public int size();
    public boolean isEmpty();
}