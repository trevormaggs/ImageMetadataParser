package heif;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import common.ByteStreamReader;
import common.ByteValueConverter;
import common.ImageHandler;
import common.ImageRandomAccessReader;
import heif.boxes.Box;
import heif.boxes.DataInformationBox;
import heif.boxes.HandlerBox;
import heif.boxes.ItemDataBox;
import heif.boxes.ItemInfoEntry;
import heif.boxes.ItemInformationBox;
import heif.boxes.ItemLocationBox;
import heif.boxes.ItemLocationBox.ExtentData;
import heif.boxes.ItemLocationBox.ItemLocationEntry;
import heif.boxes.ItemPropertiesBox;
import heif.boxes.ItemReferenceBox;
import heif.boxes.PrimaryItemBox;
import logger.LogFactory;

/**
 * Handles parsing of HEIF/HEIC file structures based on the ISO Base Media Format.
 * *
 * <p>
 * Supports Exif/XMP extraction, box navigation, and hierarchical parsing.
 * </p>
 *
 * <p>
 * <strong>API Note:</strong> According to HEIF/HEIC standards, some box types are optional and may
 * appear zero or one time per file.
 * </p>
 *
 * <p>
 * <strong>Thread Safety:</strong> This class is not thread-safe as it maintains internal state of
 * the underlying {@link ByteStreamReader}.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.1
 * @since 13 August 2025
 */
public class BoxHandler implements ImageHandler, AutoCloseable, Iterable<Box>
{
    private static final LogFactory LOGGER = LogFactory.getLogger(BoxHandler.class);
    private static final ByteOrder HEIF_BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private static final String IREF_CDSC = "cdsc";
    private static final String TYPE_EXIF = "Exif";
    private static final String TYPE_MIME = "mime";
    private final Map<HeifBoxType, List<Box>> heifBoxMap = new LinkedHashMap<>();
    private final List<Box> rootBoxes = new ArrayList<>();
    private final ByteStreamReader reader;

    public enum MetadataType
    {
        EXIF, XMP, OTHER;
    }

    /**
     * Constructs a {@code BoxHandler} to open the specified file for the parsing of the embedded
     * metadata, respecting the big-endian byte order in accordance with the ISO/IEC 14496-12
     * documentation.
     *
     * <p>
     * Note: This constructor opens a file-based resource. The handler should be used within a
     * try-with-resources block to ensure the file lock is released.
     * </p>
     *
     * @param fpath
     *        to open the image file for parsing
     *
     * @throws IOException
     *         if the file cannot be accessed or an I/O error occurs
     */
    public BoxHandler(Path fpath) throws IOException
    {
        this.reader = new ImageRandomAccessReader(fpath, HEIF_BYTE_ORDER);
    }

    /**
     * Gets the {@link HandlerBox}, if present.
     *
     * @return the {@link HandlerBox}, or null if not found
     */
    public HandlerBox getHDLR()
    {
        return getBox(HeifBoxType.HANDLER, HandlerBox.class);
    }

    /**
     * Gets the {@link PrimaryItemBox}, if present.
     *
     * @return the {@link PrimaryItemBox}, or null if not found
     */
    public PrimaryItemBox getPITM()
    {
        return getBox(HeifBoxType.PRIMARY_ITEM, PrimaryItemBox.class);
    }

    /**
     * Gets the {@link ItemInformationBox}, if present.
     *
     * @return the {@link ItemInformationBox}, or nullif not found
     */
    public ItemInformationBox getIINF()
    {
        return getBox(HeifBoxType.ITEM_INFO, ItemInformationBox.class);
    }

    /**
     * Gets the {@link ItemLocationBox}, if present.
     *
     * @return the {@link ItemLocationBox}, or null if not found
     */
    public ItemLocationBox getILOC()
    {
        return getBox(HeifBoxType.ITEM_LOCATION, ItemLocationBox.class);
    }

    /**
     * Gets the {@link ItemPropertiesBox}, if present.
     *
     * @return the {@link ItemPropertiesBox}, or null if not found
     */
    public ItemPropertiesBox getIPRP()
    {
        return getBox(HeifBoxType.ITEM_PROPERTIES, ItemPropertiesBox.class);
    }

    /**
     * Gets the {@link ItemReferenceBox}, if present.
     *
     * @return the {@link ItemReferenceBox}, or null if not found
     */
    public ItemReferenceBox getIREF()
    {
        return getBox(HeifBoxType.ITEM_REFERENCE, ItemReferenceBox.class);
    }

    /**
     * Gets the {@link ItemDataBox}, if present.
     *
     * @return the {@link ItemDataBox}, or null if not found
     */
    public ItemDataBox getIDAT()
    {
        return getBox(HeifBoxType.ITEM_DATA, ItemDataBox.class);
    }

    /**
     * Gets the {@link DataInformationBox}, if present.
     *
     * @return the {@link DataInformationBox}, or null if not found
     */
    public DataInformationBox getDINF()
    {
        return getBox(HeifBoxType.DATA_INFORMATION, DataInformationBox.class);
    }

    /**
     * Extracts the embedded Exif TIFF block linked to the primary image within the HEIF container.
     *
     * <p>
     * Supports multi-extent Exif data and correctly applies the TIFF header offset as specified in
     * the Exif payload. If no Exif block is found, {@link Optional#empty()} is returned.
     * </p>
     *
     * <p>
     * The returned byte array starts at the TIFF header, excluding the standard Exif identifier
     * prefix (usually {@code Exif\0\0}). According to <b>ISO/IEC 23008-12:2017 Annex A (p. 37)</b>,
     * the first 4 bytes of the Exif item payload contain {@code exifTiffHeaderOffset}, which
     * specifies the offset from the start of the payload to the TIFF header.
     * </p>
     *
     * <p>
     * The TIFF header typically begins with two magic bytes indicating byte order:
     * </p>
     *
     * <ul>
     * <li>{@code 0x4D 0x4D} – Motorola (big-endian)</li>
     * <li>{@code 0x49 0x49} – Intel (little-endian)</li>
     * </ul>
     *
     * @return an {@link Optional} containing the TIFF-compatible Exif block as a byte array if
     *         present, otherwise, {@link Optional#empty()}
     *
     * @throws IOException
     *         if the payload is unable to be computed due to an I/O error
     */
    public Optional<byte[]> getExifData() throws IOException
    {
        int exifId = findMetadataID(MetadataType.EXIF);

        if (exifId <= 0)
        {
            return Optional.empty();
        }

        byte[] payload = getRawItemData(exifId);

        if (payload == null || payload.length < 8)
        {
            return Optional.empty();
        }

        // 1. Check for "Exif\0\0" string (common in non-standard HEIF)
        if (payload[0] == 'E' && payload[1] == 'x' && payload[2] == 'i' && payload[3] == 'f')
        {
            // Skip "Exif\0\0" (6 bytes) and return the rest
            return Optional.of(Arrays.copyOfRange(payload, 6, payload.length));
        }

        // 2. ISO Standard: First 4 bytes is a 32-bit offset to the TIFF header
        int tiffOffset = ByteValueConverter.toInteger(payload, HEIF_BYTE_ORDER);

        // Safety check: Ensure the offset isn't 2 billion (like 'Exif')
        if (tiffOffset < 0 || tiffOffset >= payload.length)
        {
            // Fallback: If offset is garbage, search for TIFF magic bytes
            return findTiffHeader(payload);
        }

        return Optional.of(Arrays.copyOfRange(payload, tiffOffset + 4, payload.length));
    }

    private Optional<byte[]> findTiffHeader(byte[] data)
    {
        for (int i = 0; i < data.length - 4; i++)
        {
            // Look for 'II' (Intel) or 'MM' (Motorola)
            if ((data[i] == 0x49 && data[i + 1] == 0x49 && data[i + 2] == 0x2A)
                    || (data[i] == 0x4D && data[i + 1] == 0x4D && data[i + 2] == 0x00))
            {
                return Optional.of(Arrays.copyOfRange(data, i, data.length));
            }
        }

        return Optional.empty();
    }

    public Optional<byte[]> getExifData2() throws IOException
    {
        int exifId = findMetadataID(MetadataType.EXIF);

        if (exifId > 0)
        {
            byte[] payload = getRawItemData(exifId);

            if (payload != null && payload.length >= 4)
            {
                // ISO/IEC 23008-12: First 4 bytes = offset to TIFF header
                int tiffOffset = ByteValueConverter.toInteger(payload, HEIF_BYTE_ORDER);

                return Optional.of(Arrays.copyOfRange(payload, tiffOffset + 4, payload.length));
            }
        }

        return Optional.empty();
    }

    /**
     * Extracts the raw XMP metadata entries from the HEIF container.
     *
     * @return an Optional containing the XMP bytes, or Optional.empty() if not found
     *
     * @throws IOException
     *         if reading the file fails
     */
    public Optional<byte[]> getXmpData() throws IOException
    {
        int xmpId = findMetadataID(MetadataType.XMP);

        if (xmpId > 0)
        {
            byte[] payload = getRawItemData(xmpId);

            // XMP is typically raw UTF-8 XML without the 4-byte HEIF header used by Exif
            return Optional.of(payload);
        }

        return Optional.empty();
    }

    public Optional<byte[]> getXmpData2() throws IOException
    {
        int xmpId = findMetadataID(MetadataType.XMP);

        if (xmpId != -1)
        {
            ItemLocationBox iloc = getILOC();

            if (iloc == null)
            {
                return Optional.empty();
            }

            ItemLocationBox.ItemLocationEntry entry = iloc.findItem(xmpId);

            if (entry == null)
            {
                return Optional.empty();
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
            {
                for (ExtentData extent : entry.getExtents())
                {
                    baos.write(readExtent(entry, extent));
                }

                // XMP is typically raw UTF-8 XML without the 4-byte HEIF header used by Exif
                return Optional.of(baos.toByteArray());
            }
        }

        return Optional.empty();
    }

    /**
     * Extracts the thumbnail image bytes linked to the primary image.
     * 
     * @return an Optional containing the raw image bytes (often JPEG), or Optional.empty() if no
     *         thumbnail is linked
     * 
     * @throws IOException
     *         * if an I/O error occurs during extraction
     */
    public Optional<byte[]> getThumbnailData() throws IOException
    {
        PrimaryItemBox pitm = getPITM();
        ItemReferenceBox iref = getIREF();

        if (pitm != null && iref != null)
        {
            int pid = (int) pitm.getItemID();

            // Search the iref box for "thmb" links pointing to the primary ID
            List<Integer> thumbIds = iref.findLinksTo("thmb", pid);

            if (!thumbIds.isEmpty())
            {
                // Usually, there is only one thumbnail, so we take the first ID
                byte[] data = getRawItemData(thumbIds.get(0));

                return Optional.ofNullable(data);
            }
        }
        return Optional.empty();
    }

    /**
     * Displays all box types in a hierarchical fashion, useful for debugging, visualisation or
     * diagnostics.
     */
    public void displayHierarchy()
    {
        int currentDepth = -1;
        StringBuilder indent = new StringBuilder();

        LOGGER.debug("HEIF Box Hierarchy:");

        for (Box box : this)
        {
            int depth = box.getHierarchyDepth();

            if (depth != currentDepth)
            {
                indent.setLength(0);

                for (int i = 0; i < depth; i++)
                {
                    indent.append("  ");
                }

                currentDepth = depth;
            }

            LOGGER.debug(indent.toString() + box.getFourCC() + " (Size: " + box.getBoxSize() + ")");
        }
    }

    /**
     * Returns a depth-first iterator over all parsed boxes, starting from root boxes.
     *
     * <p>
     * The iteration respects the hierarchy of boxes, processing children before siblings
     * (depth-first traversal).
     * </p>
     *
     * @return an {@link Iterator} for recursively visiting all boxes
     */
    @Override
    public Iterator<Box> iterator()
    {
        return new Iterator<Box>()
        {
            private final Deque<Box> stack = new ArrayDeque<>();

            // Instance initialiser block
            {
                for (int i = rootBoxes.size() - 1; i >= 0; i--)
                {
                    stack.push(rootBoxes.get(i));
                }
            }

            @Override
            public boolean hasNext()
            {
                return !stack.isEmpty();
            }

            @Override
            public Box next()
            {
                Box current = stack.pop();
                List<Box> children = current.getBoxList();

                if (children != null)
                {
                    for (int i = children.size() - 1; i >= 0; i--)
                    {
                        stack.push(children.get(i));
                    }
                }

                return current;
            }
        };
    }

    /**
     * Closes the underlying ImageHandler object.
     */
    @Override
    public void close() throws IOException
    {
        if (reader != null)
        {
            reader.close();
        }
    }

    /**
     * Parses all HEIF boxes from the stream and builds the internal box tree structure, used to
     * extract metadata from the HEIF container.
     *
     * <p>
     * This method skips un-handled types such as {@code mdat} and gracefully recovers from
     * malformed boxes using a fail-fast approach.
     * </p>
     *
     * <p>
     * After calling this method, you can retrieve the extracted Exif block (if present) by invoking
     * {@link #getExifData()} or {@link #getXmpData()}.
     * </p>
     *
     * @return true if at least one HEIF box was successfully extracted, or false if no relevant
     *         boxes were found
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    @Override
    public boolean parseMetadata() throws IOException
    {
        Box box = null;

        while (reader.getCurrentPosition() < reader.length())
        {
            try
            {
                box = BoxFactory.createBox(reader);

                /*
                 * At this stage, no handler for processing data within the Media Data box (mdat) is
                 * available, since we are not interested in parsing it yet. This box will be
                 * skipped as not handled. Often, mdat is the last top-level box.
                 */
                if (HeifBoxType.MEDIA_DATA.equalsTypeName(box.getFourCC()))
                {
                    reader.skip(box.available(reader));
                    LOGGER.warn("Unhandled Media Data box [" + box.getFourCC() + "] skipped");
                }

                rootBoxes.add(box);
                walkBoxes(box, 0);
            }

            catch (Exception exc)
            {
                LOGGER.error("Error message received: [" + exc.getMessage() + "]");
                LOGGER.error("Malformed box structure detected in [" + box.getFourCC() + "]");
                exc.printStackTrace();
                break;
            }
        }

        return (!heifBoxMap.isEmpty());
    }

    /**
     * Recursively traverses the HEIF box hierarchy, adding each encountered box to the internal
     * {@code heifBoxMap}.
     *
     * <p>
     * This method is used internally by the {@link #parseMetadata()} method to build a
     * comprehensive map of all boxes and their relationships within the HEIF file.
     * </p>
     *
     * @param box
     *        the current {@link Box} object to process. This box and its children will be added to
     *        the internal map
     * @param depth
     *        the current depth in the box hierarchy, primarily used for debugging/visualisation
     *        purposes
     */
    private void walkBoxes(Box box, int depth)
    {
        List<Box> children = box.getBoxList();

        box.setHierarchyDepth(depth);
        heifBoxMap.putIfAbsent(box.getHeifType(), new ArrayList<>());
        heifBoxMap.get(box.getHeifType()).add(box);

        if (children != null)
        {
            for (Box child : children)
            {
                child.setParent(box);
                walkBoxes(child, depth + 1);
            }
        }
    }

    /**
     * Extracts raw bytes from fragmented data extents belonging to the specified Item ID. This also
     * supports both Construction Method 0 (Offset) and Construction Method 1 (IDAT) automatically.
     */
    private byte[] getRawItemData(int itemID) throws IOException
    {
        ItemLocationBox iloc = getILOC();

        if (iloc != null)
        {
            ItemLocationEntry entry = iloc.findItem(itemID);

            if (entry != null)
            {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
                {
                    for (ExtentData extent : entry.getExtents())
                    {
                        baos.write(readExtent(entry, extent));
                    }

                    return baos.toByteArray();
                }
            }
        }

        return null;
    }

    /**
     * Reads a specific extent of data from the underlying data source based on the item's specified
     * construction method.
     *
     * <p>
     * This method implements the data retrieval logic defined in <b>ISO/IEC 14496-12</b>. It
     * handles the two primary ways HEIF items are stored:
     * </p>
     *
     * <ul>
     * <li><b>Method 0 (File Offset):</b> Data is stored directly in the file (typically within an
     * {@code mdat} box). The absolute position is calculated as the sum of the entry's
     * {@code baseOffset} and the extent's {@code extentOffset}.</li>
     *
     * <li><b>Method 1 (IDAT Relative):</b> Data is stored within the {@code idat} (Item Data) box.
     * The {@code extentOffset} is treated as a zero-based index into the {@code idat} box's data
     * payload.</li>
     * </ul>
     *
     * @param entry
     *        the {@link ItemLocationEntry} providing the construction method
     *        and base offset for the item
     * @param extent
     *        the {@link ExtentData} providing the specific offset and length for this data segment
     * @return a byte array containing the raw data for the specified extent
     *
     * @throws IOException
     *         if an I/O error occurs, if Method 1 is specified but no {@code idat} box exists, or
     *         if the requested range is out of bounds
     */
    private byte[] readExtent(ItemLocationEntry entry, ExtentData extent) throws IOException
    {
        int constructionMethod = entry.getConstructionMethod();
        int length = extent.getExtentLength();
        long offset = extent.getExtentOffset();

        if (constructionMethod == 1)
        {
            ItemDataBox idat = getIDAT();

            if (idat == null)
            {
                throw new IOException("Item uses Method 1 (IDAT) but no idat box was found");
            }

            byte[] fullData = idat.getData();

            // Safety Check: Ensure the requested extent actually exists within the IDAT array
            if (offset < 0 || length < 0 || (offset + length) > fullData.length)
            {
                throw new IOException(String.format("IDAT access out of bounds [offset: %d, length: %d], but IDAT size is [%d]", offset, length, fullData.length));
            }

            byte[] data = new byte[length];

            System.arraycopy(fullData, (int) offset, data, 0, length);

            return data;
        }

        else
        {
            // Method 0: Absolute File Offset
            long absOffset = entry.getBaseOffset() + offset;

            if (absOffset + length > reader.length())
            {
                throw new IOException("Extent points beyond the end of the file structure");
            }

            return reader.peek(absOffset, length);
        }
    }

    /**
     * Retrieves the ID of a specific metadata segment (i.e. Exif or XMP) linked to the primary
     * image.
     *
     * <p>
     * <b>Search Logic:</b>
     * </p>
     *
     * <ol>
     * <li><b>Primary Linkage (Strict):</b> Searches the {@code iref} (Item Reference) box for
     * {@code cdsc} (content describes) references where the {@code to_item_ID} is the Primary Item
     * ID.</li>
     * <li><b>Validation:</b> If the required reference is identified, verifies the item type in
     * {@code iinf}. If XMP exists, it further validates the content type is
     * {@code application/rdf+xml} as per ISO/IEC 23008-12.</li>
     * <li><b>Fallback:</b> If no explicit reference exists in {@code iref}, performs a type-based
     * global scan of the {@code iinf} (Item Information) box. It may be less accurate.</li>
     * </ol>
     *
     * @param type
     *        the metadata category to find
     * @return the {@code item_id} of the metadata, or -1 if not found
     */
    private int findMetadataID(MetadataType type)
    {
        ItemInformationBox iinf = getIINF();

        if (iinf != null)
        {
            PrimaryItemBox pitm = getPITM();
            ItemReferenceBox iref = getIREF();

            if (pitm != null && iref != null)
            {
                int pid = (int) pitm.getItemID();

                for (int itemID : iref.findLinksTo(IREF_CDSC, pid))
                {
                    Optional<ItemInfoEntry> entryOpt = iinf.getEntry(itemID);

                    if (entryOpt.isPresent())
                    {
                        ItemInfoEntry entry = entryOpt.get();

                        if (type == MetadataType.EXIF && TYPE_EXIF.equals(entry.getItemType()))
                        {
                            return itemID;
                        }

                        else if (type == MetadataType.XMP && TYPE_MIME.equals(entry.getItemType()) && "application/rdf+xml".equalsIgnoreCase(entry.getContentType()))
                        {
                            return itemID;
                        }
                    }

                    else
                    {
                        LOGGER.warn("Unable to find metadata due to an empty item entry");
                    }
                }
            }

            if (type == MetadataType.EXIF)
            {
                ItemInfoEntry entry = iinf.findEntryByType(TYPE_EXIF);

                if (entry != null)
                {
                    LOGGER.warn("Fallback Exif segment found using Item ID [" + entry.getItemID() + "]");
                    return entry.getItemID();
                }
            }

            if (type == MetadataType.XMP)
            {
                ItemInfoEntry entry = iinf.findEntryByType(TYPE_MIME);

                if (entry != null && "application/rdf+xml".equalsIgnoreCase(entry.getContentType()))
                {
                    LOGGER.warn("Fallback XMP segment found using Item ID [" + entry.getItemID() + "]");
                    return entry.getItemID();
                }
            }
        }

        return -1;
    }

    /**
     * Retrieves the first matching box of a specific type and class.
     *
     * @param <T>
     *        the generic box type
     * @param type
     *        the box type identifier
     * @param clazz
     *        the expected box class
     *
     * @return the matching box, or {@code null} if not present or of the wrong type
     */
    @SuppressWarnings("unchecked")
    private <T extends Box> T getBox(HeifBoxType type, Class<T> clazz)
    {
        List<Box> boxes = heifBoxMap.get(type);

        if (boxes != null)
        {
            for (Box box : boxes)
            {
                if (clazz.isInstance(box))
                {
                    return (T) box;
                }
            }
        }

        return null;
    }
}