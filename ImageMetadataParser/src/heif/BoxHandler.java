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
import heif.boxes.ItemInformationBox;
import heif.boxes.ItemLocationBox;
import heif.boxes.ItemLocationBox.ExtentData;
import heif.boxes.ItemPropertiesBox;
import heif.boxes.ItemReferenceBox;
import heif.boxes.PrimaryItemBox;
import logger.LogFactory;

/**
 * Handles parsing of HEIF/HEIC file structures based on the ISO Base Media Format.
 *
 * Supports Exif extraction, box navigation, and hierarchical parsing.
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
    public static final ByteOrder HEIF_BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private final Map<HeifBoxType, List<Box>> heifBoxMap = new LinkedHashMap<>();
    private final List<Box> rootBoxes = new ArrayList<>();
    private final ByteStreamReader reader;
    private final Path imageFile;

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
        this.imageFile = fpath;
        this.reader = new ImageRandomAccessReader(fpath, HEIF_BYTE_ORDER);
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
     * Extracts the embedded Exif TIFF block from the HEIF container.
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
     *         if the payload is unable to be computed
     * @throws IllegalStateException
     *         if the Exif block is missing, malformed, or cannot be located
     */
    public Optional<byte[]> getExifData() throws IOException
    {
        Optional<List<ExtentData>> optionalExif = getExifExtents();

        if (!optionalExif.isPresent())
        {
            return Optional.empty();
        }

        List<ExtentData> extents = optionalExif.get();

        // The first extent is handled separately due to the TIFF header offset
        ItemLocationBox.ItemLocationEntry entry = getILOC().findItem(extents.get(0).getItemID());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
        {
            for (ExtentData extent : extents)
            {
                baos.write(readExtent(entry, extent));
            }

            byte[] payload = baos.toByteArray();

            if (payload.length < 8)
            {
                throw new IllegalStateException("Exif payload too small for header");
            }

            int offset = ByteValueConverter.toInteger(payload, HEIF_BYTE_ORDER);

            if (payload.length < offset + 4)
            {
                throw new IllegalStateException("Malformed Exif payload: offset exceeds data length");
            }

            byte[] tiffBlock = Arrays.copyOfRange(payload, offset + 4, payload.length);

            return Optional.of(tiffBlock);
        }
    }

    public Optional<byte[]> getExifData2() throws IOException
    {
        Optional<List<ExtentData>> optionalExif = getExifExtents();

        if (!optionalExif.isPresent())
        {
            return Optional.empty();
        }

        List<ExtentData> extents = optionalExif.get();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
        {
            // The first extent is handled separately due to the TIFF header offset
            ExtentData firstExtent = extents.get(0);
            reader.mark();
            reader.seek(firstExtent.getExtentOffset());

            if (firstExtent.getExtentLength() < 8)
            {
                throw new IllegalStateException("Extent too small to contain Exif header in [" + imageFile + "]");
            }

            int exifTiffHeaderOffset = reader.readInteger();

            if (firstExtent.getExtentLength() < exifTiffHeaderOffset + 4)
            {
                throw new IllegalStateException("Invalid TIFF header offset for Exif block in [" + imageFile + "]");
            }

            // Skip to the TIFF header, excluding the offset field
            reader.skip(exifTiffHeaderOffset);

            // Note: 4 bytes used to store that integer are part of the extent length
            int payloadLength = firstExtent.getExtentLength() - exifTiffHeaderOffset - 4;

            baos.write(reader.peek(reader.getCurrentPosition(), payloadLength));
            reader.reset();

            // Process any subsequent extents
            for (int i = 1; i < extents.size(); i++)
            {
                ExtentData extent = extents.get(i);

                reader.mark();
                reader.seek((int) extent.getExtentOffset());
                baos.write(reader.peek(reader.getCurrentPosition(), extent.getExtentLength()));
                reader.reset();
            }

            // Return the combined payload if data was found
            if (baos.size() > 0)
            {
                return Optional.of(baos.toByteArray());
            }
        }

        return Optional.empty();
    }

    /**
     * Extracts the raw XMP metadata string from the HEIF container.
     *
     * @return an Optional containing the XMP string, or Optional.empty() if not found
     * @throws IOException
     *         if reading the file fails
     */
    public Optional<String> getXmpData() throws IOException
    {
        ItemInformationBox iinf = getIINF();

        if (iinf == null)
        {
            return Optional.empty();
        }

        int xmpID = iinf.findXmpItemID();

        if (xmpID == -1)
        {
            return Optional.empty();
        }

        ItemLocationBox iloc = getILOC();

        ItemLocationBox.ItemLocationEntry itemEntry = iloc.findItem(xmpID);

        if (itemEntry == null)
        {
            return Optional.empty();
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
        {
            for (ExtentData extent : itemEntry.getExtents())
            {
                baos.write(readExtent(itemEntry, extent));

            }

            return Optional.of(new String(baos.toByteArray(), StandardCharsets.UTF_8).trim());
        }
    }

    private byte[] readExtent(ItemLocationBox.ItemLocationEntry entry, ExtentData extent) throws IOException
    {
        if (entry.getConstructionMethod() == 1)
        {
            ItemDataBox idat = getIDAT();

            if (idat == null)
            {
                throw new IOException("Item uses Method 1 (IDAT) but no idat box found.");
            }

            byte[] data = new byte[extent.getExtentLength()];

            System.arraycopy(idat.getData(), (int) extent.getExtentOffset(), data, 0, extent.getExtentLength());

            return data;
        }

        else
        {
            // Method 0: Absolute File Offset
            long absoluteOffset = entry.getBaseOffset() + extent.getExtentOffset();

            return reader.peek(absoluteOffset, extent.getExtentLength());
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
     * Parses all HEIF boxes from the stream using {@link BoxFactory#createBox} and builds the
     * internal box tree structure, used to extract metadata from the HEIF container..
     *
     * <p>
     * This method skips un-handled types such as {@code mdat} and gracefully recovers from
     * malformed boxes using a fail-fast approach.
     * </p>
     * 
     * <p>
     * After calling this method, you can retrieve the extracted Exif block (if present) by invoking
     * {@link #getExifData()}.
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
        while (reader.getCurrentPosition() < reader.length())
        {
            try
            {
                Box box = BoxFactory.createBox(reader);

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
                LOGGER.error("Parsing interrupted due to corrupted box structure [" + exc.getMessage() + "]");
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
     * This method is used internally by the {@link #parse()} method to build a comprehensive map of
     * all boxes and their relationships within the HEIF file.
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
     * Retrieves the list of {@link ExtentData} corresponding to the Exif block, if present.
     *
     * @return an {@link Optional} containing the list of extents for Exif data, or
     *         {@link Optional#empty()} if it does not exist
     */
    private Optional<List<ExtentData>> getExifExtents()
    {
        ItemLocationBox iloc = getILOC();
        ItemInformationBox iinf = getIINF();

        if (iinf == null || !iinf.containsExif())
        {
            LOGGER.warn("Exif metadata item not defined in ItemInformationBox for [" + imageFile + "]");
            return Optional.empty();
        }

        int exifID = iinf.findExifItemID();
        List<ExtentData> extents = (iloc != null) ? iloc.findExtentsForItem(exifID) : null;

        if (extents == null || extents.isEmpty())
        {
            LOGGER.warn("No location extents found for Exif ID [" + exifID + "] in [" + imageFile + "]");
            return Optional.empty();
        }

        return Optional.of(extents);
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

    private Optional<List<ExtentData>> getExifExtents2()
    {
        ItemInformationBox iinf = getIINF();
        ItemLocationBox iloc = getILOC();
        PrimaryItemBox pitm = getPITM();
        ItemReferenceBox iref = getIREF();

        if (iinf == null || iloc == null)
        {
            return Optional.empty();
        }

        int targetExifId = -1;

        // 1. Primary Method: Use PITM + IREF to find the Exif linked to the master image
        if (pitm != null && iref != null)
        {
            long primaryId = pitm.getItemID();
            targetExifId = findMetadataIdForImage(primaryId, "cdsc", "Exif");
        }

        // 2. Fallback: If no reference exists, look for any Exif item in the IINF
        if (targetExifId == -1)
        {
            targetExifId = iinf.findExifItemID();
        }

        // 3. Data Retrieval
        if (targetExifId == -1)
        {
            LOGGER.warn("Exif metadata item not found for [" + imageFile + "]");
            return Optional.empty();
        }

        List<ExtentData> extents = iloc.findExtentsForItem(targetExifId);
        
        if (extents == null || extents.isEmpty())
        {
            LOGGER.warn("No location extents found for Exif ID [" + targetExifId + "]");
            return Optional.empty();
        }

        return Optional.of(extents);
    }

    private int findMetadataIdForImage(long imageId, String refType, String itemType)
    {
        ItemReferenceBox iref = getIREF();
        ItemInformationBox iinf = getIINF();

        if (iref == null || iinf == null) return -1;

        for (Box box : iref.getReferences())
        {
            if (box instanceof ItemReferenceBox.SingleItemTypeReferenceBox)
            {
                ItemReferenceBox.SingleItemTypeReferenceBox ref = (ItemReferenceBox.SingleItemTypeReferenceBox) box;

                // Look for "cdsc" (content description) references
                if (refType.equals(ref.getTypeAsString()))
                {
                    for (long toId : ref.getToItemIDs())
                    {
                        if (toId == imageId)
                        {
                            int potentialId = (int) ref.getFromItemID();
                            // Verify this metadata item is actually the right type (Exif vs XMP)
                            if (itemType.equals(iinf.getItemType(potentialId)))
                            {
                                return potentialId;
                            }
                        }
                    }
                }
            }
        }
        return -1;
    }
}