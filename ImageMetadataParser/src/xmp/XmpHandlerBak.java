package xmp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPIterator;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.properties.XMPPropertyInfo;
import common.ImageHandler;
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
public class XmpHandlerBak implements ImageHandler
{
    private static final LogFactory LOGGER = LogFactory.getLogger(XmpHandlerBak.class);
    private static final Pattern REGEX_DIGIT = Pattern.compile("\\[\\d+\\]");
    private final XmpDirectory xmpDir;

    /**
     * Constructs a new XmpHandler from a list of XMP segments.
     *
     * @param input
     *        raw XMP data payload, typically a single XML packet, combined from multiple segments
     * 
     * @throws NullPointerException
     *         if the specified byte array is null or empty
     * @throws XMPException
     *         if the XMP data format is invalid and cannot be parsed by the SDK
     */
    public XmpHandlerBak(byte[] input) throws XMPException
    {
        if (input == null || input.length == 0)
        {
            throw new NullPointerException("XMP Data is null or empty");
        }

        xmpDir = new XmpDirectory();

        readPropertyData(input);
    }

    /**
     * Parses XMP metadata from a byte array.
     *
     * <p>
     * For efficiency, use this static utility method where XMP-formatted data is already available
     * in memory. It directly processes the byte array to extract and structure the metadata
     * directory without having to read a file from disk again.
     * </p>
     *
     * <p>
     * Note: This method assumes the specified byte array is a valid XMP payload. It is the
     * responsibility of the programmers to ensure the correct format is available. No external
     * validation is performed.
     * </p>
     *
     * @param input
     *        byte array containing XMP-formatted data
     * @return the parsed {@link XmpDirectory} object. Returns null if metadata is successfully
     *         parsed but contains no XMP properties
     * 
     * @throws XMPException
     *         if the data cannot be parsed
     */
    public static XmpDirectory addXmpDirectory(byte[] input) throws XMPException
    {
        XmpDirectory xmpDir = null;
        XmpHandlerBak xmp = new XmpHandlerBak(input);

        if (xmp.parseMetadata())
        {
            xmpDir = xmp.getXmpDirectory();
            LOGGER.debug("XMP Data Found. [" + input.length + " bytes] processed");
        }

        return xmpDir;
    }

    /**
     * Returns the {@code XmpDirectory} directory.
     *
     * <p>
     * If no XMP properties were found, it just returns an empty directory, guaranteeing no null is
     * returned.
     * </p>
     *
     * @return an instance of {@link XmpDirectory} directory
     */
    public XmpDirectory getXmpDirectory()
    {
        return xmpDir;
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

            LOGGER.debug("Number of XMP record(s) registered [" + xmpDir.size() + "]");
        }

        else
        {
            LOGGER.warn("XMP metadata could not be parsed and XMPMetaFactory returned null.");
        }
    }
}