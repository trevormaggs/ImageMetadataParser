package batch;

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
import java.util.Set;
import java.util.TreeSet;
import common.AbstractImageParser;
import common.ImageParserFactory;
import common.Metadata;
import common.SmartDateParser;
import common.SystemInfo;
import logger.LogFactory;

/**
 * Automates the batch processing of image files by copying, renaming, and chronologically sorting
 * them based on their EXIF metadata, such as {@code DateTimeOriginal}.
 *
 * <p>
 * This class supports a range of image formats, including JPEG, TIFF, PNG, WebP, and HEIF. If EXIF
 * metadata is not available, it defaults to using the file's last modified time-stamp.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 * @see MediaFile
 */
public class BatchExecutor implements Iterable<MediaFile>
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
    private final boolean forced;
    private final String[] fileSet;
    private long dateOffsetUpdate;

    static
    {
        DELETE_VISITOR = new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
            {
                return FileVisitResult.CONTINUE;
            }

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
        this.forced = builder.bd_force;
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
     * Sub-classes to define an implementation to iterate through the internal sorted set of
     * {@link MediaFile} objects for batching.
     */
    public void processBatchCopy()
    {
        throw new UnsupportedOperationException("Subclasses must implement this method's logic");
    }

    /**
     * Begins the batch processing workflow by cleaning the target directory, setting up logging,
     * and processing the specified source files or directory.
     *
     * @throws BatchErrorException
     *         if an I/O error has occurred
     */
    protected void start() throws BatchErrorException
    {
        FileVisitor<Path> visitor = createImageVisitor();

        try
        {
            if (Files.exists(targetDir))
            {
                /*
                 * Prevents the user from accidentally pointing
                 * targetDir as the source directory.
                 */
                if (Files.isSameFile(sourceDir.toAbsolutePath(), targetDir.toAbsolutePath()))
                {
                    throw new BatchErrorException("Target directory [" + targetDir + "] cannot be the same location as source directory [" + sourceDir + "]. Program terminated");
                }

                /*
                 * Permanently deletes the target directory and all of its contents.
                 * This operation is destructive and cannot be undone.
                 */
                Files.walkFileTree(targetDir, DELETE_VISITOR);

                LOGGER.warn("Old files within directory [" + targetDir + "] deleted");
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
     * Creates and returns a {@link FileVisitor} instance to traverse the source directory.
     *
     * <p>
     * The visitor analyses each file, extracts metadata segments and determines the
     * {@code Date Taken} time-stamp. Each file is then wrapped in a {@link MediaFile} object and
     * added to the internal set for later processing.
     * </p>
     *
     * @return a configured {@link FileVisitor} for processing image files
     *
     * @throws BatchErrorException
     *         if the source directory is not a valid directory
     */
    private FileVisitor<Path> createImageVisitor() throws BatchErrorException
    {
        if (!Files.isDirectory(sourceDir))
        {
            throw new BatchErrorException("The source directory [" + sourceDir + "] is not a valid directory. Please verify that the path exists and is a directory");
        }

        return new SimpleFileVisitor<Path>()
        {
            /**
             * Determines the {@code Date Taken} time-stamp for a file based on a priority
             * hierarchy.
             *
             * <ol>
             * <li>User-provided date (if {@code force} is true or metadata missing)</li>
             * <li>Metadata date (if available)</li>
             * <li>File's last modified time (final fallback)</li>
             * </ol>
             *
             * If the user-provided date is used, the timestamp will be incremented by a 10-second
             * offset for subsequent files. This mechanism is necessary to avoid timestamp
             * collisions.
             *
             * @param fpath
             *        the image file path, used only for logging context
             * @param metadataDate
             *        the date obtained from the image's metadata, or null if unavailable
             * @param modifiedTime
             *        the file's last modified time-stamp, used as the final fallback
             * @return a {@link FileTime} representing the resolved "Date Taken" value
             */
            private FileTime selectDateTaken(Path fpath, Date metadataDate, FileTime modifiedTime)
            {
                if (forced || metadataDate == null)
                {
                    Date dt = SmartDateParser.convertToDate(userDate);

                    if (dt != null)
                    {
                        long newTime = dt.getTime() + (dateOffsetUpdate * TEN_SECOND_OFFSET_MS);
                        dateOffsetUpdate++;

                        LOGGER.info("Date Taken for [" + fpath + "] set to user-defined date [" + dt + "] with offset [" + dateOffsetUpdate + "]");

                        return FileTime.fromMillis(newTime);
                    }

                    LOGGER.warn("Invalid user date format [" + userDate + "] found in [" + fpath + "]. " + (forced ? "Falling back to metadata or file timestamp" : ""));
                }

                if (metadataDate != null)
                {
                    LOGGER.info("Date Taken found in Exif metadata in file [" + fpath + "]");

                    return FileTime.fromMillis(metadataDate.getTime());
                }

                return modifiedTime;
            }

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
                try
                {
                    AbstractImageParser parser = ImageParserFactory.getParser(fpath);

                    parser.readMetadata();

                    Metadata<?> meta = parser.getMetadata();
                    Date metadataDate = meta.extractDate();
                    FileTime modifiedTime = selectDateTaken(fpath, metadataDate, attr.lastModifiedTime());
                    MediaFile media = new MediaFile(fpath, modifiedTime, parser.getImageFormat(), (metadataDate == null), forced);

                    // System.out.printf("METADATA DATE -> %s%n", metadataDate);
                    // System.out.printf("%s%n", parser.formatDiagnosticString());

                    if (media != null)
                    {
                        imageSet.add(media);
                    }
                }

                /*
                 * IOException
                 * ImageReadErrorException
                 * NoSuchFileException
                 * UnsupportedOperationException (RuntimeException)
                 * IndexOutOfBoundsException (RuntimeException)
                 * IllegalStateException (RuntimeException)
                 * NullPointerException (RuntimeException)
                 * IllegalArgumentException (RuntimeException)
                 */
                catch (IOException exc)
                {
                    LOGGER.error(exc.getMessage(), exc);

                    System.err.printf("ERROR DETECTED: %s\n", exc.getMessage());
                }

                catch (RuntimeException exc)
                {
                    // Temporary for debugging only
                    exc.printStackTrace();
                }

                return FileVisitResult.CONTINUE;
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
}