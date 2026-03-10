package tif.tagspecs;

import tif.TagHint;
import tif.DirectoryIdentifier;

public interface Taggable
{
    /**
     * @return the 16-bit numerical Tag ID (e.g., 0x0100 for ImageWidth)
     */
    public int getNumberID();

    /**
     * @return the expected data type or format hint for this tag
     */
    public TagHint getHint();

    /**
     * @return the IFD where this tag is valid
     */
    public DirectoryIdentifier getDirectoryType();

    /**
     * @return a human-readable name or description of the tag
     */
    public String getDescription();

    /**
     * @return true if this represents a tag not explicitly defined in the specification
     */
    public default boolean isUnknown()
    {
        return false;
    }
}