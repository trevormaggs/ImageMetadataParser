package batch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import common.CommandLineParser;
import common.ConsoleBar;
import common.ProjectBuildInfo;
import common.Utils;
import common.cli.CommandLineReader;
import heif.HeifDatePatcher;
import jpg.JpgDatePatcher;
import logger.LogFactory;
import png.PngDatePatcher;
import webp.WebPDatePatcher;

/**
 * The primary entry point for the batch processing engine.
 *
 * <p>
 * Processes a collection of media files by copying them from a source directory to a target
 * destination. Files are renamed using a configurable prefix and chronological index, and can be
 * sorted in ascending or descending order based on their original {@code Date Taken} metadata.
 * </p>
 *
 * <p>
 * This executor performs "surgical" binary patching on the copied files to align internal metadata
 * (EXIF, XMP, etc.) with specified timestamps and synchronises the operating system's file-system
 * attributes (Creation, Last Modified, and Last Access times).
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.2
 * @since 9 February 2026
 */
public final class BatchConsole extends BatchExecutor
{
    private static final LogFactory LOGGER = LogFactory.getLogger(BatchConsole.class);
    private static final SimpleDateFormat DF = new SimpleDateFormat("_ddMMMyyyy");

    /**
     * Constructs a console interface, using a Builder design pattern to process the parameters and
     * update the copied image files. The actual Builder implementation exists in the
     * {@link BatchBuilder} class.
     *
     * @param builder
     *        the Builder object containing parameters for constructing this instance
     *
     * @throws BatchErrorException
     *         if any metadata-related reading error occurs
     */
    public BatchConsole(BatchBuilder builder) throws BatchErrorException
    {
        super(builder);

        start();
        processBatchCopy();
    }
    /**
     * Executes the sequential copying and metadata patching process.
     *
     * <p>
     * Iterates through the sorted set of {@link MediaFile} objects, performing the following steps
     * for each:
     * </p>
     * 
     * <ul>
     * <li>Generates a new filename based on prefix, index, and optional timestamp</li>
     * <li>Copies the file to the target directory while preserving original attributes</li>
     * <li>If forced or metadata is missing, patches internal binary date tags (EXIF/XMP)</li>
     * <li>Synchronises OS-level timestamps with the media's capture time</li>
     * </ul>
     */
    @Override
    public void processBatchCopy()
    {
        int k = 0;

        for (MediaFile media : this)
        {
            k++;
            //ConsoleBar.updateProgressBar(k, getImageCount());

            if (media.isVideoFormat() && skipVideoFiles())
            {
                LOGGER.info("File [" + media.getPath() + "] skipped");
                continue;
            }

            try
            {
                String fname = generateTargetName(media, k);
                Path targetPath = getTargetDirectory().resolve(fname);
                FileTime captureTime = FileTime.fromMillis(media.getTimestamp());

                Files.copy(media.getPath(), targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

                // Dispatches to the correct patcher based on file type.
                if (isDateChangeForced() || media.isMetadataEmpty())
                {
                    if (media.isJPG())
                    {
                        JpgDatePatcher.patchAllDates(targetPath, captureTime, false);
                    }

                    else if (media.isPNG())
                    {
                        PngDatePatcher.patchAllDates(targetPath, captureTime, false);
                    }

                    else if (media.isWebP())
                    {
                        WebPDatePatcher.patchAllDates(targetPath, captureTime, false);
                    }

                    else if (media.isHEIC())
                    {
                        HeifDatePatcher.patchAllDates(targetPath, captureTime, false);
                    }

                    else if (media.isTIF())
                    {
                        // Note: TIF seems to use a different utility in your code
                        BatchMetadataUtils.updateDateTakenMetadataTIF(targetPath.toFile(), media.getPath().toFile(), captureTime);
                    }
                }

                Utils.updateFileTimeStamps(targetPath, captureTime);

            }

            catch (IOException exc)
            {
                LOGGER.error("Failed to process file [" + media.getPath() + "]. Error [" + exc.getMessage() + "]", exc);
            }
        }
    }

    /**
     * Handles the logic for naming video vs images.
     * 
     * @param media
     *        the MediaFile object to query
     * @param index
     *        the counter number identifying the order of the file based on its creation date
     * @return the newly generated file name
     */
    private String generateTargetName(MediaFile media, int index)
    {
        if (media.isVideoFormat())
        {
            return media.getPath().getFileName().toString().toLowerCase();
        }

        String suffix = embedDateTime() ? DF.format(media.getTimestamp()) : "";

        return String.format("%s%d%s.%s", getPrefix(), index, suffix, media.getMediaFormat().getFileExtensionName());
    }

    /**
     * Configures and parses the supported command-line arguments.
     *
     * <p>
     * This method defines the rules for known flags and options. Note that most option handling
     * logic is delegated to the surrounding Builder-pattern implementation, while this method
     * primarily registers the supported flags.
     * </p>
     *
     * @param arguments
     *        the raw command-line arguments passed to main
     * @return a CommandLineReader instance, already configured and parsed for the current
     *         invocation
     */
    private static CommandLineReader scanArguments(String[] arguments)
    {
        CommandLineReader cli = new CommandLineReader(arguments);

        try
        {
            cli.addRule("-p", CommandLineParser.ARG_OPTIONAL);
            cli.addRule("-t", CommandLineParser.ARG_OPTIONAL);
            cli.addRule("-e", CommandLineParser.ARG_BLANK);
            cli.addRule("-k", CommandLineParser.ARG_BLANK);
            cli.addRule("--desc", CommandLineParser.ARG_BLANK);
            cli.addRule("-m", CommandLineParser.ARG_OPTIONAL);
            cli.addRule("-f", CommandLineParser.ARG_BLANK);
            cli.addRule("-l", CommandLineParser.SEP_OPTIONAL);
            cli.addRule("-v", CommandLineParser.ARG_BLANK);
            cli.addRule("--version", CommandLineParser.ARG_BLANK);
            cli.addRule("-d", CommandLineParser.ARG_BLANK);
            cli.addRule("--debug", CommandLineParser.ARG_BLANK);
            cli.addRule("-h", CommandLineParser.ARG_BLANK);
            cli.addRule("--help", CommandLineParser.ARG_BLANK);

            cli.setMaximumStandaloneArgumentCount(1);
            cli.parse();

            if (cli.existsOption("-h") || cli.existsOption("--help"))
            {
                showHelp();
                System.exit(0);
            }

            if (cli.existsOption("-v") || cli.existsOption("--version"))
            {
                System.out.printf("Build date: %s%n", ProjectBuildInfo.getInstance(BatchConsole.class).getBuildDate());
                System.exit(0);
            }
        }

        catch (Exception exc)
        {
            System.err.println(exc.getMessage());

            showUsage();
            System.exit(1);
        }

        return cli;
    }

    /**
     * Prints the command usage line, showing the correct flag options.
     */
    private static void showUsage()
    {
        System.out.format("Usage: %s [-p label] [-t target directory] [-e] [-k] [-m date taken] [-l <File 1> ... <File n>] [--desc] [-d|--debug] [-v|--version] [-h|--help] <Source Directory>%n",
                ProjectBuildInfo.getInstance(BatchConsole.class).getShortFileName());
    }

    /**
     * Prints detailed usage help information, providing guidance on how to use this program.
     */
    private static void showHelp()
    {
        showUsage();
        System.out.println("\nOptions:");
        System.out.println("  -p <prefix>        Prepend copied files with user-defined prefix");
        System.out.println("  -t <directory>     Target directory where copied files are saved");
        System.out.println("  -e                 Embed date and time in copied file names");
        System.out.println("  -m <date>          Modify file's 'Date Taken' metadata property if empty");
        System.out.println("  -f                 Force user-defined date modification regardless of metadata. -m flag must be specified");
        System.out.println("  -l <files...>      Comma-separated list of specific file names to process");
        System.out.println("  -k                 Skip media files (videos, etc)");
        System.out.println("  --desc             Sort the images in descending order");
        System.out.println("  -v                 Display last build date");
        System.out.println("  -h                 Display this help message and exit");
        System.out.println("  -d                 Enable debugging");
    }

    /**
     * Begins the execution process by reading arguments from the command line and processing them.
     *
     * @param arguments
     *        an array of strings containing the command line arguments
     */
    private static void readCommand(String[] arguments)
    {
        CommandLineReader cli = scanArguments(arguments);

        BatchBuilder batch = new BatchBuilder()
                .source(cli.getFirstStandaloneArgument())
                .target(cli.getValueByOption("-t"))
                .prefix(cli.getValueByOption("-p"))
                .descending(cli.existsOption("--desc"))
                .userDate(cli.getValueByOption("-m"))
                .embedDateTime(cli.existsOption("-e"))
                .skipVideo(cli.existsOption("-k"))
                .debug(cli.existsOption("-d") || cli.existsOption("--debug"));

        if (cli.existsOption("-l"))
        {
            String[] files = new String[cli.getValueLength("-l")];

            for (int k = 0; k < cli.getValueLength("-l"); k++)
            {
                files[k] = cli.getValueByOption("-l", k);
            }

            batch.fileSet(files);
        }

        if (cli.existsOption("-f") && cli.existsOption("-m"))
        {
            batch.forceDateChange();
        }

        try
        {
            batch.build();
            new BatchConsole(batch);
        }

        catch (Exception exc)
        {
            // Ensure no silent failures are allowed
            LOGGER.error(exc.getMessage());
        }
    }

    public static void main(String[] args)
    {
        BatchConsole.readCommand(args);
    }
}