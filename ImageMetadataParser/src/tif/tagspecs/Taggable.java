package tif.tagspecs;

import tif.DirectoryIdentifier;
import tif.TagHint;

public interface Taggable
{
    public int getNumberID();
    public TagHint getHint();
    public DirectoryIdentifier getDirectoryType();

    default boolean isUnknown()
    {
        return false;
    }
}