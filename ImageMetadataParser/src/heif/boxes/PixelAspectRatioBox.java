package heif.boxes;

import java.io.IOException;
import common.ByteStreamReader;
import logger.LogFactory;

/**
 * Represents the {@code pasp} (Pixel Aspect Ratio Box).
 * Defines the relative width and height of a pixel (square vs non-square pixels).
 * * Refer to ISO/IEC 14496-12:2015 documentation in Page 157.
 */
public class PixelAspectRatioBox extends Box 
{
    private static final LogFactory LOGGER = LogFactory.getLogger(PixelAspectRatioBox.class);

    private final long hSpacing;
    private final long vSpacing;

    public PixelAspectRatioBox(Box box, ByteStreamReader reader) throws IOException 
    {
        super(box);
        hSpacing = reader.readUnsignedInteger();
        vSpacing = reader.readUnsignedInteger();
    }

    /** @return horizontal spacing (relative width of a pixel) */
    public long getHSpacing() { return hSpacing; }

    /** @return vertical spacing (relative height of a pixel) */
    public long getVSpacing() { return vSpacing; }

    /** @return the aspect ratio as a double (hSpacing / vSpacing) */
    public double getAspectRatio() {
        return vSpacing != 0 ? (double) hSpacing / vSpacing : 1.0;
    }

    @Override
    public void logBoxInfo() 
    {
        String tab = Box.repeatPrint("\t", getHierarchyDepth());
        LOGGER.debug(String.format("%s%s '%s': hSpacing=%d, vSpacing=%d (Ratio: %.2f)", 
            tab, getClass().getSimpleName(), getFourCC(), hSpacing, vSpacing, getAspectRatio()));
    }
}