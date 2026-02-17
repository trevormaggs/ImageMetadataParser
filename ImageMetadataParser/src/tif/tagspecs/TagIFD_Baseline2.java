package tif.tagspecs;

import tif.DirectoryIdentifier;
import tif.TagHint;

public enum TagIFD_Baseline2 implements Taggable
{
    IFD_NEW_SUBFILE_TYPE(0x00FE, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "New Subfile Type"),
    IFD_SUBFILE_TYPE(0x00FF, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Subfile Type"),
    IFD_IMAGE_WIDTH(0x0100, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Image Width"),
    IFD_IMAGE_LENGTH(0x0101, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Image Height"),
    IFD_BITS_PER_SAMPLE(0x0102, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Bits Per Sample"),
    IFD_COMPRESSION(0x0103, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Compression"),
    IFD_PHOTOMETRIC_INTERPRETATION(0x0106, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Photometric Interpretation"),
    IFD_THRESHHOLDING(0x0107, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Thresholding"),
    IFD_CELL_WIDTH(0x0108, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Cell Width"),
    IFD_CELL_LENGTH(0x0109, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Cell Length"),
    IFD_FILL_ORDER(0x010A, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Fill Order"),
    IFD_DOCUMENT_NAME(0x010D, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Document Name"),
    IFD_IMAGE_DESCRIPTION(0x010E, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Image Description"),
    IFD_MAKE(0x010F, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Make"),
    IFD_MODEL(0x0110, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Model"),
    IFD_STRIP_OFFSETS(0x0111, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Strip Offsets"),
    IFD_ORIENTATION(0x0112, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Orientation"),
    IFD_SAMPLES_PER_PIXEL(0x0115, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Samples Per Pixel"),
    IFD_ROWS_PER_STRIP(0x0116, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Rows Per Strip"),
    IFD_STRIP_BYTE_COUNTS(0x0117, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Strip Byte Counts"),
    IFD_MIN_SAMPLE_VALUE(0x0118, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Minimum Sample Value"),
    IFD_MAX_SAMPLE_VALUE(0x0119, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Maximum Sample Value"),
    IFD_XRESOLUTION(0x011A, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "X Resolution"),
    IFD_YRESOLUTION(0x011B, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Y Resolution"),
    IFD_PLANAR_CONFIGURATION(0x011C, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Planar Configuration"),
    IFD_PAGE_NAME(0x011D, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Page Name"),
    IFD_XPOSITION(0x011E, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "X Position"),
    IFD_YPOSITION(0x011F, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Y Position"),
    IFD_FREE_OFFSETS(0x0120, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Free Offsets"),
    IFD_FREE_BYTE_COUNTS(0x0121, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Free Byte Counts"),
    IFD_GRAY_RESPONSE_UNIT(0x0122, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Gray Response Unit"),
    IFD_GRAY_RESPONSE_CURVE(0x0123, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Gray Response Curve"),
    IFD_T4OPTIONS(0x0124, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "T4 Options"),
    IFD_T6OPTIONS(0x0125, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "T6 Options"),
    IFD_RESOLUTION_UNIT(0x0128, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Resolution Unit"),
    IFD_PAGE_NUMBER(0x0129, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Page Number"),
    IFD_TRANSFER_FUNCTION(0x012D, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Transfer Function"),
    IFD_SOFTWARE(0x0131, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Software"),
    IFD_DATE_TIME(0x0132, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Date/Time", TagHint.HINT_DATE),
    IFD_ARTIST(0x013B, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Artist"),
    IFD_HOST_COMPUTER(0x013C, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Host Computer"),
    IFD_PREDICTOR(0x013D, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Predictor"),
    IFD_WHITE_POINT(0x013E, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "White Point"),
    IFD_PRIMARY_CHROMATICITIES(0x013F, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Primary Chromaticities"),
    IFD_COLOR_MAP(0x0140, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Color Map"),
    IFD_HALFTONE_HINTS(0x0141, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Halftone Hints"),
    IFD_TILE_WIDTH(0x0142, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Tile Width"),
    IFD_TILE_LENGTH(0x0143, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Tile Length"),
    IFD_TILE_OFFSETS(0x0144, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Tile Offsets"),
    IFD_TILE_BYTE_COUNTS(0x0145, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Tile Byte Counts"),
    IFD_BAD_FAX_LINES(0x0146, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Bad Fax Lines"),
    IFD_CLEAN_FAX_DATA(0x0147, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Clean Fax Data"),
    IFD_CONSECUTIVE_BAD_FAX_LINES(0x0148, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Consecutive Bad Fax Lines"),
    IFD_IFDSUB_POINTER(0x014A, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "SubIFD DataOffset"),
    IFD_INK_SET(0x014C, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Ink Set"),
    IFD_INK_NAMES(0x014D, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Ink Names"),
    IFD_NUMBER_OF_INKS(0x014E, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Number of Inks"),
    IFD_DOT_RANGE(0x0150, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Dot Range"),
    IFD_TARGET_PRINTER(0x0151, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Target Printer"),
    IFD_EXTRA_SAMPLES(0x0152, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Extra Samples"),
    IFD_SAMPLE_FORMAT(0x0153, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Sample Format"),
    IFD_SMIN_SAMPLE_VALUE(0x0154, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "SMin Sample Value"),
    IFD_SMAX_SAMPLE_VALUE(0x0155, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "SMax Sample Value"),
    IFD_TRANSFER_RANGE(0x0156, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Transfer Range"),
    IFD_CLIP_PATH(0x0157, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Clip Path"),
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
    IFD_JPEG_PROC(0x0200, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "JPEG Proc"),
    // actually IFD_DIRECTORY_IFD1, but keep it for discovery purposes
    IFD_JPEG_INTERCHANGE_FORMAT(0x0201, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "JPEG Interchange Format"),
    // actually IFD_DIRECTORY_IFD1, but keep it for discovery purposes
    IFD_JPEG_INTERCHANGE_FORMAT_LENGTH(0x0202, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "JPEG Interchange Format Length"),
    IFD_JPEG_RESTART_INTERVAL(0x0203, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "JPEG Restart Interval"),
    IFD_JPEG_LOSSLESS_PREDICTORS(0x0205, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "JPEG Lossless Predictors"),
    IFD_JPEG_POINT_TRANSFORMS(0x0206, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "JPEG Point Transforms"),
    IFD_JPEG_QTABLES(0x0207, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "JPEG Point Transforms"),
    IFD_JPEG_DCTABLES(0x0208, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "JPEGDC Tables"),
    IFD_JPEG_ACTABLES(0x0209, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "JPEGAC Tables"),
    IFD_YCB_CR_COEFFICIENTS(0x0211, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "YCbCr Coefficients"),
    IFD_YCB_CR_SUB_SAMPLING(0x0212, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "YCbCr Sub-Sampling"),
    IFD_YCB_CR_POSITIONING(0x0213, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "YCbCr Positioning"),
    IFD_REFERENCE_BLACK_WHITE(0x0214, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Reference Black/White"),
    IFD_STRIP_ROW_COUNTS(0x022F, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Strip Row Counts"),
    IFD_XML_PACKET(0x02BC, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "XMP Metadata"),
    IFD_IMAGE_ID(0x800D, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Image ID"),
    IFD_COPYRIGHT(0x8298, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Copyright"),
    IFD_EXIF_POINTER(0x8769, DirectoryIdentifier.IFD_EXIF_SUBIFD_DIRECTORY, "Exif Metadata"),
    IFD_IMAGE_LAYER(0x87AC, DirectoryIdentifier.IFD_ROOT_DIRECTORY, "Image Layer"),
    IFD_GPS_INFO_POINTER(0x8825, DirectoryIdentifier.IFD_GPS_DIRECTORY, "GPS Metadata");

    private final int numID;
    private final DirectoryIdentifier directory;
    private final TagHint hint;
    private final String desc;

    private TagIFD_Baseline2(int id, DirectoryIdentifier dir, String desc)
    {
        this(id, dir, desc, TagHint.HINT_DEFAULT);
    }

    private TagIFD_Baseline2(int id, DirectoryIdentifier dir, String desc, TagHint clue)
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

    // @Override
    public String getDescription()
    {
        return desc;
    }
}