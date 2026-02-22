package tif.tagspecs;

import tif.DirectoryIdentifier;
import tif.TagHint;

public enum TagExif_Interop implements Taggable
{
    INTEROP_INDEX(0x0001, DirectoryIdentifier.EXIF_INTEROP_DIRECTORY, "Interop Index"),
    INTEROP_VERSION(0x0002, DirectoryIdentifier.EXIF_INTEROP_DIRECTORY, "Interop Version"),
    INTEROP_RELATED_IMAGE_FILE_FORMAT(0x1000, DirectoryIdentifier.EXIF_INTEROP_DIRECTORY, "Related Image File Format"),
    INTEROP_RELATED_IMAGE_WIDTH(0x1001, DirectoryIdentifier.EXIF_INTEROP_DIRECTORY, "Related Image Width"),
    INTEROP_RELATED_IMAGE_HEIGHT(0x1002, DirectoryIdentifier.EXIF_INTEROP_DIRECTORY, "Related Image Height");

    private final int numID;
    private final DirectoryIdentifier directory;
    private final TagHint hint;
    private final String desc;

    private TagExif_Interop(int id, DirectoryIdentifier dir, String desc)
    {
        this(id, dir, desc, TagHint.HINT_DEFAULT);
    }

    private TagExif_Interop(int id, DirectoryIdentifier dir, String desc, TagHint clue)
    {
        this.numID = id;
        this.directory = dir;
        this.desc = desc;
        this.hint = clue;
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

    @Override
    public String getDescription()
    {
        return desc;
    }
}