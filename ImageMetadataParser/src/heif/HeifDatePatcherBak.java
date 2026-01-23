package heif;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import heif.BoxHandler.MetadataType;
import tif.DirectoryIFD;
import tif.DirectoryIFD.EntryIFD;
import tif.TifMetadata;
import tif.TifParser;
import tif.tagspecs.TagIFD_Baseline;
import tif.tagspecs.TagIFD_Exif;
import tif.tagspecs.TagIFD_GPS;
import tif.tagspecs.Taggable;

public final class HeifDatePatcherBak
{
    private static final Map<Taggable, DateTimeFormatter> EXIF_TAG_FORMATS;
    private static final DateTimeFormatter EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter GPS_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ENGLISH);
    private static final DateTimeFormatter XMP_LONG = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter XMP_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    static
    {
        EXIF_TAG_FORMATS = new HashMap<>();
        EXIF_TAG_FORMATS.put(TagIFD_Baseline.IFD_DATE_TIME, EXIF_FORMATTER);
        EXIF_TAG_FORMATS.put(TagIFD_Exif.EXIF_DATE_TIME_ORIGINAL, EXIF_FORMATTER);
        EXIF_TAG_FORMATS.put(TagIFD_Exif.EXIF_DATE_TIME_DIGITIZED, EXIF_FORMATTER);
        EXIF_TAG_FORMATS.put(TagIFD_GPS.GPS_DATE_STAMP, GPS_FORMATTER);
    }

    /**
     * Default constructor is unsupported and will always throw an exception.
     *
     * @throws UnsupportedOperationException
     *         to indicate that instantiation is not supported
     */
    private HeifDatePatcherBak()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }

    public static void updateAllMetadataDates(Path path, FileTime newDate) throws IOException
    {
        ZonedDateTime zdt = newDate.toInstant().atZone(ZoneId.systemDefault());

        try (BoxHandler handler = new BoxHandler(path))
        {
            if (handler.parseMetadata())
            {
                try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw"))
                {
                    patchExif(handler, raf, zdt);
                    patchXmp(handler, raf, zdt);
                }
            }
        }
    }

    private static void patchExif(BoxHandler handler, RandomAccessFile raf, ZonedDateTime zdt) throws IOException
    {
        Optional<byte[]> exifData = handler.getExifData();
        int exifId = handler.findMetadataID(BoxHandler.MetadataType.EXIF);

        if (exifId != -1 && exifData.isPresent())
        {
            TifMetadata metadata = TifParser.parseTiffMetadataFromBytes(exifData.get());

            for (DirectoryIFD dir : metadata)
            {
                for (Map.Entry<Taggable, DateTimeFormatter> formatEntry : EXIF_TAG_FORMATS.entrySet())
                {
                    Taggable tag = formatEntry.getKey();

                    if (dir.hasTag(tag))
                    {
                        EntryIFD entry = dir.getTagEntry(tag);
                        long physicalPos = handler.getPhysicalAddress(exifId, entry.getOffset(), MetadataType.EXIF);

                        if (physicalPos != -1)
                        {
                            // Generate the value using the tag-specific formatter
                            String value = zdt.format(formatEntry.getValue());
                            byte[] dateBytes = (value + "\0").getBytes(StandardCharsets.US_ASCII);

                            int limit = (int) entry.getCount();
                            byte[] output = new byte[limit];
                            System.arraycopy(dateBytes, 0, output, 0, Math.min(dateBytes.length, limit));
                            output[limit - 1] = 0; // Maintain null terminator integrity

                            raf.seek(physicalPos);
                            raf.write(output);
                            System.out.println("Patched Exif Tag: " + tag + " -> " + value);
                        }
                    }
                }
            }
        }
    }

    private static void patchXmp(BoxHandler handler, RandomAccessFile raf, ZonedDateTime zdt) throws IOException
    {
        Optional<byte[]> xmpData = handler.getXmpData();
        int xmpId = handler.findMetadataID(BoxHandler.MetadataType.XMP);
        String[] tags = {"xmp:CreateDate", "xmp:ModifyDate", "xmp:MetadataDate"};

        if (xmpId != -1 && xmpData.isPresent())
        {
            String content = new String(xmpData.get(), StandardCharsets.UTF_8);

            for (String tag : tags)
            {
                int tagIdx = content.indexOf(tag);

                while (tagIdx != -1)
                {
                    int vStart = findValueBoundary(content, tagIdx, true);
                    int vEnd = findValueBoundary(content, vStart, false);

                    if (vStart > 0 && vEnd > vStart)
                    {
                        long physicalPos = handler.getPhysicalAddress(xmpId, vStart, MetadataType.XMP);

                        if (physicalPos != -1)
                        {
                            /* Force exact width match: Pad or Truncate in finalPatch */
                            int width = vEnd - vStart;
                            String patch = (width >= 25) ? zdt.format(XMP_LONG) : zdt.format(XMP_SHORT);
                            byte[] finalPatch = String.format("%-" + width + "s", patch).substring(0, width).getBytes(StandardCharsets.UTF_8);

                            raf.seek(physicalPos);
                            raf.write(finalPatch);

                            System.out.println("Patched XMP Tag: " + tag);
                        }
                    }

                    tagIdx = content.indexOf(tag, vEnd);
                }
            }
        }
    }

    private static int findValueBoundary(String content, int startIdx, boolean isStart)
    {
        int bracket = content.indexOf(isStart ? ">" : "<", startIdx);
        int quote = content.indexOf("\"", startIdx);

        if (quote != -1 && (bracket == -1 || quote < bracket))
        {
            return isStart ? quote + 1 : quote;
        }

        return isStart ? bracket + 1 : bracket;
    }

    // TESTING
    public static void exportXmpForDebug(Path heicPath) throws IOException
    {
        try (RandomAccessFile raf = new RandomAccessFile(heicPath.toFile(), "r"))
        {
            byte[] buffer = new byte[(int) raf.length()];
            raf.readFully(buffer);

            // Use UTF-8 to ensure we don't corrupt multi-byte characters
            String content = new String(buffer, StandardCharsets.UTF_8);

            int start = content.indexOf("<?xpacket");
            // Find the last occurrence of the XMP meta closing tag to ensure we get the whole block
            int xmpMetaEnd = content.lastIndexOf("</x:xmpmeta>");

            if (start != -1 && xmpMetaEnd != -1)
            {
                // Find the packet closing marker '?>' after the closing tag
                int end = content.indexOf("?>", xmpMetaEnd);

                if (end != -1)
                {
                    String xmpData = content.substring(start, end + 2);
                    Path outputPath = Paths.get("debug_xmp.xml");

                    // Java 8 equivalent of Files.writeString
                    Files.write(outputPath, xmpData.getBytes(StandardCharsets.UTF_8));

                    System.out.println("Exported XMP to " + outputPath.toAbsolutePath());
                    System.out.println("Open this file in Chrome or Edge. If it doesn't show a clean XML tree, the structure is broken.");
                }
            }

            else
            {
                System.out.println("Could not find XMP packet markers in the file.");
            }
        }
    }
}