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
 * Handles XMP metadata extraction from raw XML packets.
 * 
 * <p>
 * Supports standard XMP payloads from JPEG, TIFF, HEIF, and other container formats, using the
 * Adobe XMPCore library.
 * </p>
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
    private final XmpDirectory xmpDir = new XmpDirectory();

    /**
     * Parses XMP metadata from a byte array and returns a structured directory.
     *
     * @param input
     *        byte array containing the XMP XML packet
     * @return a populated {@link XmpDirectory}, or null if no properties were found
     * 
     * @throws XMPException
     *         if the data is malformed
     */
    public static XmpDirectory addXmpDirectory(byte[] input) throws XMPException
    {
        XmpHandler handler = new XmpHandler(input);

        if (handler.parseMetadata())
        {
            LOGGER.debug(String.format("XMP Data Found. [%d bytes] processed", input.length));

            return handler.getXmpDirectory();
        }

        return null;
    }

    /**
     * Constructs a new handler and initiates the extraction process.
     * 
     * @param input
     *        raw XMP data payload, typically a single XML packet, combined from multiple segments
     * 
     * @throws NullPointerException
     *         if input is null/empty
     * @throws XMPException
     *         if the data is malformed
     */
    public XmpHandler(byte[] input) throws XMPException
    {
        if (input == null || input.length == 0)
        {
            throw new NullPointerException("XMP Data payload cannot be null or empty");
        }

        readPropertyData(input);
    }

    /**
     * Indicates the parsing of the XMP payload was successful.
     * 
     * @return true if the internal directory is now available for retrieval
     */
    @Override
    public boolean parseMetadata()
    {
        return !xmpDir.isEmpty();
    }

    /**
     * Returns the directory containing all extracted XMP records.
     * 
     * @return an instance of {@link XmpDirectory}. Guaranteed non-null will be returned
     */
    public XmpDirectory getXmpDirectory()
    {
        return xmpDir;
    }

    /**
     * Uses Adobe XMPCore to iterate through the RDF tree and populate the directory.
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

                if (!(obj instanceof XMPPropertyInfo))
                {
                    continue;
                }

                XMPPropertyInfo info = (XMPPropertyInfo) obj;
                String ns = info.getNamespace();
                String path = info.getPath();
                String value = info.getValue();

                // Handle structural nodes (containers/arrays) that don't have immediate values
                if (path == null || value == null || value.isEmpty())
                {
                    if (ns != null && !ns.isEmpty()) nsTracker = ns;
                    continue;
                }

                String finalNs = (ns != null && !ns.isEmpty()) ? ns : nsTracker;

                // Strip array indices [1] to simplify path-based lookup
                Matcher matcher = REGEX_DIGIT.matcher(path);
                String cleanedPath = matcher.replaceAll("");

                xmpDir.add(new XmpRecord(finalNs, cleanedPath, value));
            }

            LOGGER.debug("Registered [" + xmpDir.size() + "] XMP records");
        }

        else
        {
            LOGGER.warn("XMPMetaFactory failed to produce metadata object");
        }
    }
}