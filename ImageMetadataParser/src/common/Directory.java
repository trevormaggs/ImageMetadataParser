package common;

import tif.tagspecs.Taggable;

public interface Directory<T> extends Iterable<T>
{
    public boolean contains(Taggable tag);
    public T findEntryByID(int tagid);
    public int length();
    public boolean add(T entry);
    public boolean remove(T entry);
}