package heif;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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
import heif.boxes.ItemReferenceBox.SingleItemTypeReferenceBox;
import heif.boxes.PrimaryItemBox;
import logger.LogFactory;

/**
 * Handles parsing of HEIF/HEIC file structures based on the ISO Base Media Format.
 *
 * Supports Exif/XMP extraction, box navigation, and hierarchical parsing.
 *
 * <p>
 * For detailed specifications, see:
 * </p>
 *
 * <ul>
 * <li>{@code ISO/IEC 14496-12:2015}</li>
 * <li>{@code ISO/IEC 23008-12:2017}</li>
 * </ul>
 *
 * <p>
 * <strong>API Note:</strong> According to HEIF/HEIC standards, some box types are
 * optional and may appear zero or one time per file.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class BoxHandler implements ImageHandler, AutoCloseable, Iterable<Box>
{
    private static final LogFactory LOGGER = LogFactory.getLogger(BoxHandler.class);
    private static final String IREF_CDSC = "cdsc";
    public static final ByteOrder HEIF_BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private final Map<HeifBoxType, List<Box>> heifBoxMap = new LinkedHashMap<>();
    private final List<Box> rootBoxes = new ArrayList<>();
    private final ByteStreamReader reader;

    private enum MetadataType
    {
        EXIF("Exif"), XMP("mime"), OTHER("Other");

        private final String metatype;

        private MetadataType(String type)
        {
            this.metatype = type;
        }
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

        if (exifId != -1)
        {
            ItemLocationBox iloc = getILOC();

            if (iloc != null)
            {
                ItemLocationBox.ItemLocationEntry entry = iloc.findItem(exifId);

                if (entry != null)
                {
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
                    {
                        for (ExtentData extent : entry.getExtents())
                        {
                            baos.write(readExtent(entry, extent));
                        }

                        byte[] payload = baos.toByteArray();

                        if (payload.length >= 4)
                        {

                            // ISO/IEC 23008-12: First 4 bytes = offset to TIFF header
                            int tiffOffset = ByteValueConverter.toInteger(payload, HEIF_BYTE_ORDER);

                            if (payload.length < tiffOffset + 4)
                            {
                                throw new IllegalStateException("Exif offset exceeds payload length");
                            }

                            return Optional.of(Arrays.copyOfRange(payload, tiffOffset + 4, payload.length));
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Extracts the raw XMP metadata string from the HEIF container.
     *
     * @return an Optional containing the XMP string, or Optional.empty() if not found
     *
     * @throws IOException
     *         if reading the file fails
     */
    public Optional<String> getXmpData() throws IOException
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
                return Optional.of(new String(baos.toByteArray(), StandardCharsets.UTF_8).trim());
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

            LOGGER.debug(indent.toString() + box.getTypeAsString() + " (Size: " + box.getBoxSize() + ")");
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
                if (HeifBoxType.MEDIA_DATA.equalsTypeName(box.getTypeAsString()))
                {
                    reader.skip(box.available());
                    LOGGER.warn("Unhandled Media Data box [" + box.getTypeAsString() + "] skipped");
                }

                rootBoxes.add(box);
                walkBoxes(box, 0);
            }

            catch (Exception exc)
            {
                LOGGER.error("Error message received: [" + exc.getMessage() + "]");
                LOGGER.error("Malformed box structure detected in [" + box.getTypeAsString() + "]");
                // exc.printStackTrace();
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
     * Retrieves the ID of a specific metadata segment, for example: Exif or XMP, linked to the
     * primary image.
     *
     * <p>
     * <b>Check flow</b>
     * </p>
     *
     * <ol>
     * <li>Primary Linkage (Strict): Searches the {@code iref} (Item Reference) box for {@code cdsc}
     * (content describes) references where the target (toID) is the Primary Item ID.</li>
     * <li>XMP Validation: If searching for XMP, verifies the item's content type is
     * {@code application/rdf+xml} as per ISO/IEC 23008-12.</li>
     * <li>Fallback: If no explicit reference exists in {@code iref}, performs a type-based scan of
     * the {@code iinf} (Item Information) box.</li>
     * </ol>
     *
     * @param type
     *        the metadata category to find, i.e. Exif, XMP etc
     * @return the item_id of the metadata, or -1 if not found
     */
    private int findMetadataID(MetadataType type)
    {
        PrimaryItemBox pitm = getPITM();
        ItemReferenceBox iref = getIREF();
        ItemInformationBox iinf = getIINF();

        if (pitm == null || iinf == null)
        {
            return -1;
        }

        if (iref != null)
        {
            long pid = pitm.getItemID();

            for (Box box : iref.getReferences())
            {
                if (box instanceof SingleItemTypeReferenceBox)
                {
                    SingleItemTypeReferenceBox ref = (SingleItemTypeReferenceBox) box;

                    if (IREF_CDSC.equals(ref.getTypeAsString()))
                    {
                        for (long toId : ref.getToItemIDs())
                        {
                            if (toId == pid)
                            {
                                int potentialId = (int) ref.getFromItemID();

                                if (type.metatype.equals(iinf.getItemType(potentialId)))
                                {
                                    if (type == MetadataType.XMP)
                                    {
                                        Optional<ItemInfoEntry> entry = iinf.getEntry(potentialId);

                                        if (entry.isPresent() && "application/rdf+xml".equalsIgnoreCase(entry.get().getContentType()))
                                        {
                                            System.out.printf("LOOK: %s\t%s\n", IREF_CDSC, potentialId);

                                            return potentialId;
                                        }

                                        else
                                        {
                                            LOGGER.warn("Unable to locate XMP metadata due to an empty item entry");
                                        }
                                    }

                                    else
                                    {
                                        return potentialId;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return (type == MetadataType.XMP ? iinf.findXmpItemID() : iinf.findExifItemID());
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