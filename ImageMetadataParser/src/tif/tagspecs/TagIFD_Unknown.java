package tif.tagspecs;

import tif.DirectoryIdentifier;
import tif.TagHint;

public class TagIFD_Unknown implements Taggable
{
    private final int id;
    private final DirectoryIdentifier directory;

    public TagIFD_Unknown(int id, DirectoryIdentifier directory)
    {
        this.id = id;
        this.directory = directory;
    }

    @Override
    public int getNumberID()
    {
        return id;
    }

    @Override
    public TagHint getHint()
    {
        return TagHint.HINT_DEFAULT;
    }

    @Override
    public DirectoryIdentifier getDirectoryType()
    {
        return directory;
    }

    @Override
    public boolean isUnknown()
    {
        return true;
    }
}