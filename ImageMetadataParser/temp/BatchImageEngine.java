package imagebatch;

import static tif.TagEntries.TagEXIF.*;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.png.AbstractPngText;
import org.apache.commons.imaging.formats.png.PngImageParser;
import org.apache.commons.imaging.formats.png.PngImagingParameters;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import common.DateParser;
import common.RunCommand;
import common.SystemInfo;
import composite.Metadata;
import composite.MetadataPNG;
import composite.MetadataTIF;
import logger.LogFactory;
import metadata.ImageReadErrorException;
import metadata.MetadataScanner;
import png.PngChunk;
import tif.DirectoryIFD;
import tif.DirectoryIdentifier;

/**
 * <p>
 * Automates the batch processing of image files by copying the original files to a target
 * directory, renaming them with a specified prefix, sorting them based on their original
 * {@code Date Taken} attribute, and updating the creation date, last modification time, and last
 * access time to match with the original {@code Date Taken} attribute, found in the image metadata.
 * </p>
 *
 * <p>
 * If the {@code Date Taken} attribute is missing or empty, a new property will be created based on
 * the last modified time of the respective file.
 * </p>
 *
 * <p>
 * To access the sorted set of files programmatically, use an iterator in your implementation.
 * </p>
 * 
 * <p>
 * Change History:
 * </p>
 * 
 * <ul>
 * <li>Version 1.0 - Initial release by Trevor Maggs on 19 March 2025</li>
 * </ul>
 * 
 * @author Trevor Maggs
 * @since 19 March 2025
 */
public abstract class BatchImageEngine implements Iterable<MetaMedia>
{
    private static final LogFactory LOGGER = LogFactory.getLogger(BatchImageEngine.class);
    protected static final String IMAGE_MAGICK_PATH = "ImageMagick-7.1.1-46-portable-Q8-x64\\magick.exe";
    private Set<MetaMedia> imageSet;
    private Path sourceDir;
    private String prefix;
    private Path targetDir;
    protected boolean embedDateTime;
    protected boolean skipMediaFiles;
    protected boolean convertJPG;

    /**
     * <p>
     * This inner class implements a builder design pattern to construct an object in a step-by-step
     * fashion, providing a clean and efficient approach for instantiation. Setter methods are
     * available at your disposal.
     * </p>
     * 
     * <p>
     * <b>Example:</b>
     * </p>
     * 
     * <pre>{@code
     *         BatchImageEngine.Builder batch = new BatchConsole.BatchImageBuilder()
                .source("D:\KDR Project\Milestones\TestBatch")
                .target("local\images")
                .name("image")
                .descending(true)
                .datetime("26 4 2006")
                .build();                
     * }</pre>
     */
    public abstract static class Builder
    {
        // Required parameters
        private String bd_sourceDir;
        private String bd_prefix;
        private String bd_target;
        private String bd_datetime;
        private boolean bd_descending;
        private boolean bd_embedDateTime;
        private boolean bd_skipMediaFiles;
        private String[] bd_files;
        private boolean bd_convert;

        public Builder()
        {
        }

        /**
         * Sets the source directory containing original image files.
         * 
         * @param src
         *        the source directory
         * 
         * @return this object to allow method chaining
         */
        public Builder source(final String src)
        {
            bd_sourceDir = src;
            return this;
        }

        /**
         * Assigns a prefix to be appended to each image file name for organisational
         * identification.
         *
         * @param prefix
         *        the name to be appended to every copied file
         *
         * @return this object to allow method chaining
         */
        public Builder name(final String prefix)
        {
            bd_prefix = prefix;
            return this;
        }

        /**
         * Sets the target directory where copied image files are saved to.
         * 
         * @param target
         *        the target directory
         * 
         * @return this object to allow method chaining
         */
        public Builder target(final String target)
        {
            bd_target = target;
            return this;
        }

        /**
         * Sets the date and time to modify the {@code Date Taken} attribute.
         * 
         * @param dt
         *        the date and time attribute
         * 
         * @return this object to allow method chaining
         */
        public Builder datetime(final String dt)
        {
            bd_datetime = dt;
            return this;
        }

        /**
         * Enables a flag to sort the copied images in descending order.
         * 
         * @param desc
         *        a true boolean value to sort the list in descending order
         * 
         * @return this object to allow method chaining
         */
        public Builder descending(final boolean desc)
        {
            bd_descending = desc;
            return this;
        }

        /**
         * Appends the date and time attribute to each image file name.
         * 
         * @param emb
         *        a boolean value to indicate inclusion. True means append, false means don't append
         * 
         * @return this object to allow method chaining
         */
        public Builder embedDateTime(final boolean emb)
        {
            bd_embedDateTime = emb;
            return this;
        }

        /**
         * Determines whether media files should be ignored or copied.
         *
         * @param media
         *        a boolean true value to skip media files, otherwise copy them
         * 
         * @return this object to allow method chaining
         */
        public Builder skipMedia(final boolean media)
        {
            bd_skipMediaFiles = media;
            return this;
        }

        /**
         * Defines a collection of image files to be copied instead of the entire files from the
         * source directory.
         *
         * @param files
         *        a string array of file names to be copied
         * 
         * @return this object to allow method chaining
         */
        public Builder fileSet(final String[] files)
        {
            bd_files = files;
            return this;
        }

        /**
         * Sets the program to convert any non-JPG files into a real JPG format.
         *
         * @param convert
         *        a boolean true value to convert, otherwise just copy files
         * 
         * @return this object to allow method chaining
         */
        public Builder convertJPG(final boolean convert)
        {
            bd_convert = convert;
            return this;
        }

        /**
         * To be deferred to the sub-class for implementation and instantiation of the
         * {@link BatchImageEngine} class using the builder design pattern.
         * 
         * @return a newly created instance of the BatchImageEngine outer class
         * 
         * @throws BatchErrorException
         *         in case of an error during batch processing
         */
        public abstract BatchImageEngine build() throws BatchErrorException;
    }

    /**
     * Constructs a BatchImageEngine instance using data from command line arguments, encapsulated
     * in a Builder object. This constructor is invoked by the static inner method
     * {@link BatchImageEngine.Builder#build()}.
     *
     * @param builder
     *        Builder object containing required parameters
     * 
     * @throws BatchErrorException
     *         in the event of an error during batch processing
     */
    public BatchImageEngine(Builder builder) throws BatchErrorException
    {
        this.sourceDir = Paths.get(builder.bd_sourceDir);
        this.prefix = builder.bd_prefix;
        this.targetDir = Paths.get(builder.bd_target);
        this.embedDateTime = builder.bd_embedDateTime;
        this.skipMediaFiles = builder.bd_skipMediaFiles;
        this.convertJPG = builder.bd_convert;

        if (!Files.isDirectory(sourceDir))
        {
            throw new BatchErrorException(String.format("The source directory [%s] is not a valid directory. Please verify that the path exists and is a directory.", sourceDir));
        }

        if (builder.bd_descending)
        {
            // Sorts the copied images in descending order
            imageSet = new TreeSet<MetaMedia>(new DescendingTimestampComparator());
            LOGGER.info("Sorted copied images in descending order.");
        }

        else
        {
            // Sorts the copied images in ascending order
            imageSet = new TreeSet<MetaMedia>(new DefaultTimestampComparator());
            LOGGER.info("Sorted copied images in ascending order.");
        }

        createNewTargetDirectory();
        startLogging();

        if (builder.bd_files == null)
        {
            processSourceDirectory(builder.bd_datetime);
        }

        else
        {
            processFileSet(builder.bd_datetime, builder.bd_files);
        }
    }

    /**
     * Retrieves the source directory where all original files are found.
     * 
     * @return the Path instance of the source directory
     */
    protected Path getSourceDirectory()
    {
        return sourceDir;
    }

    /**
     * Retrieves the target directory where all copied files are saved.
     * 
     * @return the Path instance of the target directory
     */
    protected Path getTargetDirectory()
    {
        return targetDir;
    }

    /**
     * Retrieves the prefixed name that begins the copied file names.
     * 
     * @return the prefixed name
     */
    protected String getPrefix()
    {
        return prefix;
    }

    /**
     * Returns the number of image entries stored in the data set (in memory).
     *
     * @return the length of the data set
     */
    protected int getImageCount()
    {
        return imageSet.size();
    }

    /**
     * Retrieves an iterator for traversing a collection of MetaImage objects.
     * 
     * @return an Iterator instance for navigating the MetaImage set
     */
    @Override
    public Iterator<MetaMedia> iterator()
    {
        return imageSet.iterator();
    }

    /**
     * Enables logging.
     *
     * @throws BatchErrorException
     *         if the logging service cannot be set up
     */
    private void startLogging() throws BatchErrorException
    {
        try
        {
            // Set up the file for logging and disable the console handler
            String logFilePath = getTargetDirectory() + "/batchlog_" + SystemInfo.getHostname() + ".log";

            LOGGER.configure(logFilePath);
            LOGGER.setDebug(false);
            LOGGER.setTrace(false);

            // Log some information about the logging setup
            LOGGER.warn("Log level set to [" + LOGGER.getVerbosityLevel() + "] by default for logging");
            LOGGER.info("Source directory set to [" + getSourceDirectory().toAbsolutePath() + "] with original images");
            LOGGER.info("Target directory set to [" + getTargetDirectory().toAbsolutePath() + "] for copying images");
        }
        catch (SecurityException | IOException exc)
        {
            throw new BatchErrorException("Unable to start logging. Program terminated.", exc);
        }
    }

    /**
     * Makes a new target directory where generated image files are stored. If the directory is
     * pre-existing, it will be deleted and re-created.
     * 
     * @throws BatchErrorException
     *         if an I/O error has occurred
     */
    private void createNewTargetDirectory() throws BatchErrorException
    {
        try
        {
            if (Files.exists(targetDir))
            {
                deleteTargetDirectory();
            }

            Files.createDirectories(targetDir);
        }

        catch (IOException exc)
        {
            throw new BatchErrorException(String.format("An I/O error was detected in target directory [%s].", targetDir), exc);
        }
    }

    /**
     * Recursively deletes the pre-existing target directory and its underlying sub-folders and
     * files.
     * 
     * @throws IOException
     *         if there is a problem removing the directory
     */
    private void deleteTargetDirectory() throws IOException
    {
        Files.walkFileTree(targetDir, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException
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
        });
    }

    /**
     * Navigates the source directory and identifies relevant files for transfer.
     * 
     * @param dtf
     *        the date and time to update the Date Taken attribute
     * 
     * @throws BatchErrorException
     *         in case of an error during batch processing
     */
    private void processSourceDirectory(String dtf) throws BatchErrorException
    {
        try
        {
            if (dtf.isEmpty())
            {
                Files.walkFileTree(sourceDir, (FileVisitor<? super Path>) new NavigateDirectory());
            }

            else
            {
                Files.walkFileTree(sourceDir, new NavigateDirectory(DateParser.convertToDate(dtf)));
            }
        }

        catch (IllegalArgumentException exc)
        {
            throw new BatchErrorException("Incorrect date format detected [" + dtf + "]. Please check.", exc);
        }

        catch (NoSuchFileException exc)
        {
            throw new BatchErrorException(String.format("Unable to find source directory [%s].", sourceDir), exc);
        }

        catch (IOException exc)
        {
            throw new BatchErrorException(String.format("There was a problem while navigating in directory [%s].", sourceDir), exc);
        }
    }

    /**
     * Navigates the source directory and marks a collection of files selected by the user for
     * transfer.
     * 
     * @param dtf
     *        the date and time to update the Date Taken attribute
     * @param files
     *        an array of specified files to copy
     * 
     * @throws BatchErrorException
     *         in case of an error during batch processing
     */
    private void processFileSet(String dtf, String[] files) throws BatchErrorException
    {
        try
        {
            if (dtf.isEmpty())
            {
                new NavigateDirectory().searchFiles(files);
            }

            else
            {
                new NavigateDirectory(DateParser.convertToDate(dtf)).searchFiles(files);
            }
        }

        catch (IllegalArgumentException exc)
        {
            throw new BatchErrorException("Incorrect Data format detected [" + dtf + "]. Please check.", exc);
        }
    }

    /**
     * An inner class extending SimpleFileVisitor navigates the source directory and extracts
     * metadata from image files on the fly.
     */
    private class NavigateDirectory extends SimpleFileVisitor<Path>
    {
        private Date dateTaken;

        public NavigateDirectory()
        {
            super();
            dateTaken = null;
        }

        public NavigateDirectory(Date dtf)
        {
            dateTaken = dtf;
        }

        /**
         * Processes the specified file and adds to the data structure according to the file
         * signature for efficient retrieval.
         * 
         * @param fpath
         *        the path representing the file to be checked
         * @param attr
         *        the attributes of the file
         * 
         * @throws IOException
         *         if an error occurs while reading the file
         * @throws ImageReadErrorException
         *         in the event of image parsing problems
         */
        private void process(Path fpath, BasicFileAttributes attr) throws IOException, ImageReadErrorException
        {
            FileTime captureTime = null;
            MetadataScanner scanner = MetadataScanner.loadImage(fpath);
            Metadata<?> meta = scanner.getMetadataInfo();

            if (meta != null)
            {
                if (DigitalSignature.detectFormat(fpath) == DigitalSignature.JPG)
                {
                    MetadataTIF tif = (MetadataTIF) meta;

                    if (tif.hasMetadata() && tif.hasExifData())
                    {
                        DirectoryIFD directory = tif.getDirectory(DirectoryIdentifier.EXIF_DIRECTORY_SUBIFD);

                        if (directory.contains(EXIF_TAG_DATE_TIME_ORIGINAL))
                        {
                            Date dt = directory.getDate(EXIF_TAG_DATE_TIME_ORIGINAL);
                            captureTime = FileTime.fromMillis(dt.toInstant().toEpochMilli());
                        }

                        if (captureTime == null)
                        {
                            captureTime = determineDateTaken(fpath, attr);
                        }

                        imageSet.add(new MetaMedia(fpath, captureTime, DigitalSignature.JPG));
                    }

                    else
                    {
                        captureTime = determineDateTaken(fpath, attr);

                        /*
                         * In this case, the JPG file is lacking valid metadata, causing it to be
                         * marked for updates. Specifically, the 'Date Taken' attribute will be
                         * created based on either the file's last modified time-stamp or a
                         * user-provided time-stamp, as no alternative reliable data is available.
                         */
                        imageSet.add(new MetaMedia(fpath, captureTime, DigitalSignature.JPG, true));
                    }

                }

                else if (DigitalSignature.detectFormat(fpath) == DigitalSignature.TIF)
                {
                    MetadataTIF tif = (MetadataTIF) meta;

                    if (tif.hasMetadata() && tif.hasExifData())
                    {
                        DirectoryIFD directory = tif.getDirectory(DirectoryIdentifier.EXIF_DIRECTORY_SUBIFD);

                        if (directory.contains(EXIF_TAG_DATE_TIME_ORIGINAL))
                        {
                            Date dt = directory.getDate(EXIF_TAG_DATE_TIME_ORIGINAL);
                            captureTime = FileTime.fromMillis(dt.toInstant().toEpochMilli());
                        }

                        if (captureTime == null)
                        {
                            captureTime = determineDateTaken(fpath, attr);
                        }

                        imageSet.add(new MetaMedia(fpath, captureTime, DigitalSignature.TIF));
                    }

                    else
                    {
                        captureTime = determineDateTaken(fpath, attr);
                        imageSet.add(new MetaMedia(fpath, captureTime, DigitalSignature.TIF, true));
                    }
                }

                else if (DigitalSignature.detectFormat(fpath) == DigitalSignature.PNG)
                {
                    MetadataPNG png = (MetadataPNG) meta;

                    /*
                     * Since PNG files typically do not include EXIF metadata, the capture date is
                     * obtained from either the file's last modified time-stamp or a user-provided
                     * time-stamp, as no alternative reliable data is present.
                     * 
                     * However, if the file happens to have a Creation Time textual chunk, then
                     * there is a good possibility of obtaining the date object.
                     */
                    if (png.hasMetadata())
                    {
                        PngChunk chunk;

                        /*
                         * if (png.countTextualChunk() > 0 && (chunk =
                         * png.findTextualChunk(TextKeyword.CREATE)) != null)
                         * {
                         * try
                         * {
                         * Date dt =
                         * DateParser.convertToDate(chunk.getTextualString(TextKeyword.CREATE));
                         * captureTime = FileTime.fromMillis(dt.toInstant().toEpochMilli());
                         * }
                         * 
                         * catch (RuntimeException exc)
                         * {
                         * LOGGER.error(String.
                         * format("Creation Time cannot be determined in file [%s]", fpath), exc);
                         * }
                         * }
                         * 
                         * else if (png.hasExifChunk())
                         * {
                         * // TODO: Add logic to capture DateTimeOriginal metadata item
                         * }
                         * 
                         * if (captureTime == null)
                         * {
                         * captureTime = determineDateTaken(fpath, attr);
                         * }
                         */

                        imageSet.add(new MetaMedia(fpath, captureTime, DigitalSignature.PNG));
                    }

                    else
                    {
                        captureTime = determineDateTaken(fpath, attr);
                        imageSet.add(new MetaMedia(fpath, captureTime, DigitalSignature.PNG, true));
                    }
                }
            }

            else if (DigitalSignature.detectFormat(fpath) == DigitalSignature.HEIC)
            {
                if (captureTime == null)
                {
                    captureTime = determineDateTaken(fpath, attr);
                }

                imageSet.add(new MetaMedia(fpath, captureTime, DigitalSignature.HEIC));
            }

            else if (DigitalSignature.detectFormat(fpath) == DigitalSignature.MOV)
            {
                // imageSet.add(new MetaMedia(fpath, attr.lastModifiedTime(),
                // DigitalSignature.MOV));
            }

            else if (DigitalSignature.detectFormat(fpath) == DigitalSignature.AVI)
            {
                // imageSet.add(new MetaMedia(fpath, attr.lastModifiedTime(),
                // DigitalSignature.AVI));
            }

            else if (DigitalSignature.detectFormat(fpath) == DigitalSignature.MP4)
            {
                // imageSet.add(new MetaMedia(fpath, attr.lastModifiedTime(),
                // DigitalSignature.MP4));
            }

            else
            {
                LOGGER.info(String.format("File [%s] is an unknown or unsupported image file", fpath));
            }
        }

        /**
         * This method takes into account the presence of a user-provided {@code Date Taken}
         * attribute and updates it if necessary. If the attribute is empty, it defaults to the
         * file's last modified time.
         * 
         * @param fpath
         *        the path representing the file to be checked
         * @param attr
         *        the attributes of the file
         * 
         * @return a FileTime instance containing the date and time information
         */
        private FileTime determineDateTaken(Path fpath, BasicFileAttributes attr)
        {
            FileTime captureTime = attr.lastModifiedTime();
            SimpleDateFormat dt = new SimpleDateFormat("dd MMM yyyy");

            if (dateTaken == null || captureTime.toMillis() > 0)
            {
                LOGGER.warn(String.format("Empty Date Taken attribute found in file [%s]. Value copied from last modified date", fpath));
            }

            else
            {
                captureTime = FileTime.fromMillis(dateTaken.getTime());
                dateTaken.setTime(dateTaken.getTime() + 10000);

                LOGGER.info(String.format("Date Taken attribute updated with [%s] in file [%s]", dt.format(dateTaken.getTime()), fpath));
            }

            return captureTime;
        }

        /**
         * Searches for specific image files in the source directory based on the specified list and
         * prepares them for copying.
         *
         * @param files
         *        an array of image files to search for
         *
         * @throws BatchErrorException
         *         if an error occurs during the file search or processing
         */
        private void searchFiles(String[] files) throws BatchErrorException
        {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir))
            {
                for (Path fpath : stream)
                {
                    if (Arrays.binarySearch(files, fpath.getFileName().toString()) > -1)
                    {
                        BasicFileAttributes attr = Files.readAttributes(fpath, BasicFileAttributes.class);

                        process(fpath, attr);
                    }
                }
            }

            catch (NoSuchFileException exc)
            {
                throw new BatchErrorException(String.format("Source directory [%s] cannot be found.", sourceDir), exc);
            }

            catch (IOException exc)
            {
                throw new BatchErrorException(String.format("An I/O error was detected while accessing files in source directory [%s].", sourceDir), exc);
            }

            catch (ImageReadErrorException exc)
            {
                throw new BatchErrorException(exc);
            }
        }

        /**
         * Visits a file in the directory, extracts metadata and adds it to the imageSet.
         * 
         * @param fpath
         *        the path of the file to visit
         * @param attr
         *        the attributes of the file
         * 
         * @return FileVisitResult.CONTINUE to continue visiting the directory
         * @throws IOException
         *         if an error occurs while reading the file
         */
        @Override
        public FileVisitResult visitFile(Path fpath, BasicFileAttributes attr) throws IOException
        {
            try
            {
                process(fpath, attr);
            }

            catch (ImageReadErrorException exc)
            {
                throw new IOException(exc);
            }

            return FileVisitResult.CONTINUE;
        }
    }

    protected Path convertImageToJPG(Path original, String prefix) throws BatchErrorException
    {
        RunCommand cmd = RunCommand.newInstance("cmd.exe /c");
        Path copied = getTargetDirectory().resolve(prefix.substring(0, prefix.lastIndexOf(".")) + ".jpg");

        cmd.addArgument(IMAGE_MAGICK_PATH);
        cmd.addArgument(original.toString());
        cmd.addArgument(copied.toString());

        try
        {
            if (cmd.execute() > 0)
            {
                copied = null;
                LOGGER.error(String.format("Unable to convert file [%s]", original));
            }
        }

        catch (IOException e)
        {
            throw new BatchErrorException("Unable to convert to a JPG format. Problem executing command: [" + cmd.getLastCommand() + "]. ");
        }

        return copied;
    }

    /*
     * PROTECTED STATIC METHODS
     */

    /**
     * Retrieves the original image capture date and time.
     *
     * @param field
     *        the TiffField object containing metadata information
     * 
     * @return the original image capture date and time in a FileTime instance
     */
    protected static FileTime findDateTakenMetadata(TiffField field)
    {
        String capture = null;
        FileTime dateTimeOriginal = null;

        if (field != null)
        {
            try
            {
                /*
                 * Remove unwanted single quotations
                 * Example: '2023:01:24 16:02:54'
                 */
                capture = field.getValueDescription().replace("'", "");

                Date dt = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss").parse(capture);
                dateTimeOriginal = FileTime.fromMillis(dt.toInstant().toEpochMilli());
            }

            catch (ParseException exc)
            {
                // If the parsing fails, use the current time-stamp as a fallback
                dateTimeOriginal = FileTime.fromMillis(System.currentTimeMillis());

                LOGGER.warn(String.format("Unable to evaluate the date-time [%s]. Current timestamp is provided.", capture));
            }
        }

        return dateTimeOriginal;
    }

    /**
     * Retrieves the {@code Date Taken} metadata property of the specified image file. It serves as
     * a wrapper to run the third-party Magick program, potentially located in the current working
     * directory. Running this external program will slow down the execution speed.
     *
     * @param fpath
     *        The original image file to be analysed
     * 
     * @return The {@code Date Taken} metadata property, parsed as a Date object. Returns null if
     *         the property is not found or cannot be parsed
     */
    protected static Date fetchDateTakenExif(Path fpath)
    {
        String attrib = "exif:DateTime:";
        RunCommand cmd = RunCommand.newInstance("cmd.exe /c");
        SimpleDateFormat dt = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");

        cmd.addArgument(IMAGE_MAGICK_PATH);
        cmd.addArgument("identify");
        cmd.addArgument("-verbose");
        cmd.addArgument(fpath.toString());

        try
        {
            if (cmd.execute() > 0)
            {
                LOGGER.error(String.format("Unable to retrieve a list of EXIF properties in file [%s]", fpath));
            }

            for (String str : cmd.getResults())
            {
                /*
                 * exif:DateTime: 2022:09:06 11:19:26
                 * exif:DateTimeDigitized: 2022:09:06 11:19:26
                 * exif:DateTimeOriginal: 2022:09:06 11:19:26
                 */

                int pos = str.indexOf(attrib);

                if (pos != -1)
                {
                    return dt.parse(str.substring(pos + attrib.length() + 1));
                }
            }
        }
        catch (IOException exc)
        {
            LOGGER.error(String.format("Problem executing command: [%s]", cmd.getLastCommand()), exc);
        }

        catch (ParseException exc)
        {
            LOGGER.error(String.format("Unable to extract date and time information for file [%s]", fpath), exc);
        }

        return null;
    }

    /**
     * Copies the source image file to the target file and modifies the {@code Date Taken} property
     * within the PNG textual chunk using the specified date-time parameter.
     *
     * @param sourceFile
     *        the original image file to be copied
     * @param targetFile
     *        the destination file, a copy of the specified source file
     * @param datetime
     *        the Date Taken property time to be updated
     *
     * @throws IOException
     *         if an error occurs during processing
     */
    public static void updateDateTakenPNG(File sourceFile, File targetFile, FileTime datetime) throws IOException
    {
        BufferedImage image = Imaging.getBufferedImage(sourceFile);
        PngImagingParameters writeParams = new PngImagingParameters();

        List<AbstractPngText> writeTexts = new ArrayList<>();

        writeTexts.add(new AbstractPngText.Text("Creation Time", new SimpleDateFormat("yyyy:MM:dd HH:mm:ss").format(datetime.toMillis())));
        writeParams.setTextChunks(writeTexts);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
        {
            PngImageParser writer = new PngImageParser();

            writer.writeImage(image, baos, writeParams);

            try (FileOutputStream fos = new FileOutputStream(targetFile); OutputStream os = new BufferedOutputStream(fos))
            {
                os.write(baos.toByteArray());
            }
        }
    }

    /**
     * Copies the specified original image file to the target file, and updates the
     * {@code Date Taken} property in the image metadata with the provided date-time parameter.
     *
     * @param sourceFile
     *        the original image file to be copied
     * @param targetFile
     *        the new target file, a copy of the specified source file
     * @param datetime
     *        The captured date-time to be updated
     * 
     * @throws FileNotFoundException
     *         if the target file cannot be found
     * @throws IOException
     *         if an error occurs during processing
     */
    protected static void changeDateTimeMetadata(File sourceFile, File targetFile, FileTime datetime) throws FileNotFoundException, IOException
    {
        try (FileOutputStream fos = new FileOutputStream(targetFile); BufferedOutputStream os = new BufferedOutputStream(fos))
        {
            TiffOutputSet outputSet = null;
            ImageMetadata metadata = Imaging.getMetadata(sourceFile);
            JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
            String dateTaken = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss").format(datetime.toMillis());

            if (jpegMetadata != null)
            {
                TiffImageMetadata exif = jpegMetadata.getExif();

                if (exif != null)
                {
                    outputSet = exif.getOutputSet();
                }
            }

            if (outputSet == null)
            {
                outputSet = new TiffOutputSet();
            }

            TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();

            // Recreate the 'Date Taken' field
            exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
            exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, dateTaken);

            // Recreate the 'Digitized Date' field
            exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED);
            exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, dateTaken);

            new ExifRewriter().updateExifMetadataLossless(sourceFile, os, outputSet);
        }
    }

    /**
     * Sets the last modified time, last accessed time, and creation time of an image file.
     *
     * @param fpath
     *        the file path to modify
     * @param fileTime
     *        the file time to set
     * 
     * @throws IOException
     *         if an error occurs while setting the file times
     */
    protected static void changeFileTimeProperties(Path fpath, FileTime fileTime) throws IOException
    {
        BasicFileAttributeView target = Files.getFileAttributeView(fpath, BasicFileAttributeView.class);
        target.setTimes(fileTime, fileTime, fileTime);
    }

    /**
     * Targets subclasses for an implementation. The aim is to transfer files from a source
     * directory to a target directory, renaming them with a specified prefix. The creation time,
     * last modified time, and last access time of each file are then updated to match the
     * {@code Date Taken} metadata property.
     *
     * @throws BatchErrorException
     *         if an error occurs during the file copy process
     */
    protected abstract void copyToTarget() throws BatchErrorException;

    // DEPRECATED
    protected Path updateDateTakenExifTool(Path original, String prefix) throws BatchErrorException
    {
        RunCommand cmd = RunCommand.newInstance("cmd.exe /c");
        // Path copied = getTargetDirectory().resolve(prefix.substring(0, prefix.lastIndexOf(".")) +
        // ".jpg");
        Path copied = getTargetDirectory().resolve(prefix);

        cmd.addArgument("exiftool-13.29_64\\exiftool.exe");
        cmd.addArgument("-PNG:CreationTime=2015:05:15 10:30:00");
        cmd.addArgument(copied.toString());

        System.out.printf("cmd: %s\n", cmd.getLastCommand());

        try
        {
            if (cmd.execute() > 0)
            {
                copied = null;
                LOGGER.error(String.format("Unable to convert file [%s]", original));
            }
        }

        catch (IOException e)
        {
            throw new BatchErrorException("Unable to convert to a JPG format. Problem executing command: [" + cmd.getLastCommand() + "]. ");
        }

        return copied;
    }

}