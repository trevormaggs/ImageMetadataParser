package batch;

import static tif.TagEntries.TagEXIF.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import common.AbstractImageParser;
import common.DateParser;
import common.DigitalSignature;
import common.ImageParserFactory;
import common.Metadata;
import common.SystemInfo;
import common.strategy.ExifMetadata;
import common.strategy.MetadataContext;
import logger.LogFactory;
import png.ChunkDirectory;
import png.ChunkType;
import png.MetadataPNG;
import png.PngChunk;
import png.PngDirectory;
import png.TextEntry;
import png.TextKeyword;
import tif.DirectoryIFD;
import tif.DirectoryIdentifier;
import tif.MetadataTIF;
import tif.TifParser;
import tif.TagEntries.TagPngChunk;

/**
 * Automates the batch processing of image files by copying, renaming, and chronologically sorting
 * them based on their EXIF metadata, such as {@code DateTimeOriginal}.
 *
 * <p>
 * This class supports a range of image formats, including JPEG, TIFF, PNG, WebP, and HEIF. If EXIF
 * metadata is not available, it defaults to using the file's last modified time-stamp.
 * </p>
 *
 * <p>
 * To access the sorted list of files, use the {@code Iterable<MediaFile>} interface.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public class BatchExecutor implements Batchable, Iterable<MediaFile>
{
    private static final LogFactory LOGGER = LogFactory.getLogger(BatchExecutor.class);
    private static final long TEN_SECOND_OFFSET_MS = 10_000L;
    private static final FileVisitor<Path> DELETE_VISITOR;
    public static final String DEFAULT_SOURCE_DIRECTORY = ".";
    public static final String DEFAULT_TARGET_DIRECTORY = "IMAGEDIR";
    public static final String DEFAULT_IMAGE_PREFIX = "image";
    private final String prefix;
    private final Path sourceDir;
    private final Path targetDir;
    private final Set<MediaFile> imageSet;
    private final boolean embedDateTime;
    private final boolean skipVideoFiles;
    private final boolean debug;
    private final String userDate;
    private final String[] fileSet;
    private long dateOffsetUpdate;

    static
    {
        DELETE_VISITOR = new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
            {
                if (exc == null)
                {
                    Files.delete(dir);
                }

                else
                {
                    throw exc;
                }

                return FileVisitResult.CONTINUE;
            }
        };
    }

    /**
     * Constructs a {@code BatchExecutor} using the provided {@link BatchBuilder} configuration.
     * This constructor is package-private and should be invoked through
     * {@link BatchBuilder#build()}.
     *
     * @param builder
     *        the builder object containing required parameters
     */
    protected BatchExecutor(BatchBuilder builder)
    {
        this.sourceDir = Paths.get(builder.bd_sourceDir);
        this.prefix = builder.bd_prefix;
        this.targetDir = Paths.get(builder.bd_target);
        this.embedDateTime = builder.bd_embedDateTime;
        this.skipVideoFiles = builder.bd_skipVideoFiles;
        this.debug = builder.bd_debug;
        this.userDate = builder.bd_userDate;
        this.fileSet = Arrays.copyOf(builder.bd_files, builder.bd_files.length);
        this.dateOffsetUpdate = 0L;

        if (builder.bd_descending)
        {
            // Sorts the copied images in descending order
            imageSet = new TreeSet<>(new DescendingTimestampComparator());
            LOGGER.info("Sorted copied images in descending order.");
        }

        else
        {
            // Sorts the copied images in ascending order
            imageSet = new TreeSet<>(new DefaultTimestampComparator());
            LOGGER.info("Sorted copied images in ascending order.");
        }
    }

    /**
     * Begins the batch processing workflow by cleaning the target directory, setting up logging,
     * and processing the specified source files or directory.
     *
     * @throws BatchErrorException
     *         if an I/O or metadata-related error occurs or the source directory is not a valid
     *         directory
     */
    protected void start() throws BatchErrorException
    {
        FileVisitor<Path> visitor = createImageVisitor();

        if (!Files.isDirectory(sourceDir))
        {
            throw new BatchErrorException("The source directory [" + sourceDir + "] is not a valid directory. Please verify that the path exists and is a directory");
        }

        try
        {
            if (Files.exists(targetDir))
            {
                /*
                 * Permanently deletes the target directory and all of its contents.
                 * This operation is destructive and cannot be undone.
                 */
                Files.walkFileTree(targetDir, DELETE_VISITOR);
            }

            Files.createDirectories(targetDir);

            startLogging();

            if (fileSet != null && fileSet.length > 0)
            {
                for (String fileName : fileSet)
                {
                    Path fpath = sourceDir.resolve(fileName);

                    if (Files.exists(fpath) && Files.isRegularFile(fpath))
                    {
                        visitor.visitFile(fpath, Files.readAttributes(fpath, BasicFileAttributes.class));
                    }

                    else
                    {
                        LOGGER.info("Skipping non-regular file [" + fpath + "]");
                    }
                }
            }

            else
            {
                Files.walkFileTree(sourceDir, visitor);
            }
        }

        catch (IOException exc)
        {
            throw new BatchErrorException("An I/O error has occurred", exc);
        }
    }

    /**
     * Retrieves the source directory where the original files are located.
     *
     * @return the Path instance of the source directory
     */
    protected Path getSourceDirectory()
    {
        return sourceDir;
    }

    /**
     * Retrieves the target directory where the processed files will be saved.
     *
     * @return the Path instance of the target directory
     */
    protected Path getTargetDirectory()
    {
        return targetDir;
    }

    /**
     * Returns the prefix used for renaming each copied image file.
     *
     * @return the filename prefix
     */
    protected String getPrefix()
    {
        return prefix;
    }

    /**
     * Returns the total number of image files identified and processed after a batch run.
     *
     * @return the count of processed images
     */
    protected int getImageCount()
    {
        return imageSet.size();
    }

    /**
     * Indicates whether the {@code Date Taken} attribute should be prepended to the copied file
     * name.
     *
     * @return true if the date-time should be prepended, otherwise false
     */
    protected boolean embedDateTime()
    {
        return embedDateTime;
    }

    /**
     * Returns whether video files in the source directory should be skipped during processing.
     *
     * @return true if video files should be ignored, otherwise false
     */
    protected boolean skipVideoFiles()
    {
        return skipVideoFiles;
    }

    /**
     * Returns an iterator over the internal sorted set of {@code MediaFile} objects.
     *
     * @return an Iterator for navigating the media file set
     */
    @Override
    public Iterator<MediaFile> iterator()
    {
        return imageSet.iterator();
    }

    /**
     * Creates and returns a {@link FileVisitor} instance to traverse the source directory.
     *
     * <p>
     * This visitor extracts and analyses metadata from each file, such as the EXIF
     * {@code DateTimeOriginal} tag. Files with relevant metadata are wrapped in a {@link MediaFile}
     * object and added to the internal sorted set.
     * </p>
     *
     * @return a configured {@link FileVisitor} for processing image files
     */
    private FileVisitor<Path> createImageVisitor()
    {
        return new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
            {
                if (!dir.equals(sourceDir))
                {
                    LOGGER.info("Sub-directory [" + dir + "] is being read");
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path fpath, BasicFileAttributes attr) throws IOException
            {
                boolean forcedTest = false;

                try
                {
                    AbstractImageParser parser = ImageParserFactory.getParser(fpath);
                    MetadataContext context = new MetadataContext(parser.readMetadataAdvanced());
                    Date metadataDate = findDateTakenAdvanced(context, parser.getImageFormat());
                    FileTime modifiedTime = selectDateTakenPriority(metadataDate, fpath, attr.lastModifiedTime(), userDate, dateOffsetUpdate, forcedTest);
                    MediaFile media = new MediaFile(fpath, modifiedTime, parser.getImageFormat(), (metadataDate == null), forcedTest);

                    System.out.printf("fpath: %s%n", fpath);
                    System.out.printf("metadataDate %s%n", metadataDate);
                    System.out.printf("modifiedTime %s%n", modifiedTime);

                    if (media != null)
                    {
                        imageSet.add(media);
                    }
                }

                catch (Exception exc)
                {
                    LOGGER.error("Unexpected error while processing [" + fpath + "]", exc);
                    // LOGGER.error("BOOM: [" + exc.getMessage() + "]", exc);
                }

                return FileVisitResult.CONTINUE;
            }

            /**
             * Attempts to find best available {@code Date Taken} time-stamp from the specified
             * metadata. The value is extracted from either:
             *
             * <ul>
             * <li>an Exif payload in TIFF-based metadata, for example: JPG, TIF, HEIF or,</li>
             * <li>a textual chunk in PNG metadata</li>
             * </ul>
             *
             * @param context
             *        the MetadataContext instance that encapsulates all metadata entries
             * @param format
             *        the detected image type, such as {@code TIF}, {@code PNG}, or {@code JPG}, etc
             *
             * @return the best available Date Taken time-stamp, or null if none found
             */
            private Date findDateTakenAdvanced(MetadataContext context, DigitalSignature format)
            {
                if (context != null && context.containsMetadata())
                {
                    if (format == DigitalSignature.TIF)
                    {
                        return extractExifDate(context);
                    }

                    else if (format == DigitalSignature.PNG)
                    {
                        return extractPngDate(context);
                    }
                }

                return null;
            }

            /**
             * Determines the {@code Date Taken} time-stamp for a file, based on priority rules:
             *
             * <ol>
             * <li>User-provided date (if {@code force} is true)</li>
             * <li>Metadata date (if available)</li>
             * <li>User-provided date (if metadata missing)</li>
             * <li>Last modified time (fallback)</li>
             * </ol>
             *
             * @param metadataDate
             *        the date obtained from the image's metadata, or {@code null} if unavailable
             * @param fpath
             *        the image file path, used only for logging context
             * @param modifiedTime
             *        the file's last modified time-stamp, used as the final fallback
             * @param userDateTime
             *        a user-specified date string to use as a fallback, must be in a supported
             *        format
             * @param dateOffset
             *        offset multiplier (in 10-second increments) applied to user dates for
             *        uniqueness
             * @param force
             *        if {@code true}, forces the use of {@code userDateTime}, ignoring
             *        {@code metadataDate}
             *
             * @return a {@link FileTime} representing the resolved "Date Taken" value
             */
            private FileTime selectDateTakenPriority(Date metadataDate, Path fpath, FileTime modifiedTime, String userDateTime, long dateOffset, boolean force)
            {
                // 1. User-provided date takes precedence if forced
                if (force)
                {
                    Optional<FileTime> forced = parseUserDate(userDateTime, fpath, dateOffset, true);

                    if (forced.isPresent())
                    {
                        return forced.get();
                    }
                }

                // 2. Use metadata date if available
                if (metadataDate != null)
                {
                    LOGGER.info("Attribute - Date Taken found in Exif metadata for [" + fpath + "]");
                    return FileTime.fromMillis(metadataDate.getTime());
                }

                // 3. Otherwise, fallback to user-provided date
                Optional<FileTime> userDate = parseUserDate(userDateTime, fpath, dateOffset, false);

                if (userDate.isPresent())
                {
                    return userDate.get();
                }

                // 4. Final fallback: file's last modified time
                LOGGER.info("No valid date found for [" + fpath + "]. Using file's last modified date [" + modifiedTime + "]");

                return modifiedTime;
            }

            /**
             * Attempts to parse and offset a user-provided date string.
             */
            private Optional<FileTime> parseUserDate(String userDateTime, Path fpath, long dateOffset, boolean forced)
            {
                if (userDateTime == null || userDateTime.isEmpty())
                {
                    return Optional.empty();
                }

                Date parsed = DateParser.convertToDate(userDateTime);

                if (parsed == null)
                {
                    LOGGER.warn("Invalid user date format [" + userDateTime + "] for [" + fpath + "]. [" + (forced ? "Falling back to metadata or file timestamp" : "Ignoring") + "]");
                    return Optional.empty();
                }

                long newTime = parsed.getTime() + (dateOffset * TEN_SECOND_OFFSET_MS);

                LOGGER.info("Date Taken for [" + fpath + "] set to user-defined date [" + parsed + "] with offset [" + dateOffset + "]");

                return Optional.of(FileTime.fromMillis(newTime));
            }
        };
    }

    /**
     * Begins the logging system and writes initial configuration details to a log file. This method
     * is for internal setup and is not intended for external use.
     *
     * @throws BatchErrorException
     *         if the logging service cannot be set up
     */
    private void startLogging() throws BatchErrorException
    {
        try
        {
            String logFilePath = Paths.get(targetDir.toString(), "batchlog_" + SystemInfo.getHostname() + ".log").toString();

            LOGGER.configure(logFilePath);
            LOGGER.setDebug(debug);
            LOGGER.setTrace(false);

            // Log some information about the logging setup
            LOGGER.info("Source directory set to [" + getSourceDirectory().toAbsolutePath() + "] with original images");
            LOGGER.info("Target directory set to [" + getTargetDirectory().toAbsolutePath() + "] for copied images");
        }

        catch (SecurityException | IOException exc)
        {
            throw new BatchErrorException("Unable to enable logging. Program terminated", exc);
        }
    }

    private static Date extractExifDate(MetadataContext context)
    {
        if (context.hasExifData())
        {
            Optional<DirectoryIFD> opt = context.getDirectory(DirectoryIdentifier.EXIF_DIRECTORY_SUBIFD);

            if (opt.isPresent())
            {
                return opt.get().getDate(EXIF_TAG_DATE_TIME_ORIGINAL);
            }
        }

        return null;
    }

    private static Date extractPngDate(MetadataContext context)
    {
        if (context.hasExifData())
        {
            Optional<PngDirectory> optExif = context.getDirectory(TagPngChunk.CHUNK_TAG_EXIF_PROFILE);

            if (optExif.isPresent())
            {
                PngDirectory dir = optExif.get();
                PngChunk chunk = dir.getFirstChunk(ChunkType.eXIf);
                ExifMetadata exif = TifParser.parseFromSegmentData(chunk.getPayloadArray());
                DirectoryIFD ifd = exif.getDirectory(DirectoryIdentifier.EXIF_DIRECTORY_SUBIFD);

                return ifd.getDate(EXIF_TAG_DATE_TIME_ORIGINAL);
            }
        }

        if (context.hasTextualData())
        {
            Optional<PngDirectory> opt = context.getDirectory(ChunkType.Category.TEXTUAL);

            if (opt.isPresent())
            {
                PngDirectory dir = opt.get();

                for (PngChunk chunk : dir)
                {
                    if (chunk.hasKeywordPair(TextKeyword.CREATE))
                    {
                        if (!chunk.getText().isEmpty())
                        {
                            return DateParser.convertToDate(chunk.getText());
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Extracts the date from EXIF metadata in a {@link MetadataTIF} object.
     *
     * @param tif
     *        the {@code MetadataTIF} instance
     *
     * @return a {@code Date} object from the EXIF data, or {@code null} if not found
     */
    private static Date extractExifDate(MetadataTIF tif)
    {
        if (tif.hasExifData())
        {
            DirectoryIFD dir = tif.getDirectory(DirectoryIdentifier.EXIF_DIRECTORY_SUBIFD);

            if (dir != null)
            {
                return dir.getDate(EXIF_TAG_DATE_TIME_ORIGINAL);
            }
        }

        return null;
    }

    /**
     * Extracts the date from PNG metadata. It first checks for embedded EXIF data, then
     * falls back to textual chunks if available.
     *
     * @param png
     *        the {@code MetadataPNG} instance
     *
     * @return a Date object from the PNG data, or null if not found
     */
    private static Date extractPngDate(MetadataPNG<?> png)
    {
        if (png.hasExifData())
        {
            Object dir = png.getDirectory(MetadataTIF.class);

            if (dir instanceof MetadataTIF)
            {
                Date exifDate = extractExifDate((MetadataTIF) dir);

                if (exifDate != null)
                {
                    return exifDate;
                }
            }
        }

        else if (png.hasTextualData())
        {
            Object dir = png.getDirectory(ChunkType.Category.TEXTUAL);

            if (dir instanceof ChunkDirectory)
            {
                List<TextEntry> data = ((ChunkDirectory) dir).getTextualData(TextKeyword.CREATE);

                if (!data.isEmpty())
                {
                    Date parsed = DateParser.convertToDate(data.get(0).getValue());

                    if (parsed != null)
                    {
                        return parsed;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Executes the batch copying process. To be sub-classed to provide an accurate implementation.
     *
     * <p>
     * This method iterates through the internal sorted set of {@link MediaFile} objects and copies
     * each source file to the target directory. It renames the copied file using the designated
     * prefix and updates its file time attributes (creation, last modified, and last access) to
     * match the "Date Taken" timestamp determined during the scan phase.
     * </p>
     */
    @Override
    public void updateAndCopyFiles() throws FileNotFoundException, IOException
    {
        this.forEach(System.out::println);
    }

    // DEPRECATED
    @SuppressWarnings("unused")
    private Date findDateTaken(Metadata<?> meta)
    {
        if (meta == null || !meta.hasMetadata())
        {
            return null;
        }

        if (meta instanceof MetadataTIF)
        {
            return extractExifDate((MetadataTIF) meta);
        }

        else if (meta instanceof MetadataPNG)
        {
            return extractPngDate((MetadataPNG<?>) meta);
        }

        return null;
    }
}