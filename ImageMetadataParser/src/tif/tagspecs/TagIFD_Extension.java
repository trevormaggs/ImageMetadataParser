package tif.tagspecs;

import tif.DirectoryIdentifier;
import tif.TagHint;

public enum TagIFD_Extension implements Taggable
{
    IFD_BAD_FAX_LINES(0x0146, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Bad Fax Lines"),
    IFD_CLEAN_FAX_DATA(0x0147, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Clean Fax Data", TagHint.HINT_SHORT),
    IFD_CONSECUTIVE_BAD_FAX_LINES(0x0148, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Consecutive Bad Fax Lines"),
    IFD_IFDSUB_POINTER(0x014A, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "SubIFD DataOffset"),
    IFD_CLIP_PATH(0x0157, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Clip Path", TagHint.HINT_BYTE_STREAM),
    IFD_XCLIP_PATH_UNITS(0x0158, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "XClip Path Units"),
    IFD_YCLIP_PATH_UNITS(0x0159, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "YClip Path Units"),
    IFD_INDEXED(0x015A, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Indexed"),
    IFD_JPEG_TABLES(0x015B, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "JPEG Tables"),
    IFD_OPIPROXY(0x015F, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "OPI Proxy"),
    IFD_GLOBAL_PARAMETERS_IFD(0x0190, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Global Parameters IFD"),
    IFD_PROFILE_TYPE(0x0191, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Type"),
    IFD_FAX_PROFILE(0x0192, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Fax Profile"),
    IFD_CODING_METHODS(0x0193, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Coding Methods"),
    IFD_VERSION_YEAR(0x0194, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Version Year"),
    IFD_MODE_NUMBER(0x0195, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Mode Number"),
    IFD_DECODE(0x01B1, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Decode"),
    IFD_DEFAULT_IMAGE_COLOR(0x01B2, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Default Image Color"),
    IFD_STRIP_ROW_COUNTS(0x022F, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Strip Row Counts"),
    IFD_XML_PACKET(0x02BC, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "XMP Metadata", TagHint.HINT_BYTE_STREAM),
    IFD_IMAGE_ID(0x800D, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Image ID"),
    IFD_EXIF_POINTER(0x8769, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Exif Metadata"),
    IFD_GPS_INFO_POINTER(0x8825, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "GPS Metadata"),
    IFD_IMAGE_LAYER(0x87AC, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Image Layer");

    private final int numID;
    private final DirectoryIdentifier directory;
    private final TagHint hint;
    private final String desc;

    private TagIFD_Extension(int id, DirectoryIdentifier dir, String desc)
    {
        this(id, dir, desc, TagHint.HINT_DEFAULT);
    }

    private TagIFD_Extension(int id, DirectoryIdentifier dir, String desc, TagHint clue)
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