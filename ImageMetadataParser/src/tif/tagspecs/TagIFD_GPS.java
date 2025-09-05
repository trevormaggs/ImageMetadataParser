package tif.tagspecs;

import tif.DirectoryIdentifier;
import tif.TagHint;

public enum TagIFD_GPS implements Taggable
{
    GPS_VERSION_ID(0X0000, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_LATITUDE_REF(0X0001, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_LATITUDE(0X0002, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_LONGITUDE_REF(0X0003, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_LONGITUDE(0X0004, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_ALTITUDE_REF(0X0005, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_ALTITUDE(0X0006, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_TIME_STAMP(0X0007, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_SATELLITES(0X0008, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_STATUS(0X0009, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_MEASURE_MODE(0X000A, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_DOP(0X000B, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_SPEED_REF(0X000C, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_SPEED(0X000D, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_TRACK_REF(0X000E, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_TRACK(0X000F, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_IMG_DIRECTION_REF(0X0010, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_IMG_DIRECTION(0X0011, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_MAP_DATUM(0X0012, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_DEST_LATITUDE_REF(0X0013, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_DEST_LATITUDE(0X0014, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_DEST_LONGITUDE_REF(0X0015, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_DEST_LONGITUDE(0X0016, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_DEST_BEARING_REF(0X0017, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_DEST_BEARING(0X0018, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_DEST_DISTANCE_REF(0X0019, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_DEST_DISTANCE(0X001A, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_PROCESSING_METHOD(0X001B, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_AREA_INFORMATION(0X001C, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_DATE_STAMP(0X001D, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_DIFFERENTIAL(0X001E, DirectoryIdentifier.IFD_GPS_DIRECTORY),
    GPS_HPOSITIONING_ERROR(0X001F, DirectoryIdentifier.IFD_GPS_DIRECTORY);

    private final int numID;
    private final DirectoryIdentifier directory;
    private final TagHint hint;

    private TagIFD_GPS(int id, DirectoryIdentifier dir)
    {
        this(id, dir, TagHint.HINT_DEFAULT);
    }

    private TagIFD_GPS(int id, DirectoryIdentifier dir, TagHint clue)
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