package heif.boxes;

import java.io.IOException;
import common.ByteStreamReader;
import logger.LogFactory;

/**
 * Represents the {@code clap} (Clean Aperture Box). It defines the exact area of the image to be
 * displayed, cropping any padding.
 *
 * Refer to ISO/IEC 14496-12:2015 documentation in Page 157 for more technical details.
 */
public class CleanApertureBox extends Box
{
    private static final LogFactory LOGGER = LogFactory.getLogger(CleanApertureBox.class);

    private final long widthN, widthD;
    private final long heightN, heightD;
    private final long hOffN, hOffD;
    private final long vOffN, vOffD;

    /*
     * class CleanApertureBox extends Box(‘clap’){
     * unsigned int(32) cleanApertureWidthN;
     * unsigned int(32) cleanApertureWidthD;
     * unsigned int(32) cleanApertureHeightN;
     * unsigned int(32) cleanApertureHeightD;
     * unsigned int(32) horizOffN;
     * unsigned int(32) horizOffD;
     * unsigned int(32) vertOffN;
     * unsigned int(32) vertOffD;
     */

    public CleanApertureBox(Box box, ByteStreamReader reader) throws IOException
    {
        super(box);

        widthN = reader.readUnsignedInteger();
        widthD = reader.readUnsignedInteger();
        heightN = reader.readUnsignedInteger();
        heightD = reader.readUnsignedInteger();
        hOffN = reader.readUnsignedInteger();
        hOffD = reader.readUnsignedInteger();
        vOffN = reader.readUnsignedInteger();
        vOffD = reader.readUnsignedInteger();
    }

    // --- Raw Getters ---

    public long getWidthN()
    {
        return widthN;
    }

    public long getWidthD()
    {
        return widthD;
    }

    public long getHeightN()
    {
        return heightN;
    }
    public long getHeightD()
    {
        return heightD;
    }

    public long getHorizOffN()
    {
        return hOffN;
    }

    public long getHorizOffD()
    {
        return hOffD;
    }

    public long getVertOffN()
    {
        return vOffN;
    }

    public long getVertOffD()
    {
        return vOffD;
    }

    // --- Calculated Getters (API Conveniences) ---

    /** @return the calculated width as a double. */
    public double getCleanWidth()
    {
        return widthD != 0 ? (double) widthN / widthD : 0;
    }

    /** @return the calculated height as a double. */
    public double getCleanHeight()
    {
        return heightD != 0 ? (double) heightN / heightD : 0;
    }

    /** @return the horizontal offset from the center. */
    public double getHorizontalOffset()
    {
        return hOffD != 0 ? (double) hOffN / hOffD : 0;
    }

    /** @return the vertical offset from the center. */
    public double getVerticalOffset()
    {
        return vOffD != 0 ? (double) vOffN / vOffD : 0;
    }

    @Override
    public void logBoxInfo()
    {
        String tab = Box.repeatPrint("\t", getHierarchyDepth());
        LOGGER.debug(String.format("%s%s '%s': Width=%.2f, Height=%.2f, H-Offset=%.2f, V-Offset=%.2f", tab, getClass().getSimpleName(), getFourCC(), getCleanWidth(), getCleanHeight(), getHorizontalOffset(), getVerticalOffset()));
    }
}