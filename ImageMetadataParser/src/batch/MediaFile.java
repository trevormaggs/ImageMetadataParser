package batch;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import common.DigitalSignature;

/**
 * Represents metadata for a single media file, including file path, capture date, file format, and
 * metadata availability.
 * 
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public final class MediaFile
{
    private final Path mediaFile;
    private final FileTime dateTaken;
    private final boolean hasEmptyMetadata;
    private final DigitalSignature mediaFormat;

    private final boolean forced;

    /**
     * Constructs a MediaFile instance with the specified file path, capture date, and format
     * signature.
     *
     * @param fpath
     *        the path to the media file
     * @param date
     *        the capture or creation date of the media
     * @param sig
     *        the media format signature
     */
    public MediaFile(Path fpath, FileTime date, DigitalSignature sig)
    {
        this(fpath, date, sig, false, false);
    }

    /**
     * Constructs a MediaFile instance with the specified path, date, format, and metadata status.
     * 
     * @param fpath
     *        the path to the media file
     * @param date
     *        the capture or creation date of the media
     * @param sig
     *        the media format signature
     * @param emptymeta
     *        true if the media file has missing or empty metadata
     * @param forced
     *        XXXXXX - TESTING
     */
    public MediaFile(Path fpath, FileTime date, DigitalSignature sig, boolean emptymeta, boolean forced)
    {
        this.mediaFile = fpath;
        this.dateTaken = date;
        this.mediaFormat = sig;
        this.hasEmptyMetadata = emptymeta;

        this.forced = forced;
    }

    /**
     * Copy constructor. Creates a new MediaFile instance by copying the values from another
     * instance.
     *
     * @param obj
     *        the MediaFile object to copy
     */
    public MediaFile(MediaFile obj)
    {
        this(obj.getPath(), obj.getDateTaken(), obj.getMediaFormat(), obj.isMetadataEmpty(), obj.isForced());
    }

    public boolean isForced()
    {
        return forced;
    }

    /**
     * Returns the file system path to the media file.
     *
     * @return the media file path
     */
    public Path getPath()
    {
        return mediaFile;
    }

    /**
     * Returns the date and time when the media was originally captured or created.
     *
     * @return the media capture time-stamp as a {@code FileTime}
     */
    public FileTime getDateTaken()
    {
        return dateTaken;
    }

    /**
     * Returns the capture date as the number of milliseconds since the Unix epoch.
     *
     * @return the time-stamp in milliseconds
     */
    public long getTimestamp()
    {
        return dateTaken.toMillis();
    }

    /**
     * Returns the media format signature used to identify the file type, normally via magic
     * numbers.
     *
     * @return the media format type
     */
    public DigitalSignature getMediaFormat()
    {
        return mediaFormat;
    }

    /**
     * Indicates whether this media file lacks embedded metadata, for example: EXIF. This may be
     * used to determine whether metadata should be added or inferred.
     *
     * @return true if the file lacks metadata, otherwise false
     */
    public boolean isMetadataEmpty()
    {
        return hasEmptyMetadata;
    }

    /**
     * Returns whether this media file is in JPG format.
     *
     * @return true if JPG, otherwise false
     */
    public boolean isJPG()
    {
        return mediaFormat == DigitalSignature.JPG;
    }

    /**
     * Returns whether this media file is in PNG format.
     *
     * @return true if PNG, otherwise false
     */
    public boolean isPNG()
    {
        return mediaFormat == DigitalSignature.PNG;
    }

    /**
     * Returns whether this media file is in TIFF format.
     *
     * @return true if TIFF, otherwise false
     */
    public boolean isTIF()
    {
        return mediaFormat == DigitalSignature.TIF;
    }

    /**
     * Returns whether this media file is in HEIC format.
     *
     * @return true if HEIC, otherwise false
     */
    public boolean isHEIC()
    {
        return mediaFormat == DigitalSignature.HEIF;
    }

    /**
     * Returns whether this media file is in isWebP format.
     *
     * @return true if isWebP, otherwise false
     */
    public boolean isWebP()
    {
        return mediaFormat == DigitalSignature.WEBP;
    }

    /**
     * Returns whether this media file is a known video format, for example: MP4, MOV, AVI, etc.
     *
     * @return true if the media is a video, otherwise false
     */
    public boolean isVideoFormat()
    {
        return mediaFormat.isVideo();
    }

    /**
     * Compares this MediaFile instance with another for equality.
     *
     * @param other
     *        the object to compare
     * 
     * @return true if the objects are equal, otherwise false
     */
    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof MediaFile))
        {
            return false;
        }

        MediaFile meta = (MediaFile) other;

        return hasEmptyMetadata == meta.hasEmptyMetadata && mediaFormat == meta.mediaFormat
                && Objects.equals(dateTaken, meta.dateTaken) && Objects.equals(mediaFile, meta.mediaFile);
    }

    /**
     * Computes a hash code based on the media path, capture date, format, and metadata status.
     *
     * @return the hash code for this object
     */
    @Override
    public int hashCode()
    {
        int result = 17;

        result = 31 * result + mediaFile.hashCode();
        result = 31 * result + dateTaken.hashCode();
        result = 31 * result + mediaFormat.hashCode();
        result = 31 * result + Boolean.hashCode(hasEmptyMetadata);

        return result;
    }

    /**
     * Returns a multi-line string representation of this {@code MediaFile} instance, listing its
     * fields for debugging or logging.
     *
     * @return the formatted string representation
     */
    @Override
    public String toString()
    {
        StringBuilder line = new StringBuilder();
        line.append(String.format("  %-30s %s%n", "[Media File]", mediaFile));
        line.append(String.format("  %-30s %s%n", "[Date Taken]", dateTaken));
        line.append(String.format("  %-30s %s%n", "[Format]", mediaFormat));
        line.append(String.format("  %-30s %s%n", "[Empty Metadata]", hasEmptyMetadata));

        return line.toString();
    }
}