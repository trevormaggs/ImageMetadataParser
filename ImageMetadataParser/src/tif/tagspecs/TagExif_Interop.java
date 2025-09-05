package tif.tagspecs;

import tif.DirectoryIdentifier;
import tif.TagHint;

public enum TagExif_Interop implements Taggable
{
    INTEROP_INDEX(0x0001, DirectoryIdentifier.EXIF_INTEROP_DIRECTORY),
    INTEROP_VERSION(0x0002, DirectoryIdentifier.EXIF_INTEROP_DIRECTORY),
    RELATED_IMAGE_FILE_FORMAT(0x1000, DirectoryIdentifier.EXIF_INTEROP_DIRECTORY),
    RELATED_IMAGE_WIDTH(0x1001, DirectoryIdentifier.EXIF_INTEROP_DIRECTORY),
    RELATED_IMAGE_HEIGHT(0x1002, DirectoryIdentifier.EXIF_INTEROP_DIRECTORY);

    private final int numID;
    private final DirectoryIdentifier directory;
    private final TagHint hint;

    private TagExif_Interop(int id, DirectoryIdentifier dir)
    {
        this(id, dir, TagHint.HINT_DEFAULT);
    }

    private TagExif_Interop(int id, DirectoryIdentifier dir, TagHint clue)
    {
        numID = id;
        directory = dir;
        hint = clue;
    }

    @Override
    public int getNumberID()
    {
        return numID;
    }

    @Override
    public DirectoryIdentifier getDirectoryType()
    {
        return directory;
    }

    @Override
    public TagHint getHint()
    {
        return hint;
    }
}