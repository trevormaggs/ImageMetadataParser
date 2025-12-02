package xmp;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPIterator;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.properties.XMPPropertyInfo;
import common.ImageHandler;
import common.ImageReadErrorException;
import logger.LogFactory;
import xmp.XmpDirectory.XmpRecord;

/**
 * Handles XMP metadata extraction from the raw XMP payload (an XML packet). This payload can be
 * sourced from various file types, including JPEG (APP1 segment), TIFF, WebP, PNG, and DNG.
 *
 * <pre>
 *  -- For developmental testing --
 *
 * <u>Some examples of exiftool usages</u>
 *
 * exiftool -XMP:All -a -u -g1 pool19.JPG
 * ---- XMP-x ----
 * XMP Toolkit : Image::ExifTool 13.29
 * ---- XMP-rdf ----
 * About : uuid:faf5bdd5-ba3d-11da-ad31-d33d75182f1b
 * ---- XMP-dc ----
 * Creator : Gemma Emily Maggs
 * Description : Trevor
 * Title : Trevor
 * ---- XMP-exif ----
 * Date/Time Original : 2011:10:07 22:59:20
 * ---- XMP-xmp ----
 * Create Date : 2011:10:07 22:59:20
 * Modify Date : 2011:10:07 22:59:20
 *
 * exiftool -XMP:Description="Construction Progress" XMPimage.png
 * </pre>
 *
 * @author Trevor
 * @version 1.8
 * @since 9 November 2025
 */
public class XmpHandler implements ImageHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(XmpHandler.class);
    private static final Pattern REGEX_DIGIT = Pattern.compile("\\[\\d+\\]");
    private final XmpDirectory xmpDir;

    /**
     * Constructs a new XmpHandler from a list of XMP segments.
     *
     * @param input
     *        raw XMP segments as a single byte array
     *
     * @throws ImageReadErrorException
     *         if segments are null, empty, or cannot be parsed
     */
    public XmpHandler(byte[] input) throws ImageReadErrorException
    {
        if (input == null || input.length == 0)
        {
            throw new ImageReadErrorException("XMP Data is null or empty");
        }

        xmpDir = new XmpDirectory();

        try
        {
            readPropertyData(input);
        }

        catch (XMPException exc)
        {
            throw new ImageReadErrorException("Failed to parse XMP data: " + exc.getMessage(), exc);
        }
    }

    /**
     * Returns a wrapper of the {@code XmpDirectory} directory that was successfully parsed.
     *
     * <p>
     * If no XMP properties were found, it returns {@link Optional#empty()}. Otherwise, it returns
     * an {@link Optional} containing the parsed XmpDirectory directory.
     * </p>
     *
     * @return an {@link Optional} containing one instance of {@link XmpDirectory}, or
     *         {@link Optional#empty()} if no properties were parsed
     */
    public Optional<XmpDirectory> getXmpDirectory()
    {
        return (xmpDir.isEmpty() ? Optional.empty() : Optional.of(xmpDir));
    }

    /**
     * If the parsing of the XMP segment was successful in constructor, it will return true.
     *
     * @return true if one or more XMP properties were successfully extracted, otherwise false
     */
    @Override
    public boolean parseMetadata()
    {
        return !xmpDir.isEmpty();
    }

    /**
     * Parses the raw XMP byte array and populates the property map. Employs the Adobe XMP SDK
     * (XMPCore library) for efficient iteration and property extraction.
     *
     * The logic handles structural nodes by tracking the last known namespace URI.
     *
     * @param data
     *        an array of bytes containing raw XMP data
     *
     * @throws XMPException
     *         if parsing fails
     */
    private void readPropertyData(byte[] data) throws XMPException
    {
        String nsTracker = "";
        XMPMeta xmpMeta = XMPMetaFactory.parseFromBuffer(data);

        if (xmpMeta != null)
        {
            XMPIterator iter = xmpMeta.iterator();

            while (iter.hasNext())
            {
                Object obj = iter.next();

                if (obj instanceof XMPPropertyInfo)
                {
                    XMPPropertyInfo info = (XMPPropertyInfo) obj;

                    String ns = info.getNamespace();
                    String path = info.getPath();
                    String value = info.getValue();

                    if (path == null || value == null || value.isEmpty())
                    {
                        if (ns != null && !ns.isEmpty())
                        {
                            nsTracker = ns;
                        }

                        continue;
                    }

                    String finalNs = nsTracker;

                    if (ns != null && !ns.isEmpty())
                    {
                        finalNs = ns;
                    }

                    Matcher dirtyPath = REGEX_DIGIT.matcher(path);
                    String cleanedPath = dirtyPath.replaceAll("");

                    xmpDir.add(new XmpRecord(finalNs, cleanedPath, value));
                }
            }
        }

        else
        {
            LOGGER.warn("XMP metadata could not be parsed and XMPMetaFactory returned null.");
        }
    }
}