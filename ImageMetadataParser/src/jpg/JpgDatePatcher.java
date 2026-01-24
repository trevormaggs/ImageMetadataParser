package jpg;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import logger.LogFactory;
import tif.DirectoryIFD;
import tif.DirectoryIFD.EntryIFD;
import tif.TifMetadata;
import tif.TifParser;
import tif.tagspecs.TagIFD_Baseline;
import tif.tagspecs.TagIFD_Exif;
import tif.tagspecs.TagIFD_GPS;
import tif.tagspecs.Taggable;

/**
 * Surgically patches EXIF (TIFF) and XMP (XML) dates in JPEG files. Overwrites bytes in-place to
 * maintain segment offsets and file integrity.
 *
 * @author Trevor Maggs
 * @version 1.4
 */
public final class JpgDatePatcher
{
    private static final LogFactory LOGGER = LogFactory.getLogger(JpgDatePatcher.class);
    private static final byte[] XMP_ID = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.UTF_8);
    private static final DateTimeFormatter EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter GPS_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ENGLISH);
    private static final DateTimeFormatter XMP_LONG = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter XMP_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Map<Taggable, DateTimeFormatter> EXIF_TAG_FORMATS;

    static
    {
        EXIF_TAG_FORMATS = new HashMap<>();
        EXIF_TAG_FORMATS.put(TagIFD_Baseline.IFD_DATE_TIME, EXIF_FORMATTER);
        EXIF_TAG_FORMATS.put(TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL, EXIF_FORMATTER);
        EXIF_TAG_FORMATS.put(TagIFD_Exif.EXIF_DATE_TIME_DIGITIZED, EXIF_FORMATTER);
        EXIF_TAG_FORMATS.put(TagIFD_GPS.GPS_DATE_STAMP, GPS_FORMATTER);
    }

    private final Path imagePath;

    public JpgDatePatcher(Path imagePath)
    {
        this.imagePath = imagePath;
    }

    public void patchAllDates(ZonedDateTime zdt) throws IOException
    {
        try (RandomAccessFile raf = new RandomAccessFile(imagePath.toFile(), "rw"))
        {
            patchMetadataSegments(raf, zdt);
        }
    }

    private void patchMetadataSegments(RandomAccessFile raf, ZonedDateTime zdt) throws IOException
    {
        raf.seek(0);

        while (raf.getFilePointer() < raf.length() - 4)
        {
            int marker = raf.readUnsignedByte();

            if (marker != 0xFF)
            {
                continue;
            }

            int flag = raf.readUnsignedByte();

            // APP1 Segment
            if (flag == 0xE1)
            {
                int length = raf.readUnsignedShort() - 2;
                long startPos = raf.getFilePointer();

                byte[] header = new byte[6];
                raf.readFully(header);
                String headerStr = new String(header, StandardCharsets.US_ASCII);

                if (headerStr.startsWith("Exif"))
                {
                    processExifSegment(raf, startPos, length, zdt);
                }

                else if (Arrays.equals(Arrays.copyOf(header, XMP_ID.length), XMP_ID))
                {
                    processXmpSegment(raf, startPos, length, zdt);
                }

                raf.seek(startPos + length);
            }

            else if (flag == 0xDA) // Start of Scan (End of metadata area)
            {
                break;
            }
        }
    }

    private void processExifSegment(RandomAccessFile raf, long startPos, int length, ZonedDateTime zdt) throws IOException
    {
        byte[] tiffPayload = new byte[length - 6];
        
        raf.seek(startPos + 6); // Skip "Exif\0\0"        
        raf.readFully(tiffPayload);

        TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(tiffPayload);
        
        for (DirectoryIFD dir : metadata)
        {
            for (Map.Entry<Taggable, DateTimeFormatter> formatEntry : EXIF_TAG_FORMATS.entrySet())
            {
                Taggable tag = formatEntry.getKey();
                
                if (dir.hasTag(tag))
                {
                    EntryIFD entry = dir.getTagEntry(tag);
                    long physicalPos = startPos + 6 + entry.getOffset();

                    String value = zdt.format(formatEntry.getValue());
                    byte[] dateBytes = (value + "\0").getBytes(StandardCharsets.US_ASCII);

                    raf.seek(physicalPos);
                    raf.write(dateBytes, 0, Math.min(dateBytes.length, (int) entry.getCount()));
                    
                    LOGGER.info("Patched EXIF tag [" + tag + "] at 0x" + Long.toHexString(physicalPos));
                }
            }
        }
    }

    private void processXmpSegment(RandomAccessFile raf, long startPos, int length, ZonedDateTime zdt) throws IOException
    {
        String[] xmpTags = {"xmp:CreateDate", "xmp:ModifyDate", "xmp:MetadataDate", "photoshop:DateCreated"};

        byte[] payload = new byte[length - XMP_ID.length];
        
        raf.seek(startPos + XMP_ID.length);
        raf.readFully(payload);

        String content = new String(payload, StandardCharsets.UTF_8);

        for (String tag : xmpTags)
        {
            int tagIdx = content.indexOf(tag);

            while (tagIdx != -1)
            {
                // Safety: ignore </xmp:CreateDate>
                if (tagIdx > 0 && content.charAt(tagIdx - 1) != '/')
                {
                    int[] span = findValueSpan(content, tagIdx);
                    
                    if (span != null && span[1] >= 10)
                    {
                        int width = span[1];
                        String patch = (width >= 25) ? zdt.format(XMP_LONG) : zdt.format(XMP_SHORT);
                        byte[] finalPatch = String.format("%-" + width + "s", patch).substring(0, width).getBytes(StandardCharsets.UTF_8);
                        long physicalPos = startPos + XMP_ID.length + span[0];
                        
                        raf.seek(physicalPos);
                        raf.write(finalPatch);
                        
                        LOGGER.info("Patched XMP tag [" + tag + "] at 0x" + Long.toHexString(physicalPos));
                    }
                }

                tagIdx = content.indexOf(tag, tagIdx + tag.length());
            }
        }
    }

    private static int[] findValueSpan(String content, int tagIdx)
    {
        int bracket = content.indexOf(">", tagIdx);
        int quote = content.indexOf("\"", tagIdx);

        boolean isAttr = (quote != -1 && (bracket == -1 || quote < bracket));
        int start = isAttr ? quote + 1 : bracket + 1;
        int end = content.indexOf(isAttr ? "\"" : "<", start);

        return (start > 0 && end > start) ? new int[]{start, end - start} : null;
    }
}