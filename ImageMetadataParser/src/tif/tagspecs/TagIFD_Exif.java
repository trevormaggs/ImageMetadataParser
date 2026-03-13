package tif.tagspecs;

import tif.DirectoryIdentifier;
import tif.TagHint;

public enum TagIFD_Exif implements Taggable
{
    /* --- Capture & Exposure Settings --- */
    EXIF_EXPOSURE_TIME(0x829A, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Exposure Time", TagHint.HINT_RATIONAL),
    EXIF_FNUMBER(0x829D, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "F-Number", TagHint.HINT_RATIONAL),
    EXIF_EXPOSURE_PROGRAM(0x8822, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Exposure Program", TagHint.HINT_DEFAULT),
    EXIF_SPECTRAL_SENSITIVITY(0x8824, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Spectral Sensitivity", TagHint.HINT_DEFAULT),
    EXIF_ISOSPEED_RATINGS(0x8827, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "ISO Speed Ratings", TagHint.HINT_DEFAULT),
    EXIF_OECF(0x8828, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Optoelectronic Conversion Function", TagHint.HINT_DEFAULT),
    EXIF_SENSITIVITY_TYPE(0x8830, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Sensitivity Type", TagHint.HINT_DEFAULT),
    EXIF_STANDARD_OUTPUT_SENSITIVITY(0x8831, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Standard Output Sensitivity", TagHint.HINT_DEFAULT),
    EXIF_RECOMMENDED_EXPOSURE_INDEX(0x8832, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Recommended Exposure Index", TagHint.HINT_DEFAULT),
    EXIF_ISOSPEED(0x8833, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "ISO Speed", TagHint.HINT_DEFAULT),
    EXIF_ISOSPEED_LATITUDEYYY(0x8834, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "ISO Speed Latitude yyy", TagHint.HINT_DEFAULT),
    EXIF_ISOSPEED_LATITUDEZZZ(0x8835, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "ISO Speed Latitude zzz", TagHint.HINT_DEFAULT),
    EXIF_SHUTTER_SPEED_VALUE(0x9201, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Shutter Speed Value", TagHint.HINT_DEFAULT),
    EXIF_APERTURE_VALUE(0x9202, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Aperture Value", TagHint.HINT_DEFAULT),
    EXIF_BRIGHTNESS_VALUE(0x9203, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Brightness Value", TagHint.HINT_DEFAULT),
    EXIF_EXPOSURE_BIAS_VALUE(0x9204, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Exposure Bias Value", TagHint.HINT_DEFAULT),
    EXIF_MAX_APERTURE_VALUE(0x9205, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Max Aperture Value", TagHint.HINT_DEFAULT),
    EXIF_METERING_MODE(0x9207, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Metering Mode", TagHint.HINT_DEFAULT),
    EXIF_LIGHT_SOURCE(0x9208, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Light Source", TagHint.HINT_DEFAULT),
    EXIF_FLASH(0x9209, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Flash", TagHint.HINT_DEFAULT),
    EXIF_FOCAL_LENGTH(0x920A, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Focal Length", TagHint.HINT_DEFAULT),
    EXIF_FLASH_ENERGY(0xA20B, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Flash Energy", TagHint.HINT_DEFAULT),
    EXIF_EXPOSURE_INDEX(0xA215, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Exposure Index", TagHint.HINT_DEFAULT),
    EXIF_EXPOSURE_MODE(0xA402, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Exposure Mode", TagHint.HINT_DEFAULT),
    EXIF_WHITE_BALANCE(0xA403, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "White Balance", TagHint.HINT_DEFAULT),
    EXIF_FOCAL_LENGTH_IN_35MM_FILM(0xA405, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Focal Length In 35mm Film", TagHint.HINT_DEFAULT),
    EXIF_SCENE_CAPTURE_TYPE(0xA406, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Scene Capture Type", TagHint.HINT_DEFAULT),
    EXIF_GAIN_CONTROL(0xA407, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Gain Control", TagHint.HINT_DEFAULT),

    /* --- Date & Time --- */
    EXIF_DATE_TIME_ORIGINAL(0x9003, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Date/Time Original", TagHint.HINT_DATE),
    EXIF_DATE_TIME_DIGITIZED(0x9004, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Date/Time Digitized", TagHint.HINT_DATE),
    EXIF_OFFSET_TIME(0x9010, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Offset Time (Time Zone)", TagHint.HINT_DEFAULT),
    EXIF_OFFSET_TIME_ORIGINAL(0x9011, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Offset Time Original", TagHint.HINT_DEFAULT),
    EXIF_OFFSET_TIME_DIGITIZED(0x9012, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Offset Time Digitized", TagHint.HINT_DEFAULT),
    EXIF_SUBSEC_TIME(0x9290, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Sub-second Time", TagHint.HINT_STRING),
    EXIF_SUBSEC_TIME_ORIGINAL(0x9291, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Sub-second Time Original", TagHint.HINT_STRING),
    EXIF_SUBSEC_TIME_DIGITIZED(0x9292, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Sub-second Time Digitized", TagHint.HINT_STRING),

    /* --- Image Characterization --- */
    EXIF_COLOR_SPACE(0xA001, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Color Space", TagHint.HINT_DEFAULT),
    EXIF_PIXEL_XDIMENSION(0xA002, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Pixel X Dimension", TagHint.HINT_DEFAULT),
    EXIF_PIXEL_YDIMENSION(0xA003, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Pixel Y Dimension", TagHint.HINT_DEFAULT),
    EXIF_COMPONENTS_CONFIGURATION(0x9101, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Components Configuration", TagHint.HINT_BYTE),
    EXIF_COMPRESSED_BITS_PER_PIXEL(0x9102, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Compressed Bits Per Pixel", TagHint.HINT_DEFAULT),
    EXIF_CONTRAST(0xA408, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Contrast", TagHint.HINT_DEFAULT),
    EXIF_SATURATION(0xA409, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Saturation", TagHint.HINT_DEFAULT),
    EXIF_SHARPNESS(0xA40A, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Sharpness", TagHint.HINT_DEFAULT),
    EXIF_LENS_SPECIFICATION(0xA432, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Lens Specification", TagHint.HINT_RATIONAL),
    EXIF_GAMMA(0xA500, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Gamma", TagHint.HINT_DEFAULT),

    /* --- Hardware & Lens --- */
    EXIF_MAKER_NOTE(0x927C, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Maker Note", TagHint.HINT_BYTE_STREAM),
    EXIF_USER_COMMENT(0x9286, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "User Comment", TagHint.HINT_ENCODED_STRING),
    EXIF_LENS_MAKE(0xA433, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Lens Make", TagHint.HINT_DEFAULT),
    EXIF_LENS_MODEL(0xA434, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Lens Model", TagHint.HINT_DEFAULT),
    EXIF_LENS_SERIAL_NUMBER(0xA435, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Lens Serial Number", TagHint.HINT_DEFAULT),
    EXIF_BODY_SERIAL_NUMBER(0xA431, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Body Serial Number", TagHint.HINT_DEFAULT),
    EXIF_CAMERA_OWNER_NAME(0xA430, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Camera Owner Name", TagHint.HINT_DEFAULT),

    /* --- DNG Specific (Digital Negative) --- */
    EXIF_DNGVERSION(0xC612, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "DNG Version", TagHint.HINT_DEFAULT),
    EXIF_DNGBACKWARD_VERSION(0xC613, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "DNG Backward Version", TagHint.HINT_DEFAULT),
    EXIF_UNIQUE_CAMERA_MODEL(0xC614, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Unique Camera Model", TagHint.HINT_DEFAULT),
    EXIF_BLACK_LEVEL(0xC61A, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Black Level", TagHint.HINT_DEFAULT),
    EXIF_WHITE_LEVEL(0xC61D, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "White Level", TagHint.HINT_DEFAULT),
    EXIF_AS_SHOT_NEUTRAL(0xC628, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "As Shot Neutral", TagHint.HINT_DEFAULT),

    /*
     * Note, these tags can appear in the Exif Sub-IFD, they are almost exclusively written by
     * Windows and Adobe software into the Root IFD (IFD0).
     */

    // TODO: CHECK IF TagHint.HINT_UCS2 SHOULD BE USED INSTEAD?

    /*
     * These tags are written by Windows (UCS-2 encoded) and Adobe
     * typically into the Root IFD (IFD0).
     */
    EXIF_XPTITLE(0x9C9B, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Windows XP Title", TagHint.HINT_UCS2),
    EXIF_XPCOMMENT(0x9C9C, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Windows XP Comment", TagHint.HINT_UCS2),
    EXIF_XPKEYWORDS(0x9C9E, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Windows XP Keywords", TagHint.HINT_UCS2),
    EXIF_XPSUBJECT(0x9C9F, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Windows XP Subject", TagHint.HINT_UCS2),
    EXIF_IMAGE_RATING(0x4746, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Rating (0-5)", TagHint.HINT_DEFAULT),
    EXIF_IMAGE_RATING_PERCENT(0x4749, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Rating Percent", TagHint.HINT_DEFAULT),
    EXIF_INTEROPERABILITY_POINTER(0xA005, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Interoperability Offset", TagHint.HINT_DEFAULT);

    private final int numID;
    private final DirectoryIdentifier directory;
    private final TagHint hint;
    private final String desc;

    private TagIFD_Exif(int id, DirectoryIdentifier dir, String desc, TagHint clue)
    {
        this.numID = id;
        this.directory = dir;
        this.desc = desc;
        this.hint = clue;
    }

    private TagIFD_Exif(int id, DirectoryIdentifier dir)
    {
        this(id, dir, "", TagHint.HINT_DEFAULT);
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