package tif.tagspecs;

import static tif.DirectoryIdentifier.*;
import tif.DirectoryIdentifier;
import tif.TagHint;

public enum TagIFD_GPS implements Taggable
{
    GPS_VERSION_ID(0X0000, IFD_GPS_DIRECTORY, "GPS Tag Version"),
    GPS_LATITUDE_REF(0X0001, IFD_GPS_DIRECTORY, "North or South Latitude"),
    GPS_LATITUDE(0X0002, IFD_GPS_DIRECTORY, "Latitude (Degrees, Minutes, Seconds)"),
    GPS_LONGITUDE_REF(0X0003, IFD_GPS_DIRECTORY, "East or West Longitude"),
    GPS_LONGITUDE(0X0004, IFD_GPS_DIRECTORY, "Longitude (Degrees, Minutes, Seconds)"),
    GPS_ALTITUDE_REF(0X0005, IFD_GPS_DIRECTORY, "Altitude Reference (Sea Level)"),
    GPS_ALTITUDE(0X0006, IFD_GPS_DIRECTORY, "Altitude (Meters)"),
    GPS_TIME_STAMP(0X0007, IFD_GPS_DIRECTORY, "GPS Time (Atomic Clock)", TagHint.HINT_DATE),
    GPS_SATELLITES(0X0008, IFD_GPS_DIRECTORY, "GPS Satellites Used for Measurement"),
    GPS_STATUS(0X0009, IFD_GPS_DIRECTORY, "GPS Receiver Status"),
    GPS_MEASURE_MODE(0X000A, IFD_GPS_DIRECTORY, "GPS Measurement Mode"),
    GPS_DOP(0X000B, IFD_GPS_DIRECTORY, "Measurement Precision (DOP)"),
    GPS_SPEED_REF(0X000C, IFD_GPS_DIRECTORY, "Speed Unit (K, M, N)"),
    GPS_SPEED(0X000D, IFD_GPS_DIRECTORY, "Speed of GPS Receiver"),
    GPS_TRACK_REF(0X000E, IFD_GPS_DIRECTORY, "Reference for Direction of Movement"),
    GPS_TRACK(0X000F, IFD_GPS_DIRECTORY, "Direction of Movement"),
    GPS_IMG_DIRECTION_REF(0X0010, IFD_GPS_DIRECTORY, "Reference for Direction of Image"),
    GPS_IMG_DIRECTION(0X0011, IFD_GPS_DIRECTORY, "Direction of Image"),
    GPS_MAP_DATUM(0X0012, IFD_GPS_DIRECTORY, "Geodetic Survey Data Used"),
    GPS_DEST_LATITUDE_REF(0X0013, IFD_GPS_DIRECTORY, "Reference for Latitude of Destination"),
    GPS_DEST_LATITUDE(0X0014, IFD_GPS_DIRECTORY, "Latitude of Destination"),
    GPS_DEST_LONGITUDE_REF(0X0015, IFD_GPS_DIRECTORY, "Reference for Longitude of Destination"),
    GPS_DEST_LONGITUDE(0X0016, IFD_GPS_DIRECTORY, "Longitude of Destination"),
    GPS_DEST_BEARING_REF(0X0017, IFD_GPS_DIRECTORY, "Reference for Bearing of Destination"),
    GPS_DEST_BEARING(0X0018, IFD_GPS_DIRECTORY, "Bearing of Destination"),
    GPS_DEST_DISTANCE_REF(0X0019, IFD_GPS_DIRECTORY, "Reference for Distance to Destination"),
    GPS_DEST_DISTANCE(0X001A, IFD_GPS_DIRECTORY, "Distance to Destination"),
    GPS_PROCESSING_METHOD(0X001B, IFD_GPS_DIRECTORY, "Name of GPS Processing Method", TagHint.HINT_ENCODED_STRING),
    GPS_AREA_INFORMATION(0X001C, IFD_GPS_DIRECTORY, "Name of GPS Area"),
    GPS_DATE_STAMP(0X001D, IFD_GPS_DIRECTORY, "GPS Date Stamp", TagHint.HINT_DATE),
    GPS_DIFFERENTIAL(0X001E, IFD_GPS_DIRECTORY, "GPS Differential Correction"),
    GPS_HPOSITIONING_ERROR(0X001F, IFD_GPS_DIRECTORY, "Horizontal Positioning Error (Metres)");

    private final int numID;
    private final DirectoryIdentifier directory;
    private final TagHint hint;
    private final String desc;

    private TagIFD_GPS(int id, DirectoryIdentifier dir, String desc)
    {
        this(id, dir, desc, TagHint.HINT_DEFAULT);
    }

    private TagIFD_GPS(int id, DirectoryIdentifier dir, String desc, TagHint clue)
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