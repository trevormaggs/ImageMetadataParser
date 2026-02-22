package tif.tagspecs;

import tif.DirectoryIdentifier;
import tif.TagHint;

public interface Taggable
{
    public int getNumberID();
    public TagHint getHint();
    public DirectoryIdentifier getDirectoryType();
    public String getDescription();

    default boolean isUnknown()
    {
        return false;
    }
}