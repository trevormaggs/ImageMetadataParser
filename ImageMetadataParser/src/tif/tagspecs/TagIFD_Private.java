package tif.tagspecs;

import tif.DirectoryIdentifier;
import tif.TagHint;

public enum TagIFD_Private implements Taggable
{
    IFD_PROCESSING_SOFTWARE(0x000B, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Processing Software"),
    IFD_RATING(0x4746, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Rating"),
    IFD_RATING_PERCENT(0x4749, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Rating Percent"),
    IFD_PIXEL_SCALE(0x830E, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Pixel Scale"),
    IFD_INTERGRAPH_MATRIX(0x8480, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "IntergraphMatrix"),
    IFD_MODEL_TIE_POINT(0x8482, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Model Tie Point"),
    IFD_SEMINFO(0x8546, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "SEM Info"),
    IFD_MODEL_TRANSFORM(0x85D8, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Model Transform"),
    IFD_PHOTOSHOP_SETTINGS(0x8649, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Photoshop Tags"),
    IFD_ICC_PROFILE(0x8773, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "ICC Profile Tags", TagHint.HINT_BYTE_STREAM),
    IFD_GEO_TIFF_DIRECTORY(0x87AF, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Geo Tiff Directory", TagHint.HINT_SHORT),
    IFD_GEO_TIFF_DOUBLE_PARAMS(0x87B0, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Geo Tiff Double Params"),
    IFD_GEO_TIFF_ASCII_PARAMS(0x87B1, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Geo Tiff Ascii Params"),
    IFD_IMAGE_SOURCE_DATA(0x935C, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Image Source Data"),
    IFD_XPTITLE(0x9C9B, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "XP Title", TagHint.HINT_UCS2),
    IFD_XPCOMMENT(0x9C9C, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "XP Comment", TagHint.HINT_UCS2),
    IFD_XPAUTHOR(0x9C9D, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "XP Author", TagHint.HINT_UCS2),
    IFD_XPKEYWORDS(0x9C9E, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "XP Keywords", TagHint.HINT_UCS2),
    IFD_XPSUBJECT(0x9C9F, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "XP Subject", TagHint.HINT_UCS2),
    IFD_GDAL_METADATA(0xA480, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "GDAL Metadata"),
    IFD_GDAL_NO_DATA(0xA481, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "GDAL NoData"),
    IFD_PRINT_IM(0xC4A5, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Print IM"),
    IFD_DNG_VERSION(0xC612, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "DNG Version", TagHint.HINT_BYTE),
    IFD_DNG_BACKWARD_VERSION(0xC613, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "DNG Backward Version"),
    IFD_UNIQUE_CAMERA_MODEL(0xC614, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Unique Camera Model"),
    IFD_LOCALIZED_CAMERA_MODEL(0xC615, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Localized Camera Model"),
    IFD_COLOR_MATRIX1(0xC621, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Color Matrix1"),
    IFD_COLOR_MATRIX2(0xC622, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Color Matrix2"),
    IFD_CAMERA_CALIBRATION1(0xC623, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Camera Calibration1"),
    IFD_CAMERA_CALIBRATION2(0xC624, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Camera Calibration2"),
    IFD_REDUCTION_MATRIX1(0xC625, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Reduction Matrix1"),
    IFD_REDUCTION_MATRIX2(0xC626, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Reduction Matrix2"),
    IFD_ANALOG_BALANCE(0xC627, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Analog Balance"),
    IFD_AS_SHOT_NEUTRAL(0xC628, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "As Shot Neutral", TagHint.HINT_RATIONAL),
    IFD_AS_SHOT_WHITE_XY(0xC629, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "As Shot White XY"),
    IFD_BASELINE_EXPOSURE(0xC62A, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Baseline Exposure"),
    IFD_BASELINE_NOISE(0xC62B, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Baseline Noise"),
    IFD_BASELINE_SHARPNESS(0xC62C, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Baseline Sharpness"),
    IFD_LINEAR_RESPONSE_LIMIT(0xC62E, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Linear Response Limit"),
    IFD_CAMERA_SERIAL_NUMBER(0xC62F, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Camera Serial Number"),
    IFD_DNG_LENS_INFO(0xC630, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "DNG Lens Info"),
    IFD_SHADOW_SCALE(0xC633, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Shadow Scale"),
    IFD_SR2_PRIVATE(0xC634, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "SR2 Private"),
    IFD_MAKER_NOTE_SAFETY(0xC635, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Maker Note Safety"),
    IFD_CALIBRATION_ILLUMINANT1(0xC65A, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Calibration Illuminant1"),
    IFD_CALIBRATION_ILLUMINANT2(0xC65B, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Calibration Illuminant2"),
    IFD_RAW_DATA_UNIQUE_ID(0xC65D, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Raw Data Unique ID"),
    IFD_ORIGINAL_RAW_FILE_NAME(0xC68B, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Original Raw File Name"),
    IFD_ORIGINAL_RAW_FILE_DATA(0xC68C, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Original Raw File Data"),
    IFD_AS_SHOT_ICCPROFILE(0xC68F, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "As Shot ICC Profile"),
    IFD_AS_SHOT_PRE_PROFILE_MATRIX(0xC690, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "As Shot Pre Profile Matrix"),
    IFD_CURRENT_ICCPROFILE(0xC691, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Current ICC Profile"),
    IFD_CURRENT_PRE_PROFILE_MATRIX(0xC692, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Current Pre Profile Matrix"),
    IFD_COLORIMETRIC_REFERENCE(0xC6BF, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Colorimetric Reference"),
    IFD_SRAW_TYPE(0xC6C5, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "SRaw Type"),
    IFD_PANASONIC_TITLE(0xC6D2, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Panasonic Title"),
    IFD_PANASONIC_TITLE2(0xC6D3, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Panasonic Title2"),
    IFD_CAMERA_CALIBRATION_SIG(0xC6F3, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Camera Calibration Sig"),
    IFD_PROFILE_CALIBRATION_SIG(0xC6F4, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Calibration Sig"),
    IFD_PROFILE_IFD(0xC6F5, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile IFD"),
    IFD_AS_SHOT_PROFILE_NAME(0xC6F6, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "As Shot Profile Name"),
    IFD_PROFILE_NAME(0xC6F8, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Name"),
    IFD_PROFILE_HUE_SAT_MAP_DIMS(0xC6F9, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Hue Sat Map Dims"),
    IFD_PROFILE_HUE_SAT_MAP_DATA1(0xC6FA, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Hue Sat Map Data1"),
    IFD_PROFILE_HUE_SAT_MAP_DATA2(0xC6FB, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Hue Sat Map Data2"),
    IFD_PROFILE_TONE_CURVE(0xC6FC, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Tone Curve"),
    IFD_PROFILE_EMBED_POLICY(0xC6FD, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Embed Policy"),
    IFD_PROFILE_COPYRIGHT(0xC6FE, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Copyright"),
    IFD_FORWARD_MATRIX1(0xC714, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Forward Matrix1"),
    IFD_FORWARD_MATRIX2(0xC715, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Forward Matrix2"),
    IFD_PREVIEW_APPLICATION_NAME(0xC716, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Preview Application Name"),
    IFD_PREVIEW_APPLICATION_VERSION(0xC717, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Preview Application Version"),
    IFD_PREVIEW_SETTINGS_NAME(0xC718, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Preview Settings Name"),
    IFD_PREVIEW_SETTINGS_DIGEST(0xC719, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Preview Settings Digest"),
    IFD_PREVIEW_COLOR_SPACE(0xC71A, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Preview Color Space"),
    IFD_PREVIEW_DATE_TIME(0xC71B, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Preview Date Time", TagHint.HINT_DATE),
    IFD_RAW_IMAGE_DIGEST(0xC71C, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Raw Image Digest"),
    IFD_ORIGINAL_RAW_FILE_DIGEST(0xC71D, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Original Raw File Digest"),
    IFD_PROFILE_LOOK_TABLE_DIMS(0xC725, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Look Table Dims"),
    IFD_PROFILE_LOOK_TABLE_DATA(0xC726, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Look Table Data"),
    IFD_TIME_CODES(0xC763, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Time Codes"),
    IFD_FRAME_RATE(0xC764, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Frame Rate"),
    IFD_TSTOP(0xC772, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "TStop"),
    IFD_REEL_NAME(0xC789, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Reel Name"),
    IFD_ORIGINAL_DEFAULT_FINAL_SIZE(0xC791, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Original Default Final Size"),
    IFD_ORIGINAL_BEST_QUALITY_SIZE(0xC792, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Original Best Quality Size"),
    IFD_ORIGINAL_DEFAULT_CROP_SIZE(0xC793, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Original Default Crop Size"),
    IFD_CAMERA_LABEL(0xC7A1, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Camera Label"),
    IFD_PROFILE_HUE_SAT_MAP_ENCODING(0xC7A3, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Hue Sat Map Encoding"),
    IFD_PROFILE_LOOK_TABLE_ENCODING(0xC7A4, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Look Table Encoding"),
    IFD_BASELINE_EXPOSURE_OFFSET(0xC7A5, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Baseline Exposure Offset"),
    IFD_DEFAULT_BLACK_RENDER(0xC7A6, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Default Black Render"),
    IFD_NEW_RAW_IMAGE_DIGEST(0xC7A7, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "New Raw Image Digest"),
    IFD_RAW_TO_PREVIEW_GAIN(0xC7A8, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Raw To Preview Gain"),
    IFD_DEPTH_FORMAT(0xC7E9, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Depth Format"),
    IFD_DEPTH_NEAR(0xC7EA, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Depth Near"),
    IFD_DEPTH_FAR(0xC7EB, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Depth Far"),
    IFD_DEPTH_UNITS(0xC7EC, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Depth Units"),
    IFD_DEPTH_MEASURE_TYPE(0xC7ED, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Depth Measure Type"),
    IFD_ENHANCE_PARAMS(0xC7EE, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Enhance Params"),
    IFD_CALIBRATION_ILLUMINANT3(0xCD31, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Calibration Illuminant3"),
    IFD_CAMERA_CALIBRATION3(0xCD32, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Camera Calibration3"),
    IFD_COLOR_MATRIX3(0xCD33, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Color Matrix3"),
    IFD_FORWARD_MATRIX3(0xCD34, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Forward Matrix3"),
    IFD_ILLUMINANT_DATA1(0xCD35, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Illuminant Data1"),
    IFD_ILLUMINANT_DATA2(0xCD36, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Illuminant Data2"),
    IFD_ILLUMINANT_DATA3(0xCD37, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Illuminant Data3"),
    IFD_PROFILE_HUE_SAT_MAP_DATA3(0xCD39, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Hue Sat Map Data3"),
    IFD_REDUCTION_MATRIX3(0xCD3A, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Reduction Matrix3"),
    IFD_RGB_TABLES(0xCD3F, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "RGB Tables"),
    IFD_PROFILE_GAIN_TABLE_MAP2(0xCD40, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Gain Table Map2"),
    IFD_IMAGE_SEQUENCE_INFO(0xCD44, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Image Sequence Info"),
    IFD_IMAGE_STATS(0xCD46, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Image Stats"),
    IFD_PROFILE_DYNAMIC_RANGE(0xCD47, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Dynamic Range"),
    IFD_PROFILE_GROUP_NAME(0xCD48, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Profile Group Name"),
    IFD_JXL_DISTANCE(0xCD49, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "JXL Distance"),
    IFD_JXL_EFFORT(0xCD4A, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "JXL Effort"),
    IFD_JXL_DECODE_SPEED(0xCD4B, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "JXL Decode Speed"),
    IFD_SEAL(0xCEA1, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "SEAL");

    private final int numID;
    private final DirectoryIdentifier directory;
    private final TagHint hint;
    private final String desc;

    private TagIFD_Private(int id, DirectoryIdentifier dir, String desc)
    {
        this(id, dir, desc, TagHint.HINT_DEFAULT);
    }

    private TagIFD_Private(int id, DirectoryIdentifier dir, String desc, TagHint clue)
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