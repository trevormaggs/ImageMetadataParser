package batch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import common.CommandLineParser;
import common.ProjectBuildInfo;
import common.Utils;
import common.cli.CommandLineReader;
import heif.HeifDatePatcher;
import jpg.JpgDatePatcher;
import logger.LogFactory;
import png.PngDatePatcher;

/**
 * <p>
 * This is the main console batch executor used for copying media files and updating metadata.
 * Command line arguments are read and processed, aiming to write copied files to a target directory
 * and sorting them by their {@code Date Taken} metadata attribute.
 * </p>
 *
 * <p>
 * Specifically, it updates each file's creation date, last modification time, and last access
 * time to align with the corresponding {@code Date Taken} property. The sorted list can be either
 * in an ascending (default) or descending chronological order.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
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
     * Executes the batch copying process, iterating through the internal sorted set of
     * {@link MediaFile} objects and copies each source file to the target directory. It renames the
     * copied file using the specified prefix and updates its file time attributes (creation, last
     * modified, and last access) to match the {@code Date Taken} timestamp determined during the
     * scan phase.
     */
    @Override
    public void processBatchCopy()
    {
        int k = 0;
        Path copied;
        FileTime captureTime;

        for (MediaFile media : this)
        {
            String originalFileName = media.getPath().getFileName().toString();
            String fileExtension = media.getMediaFormat().getFileExtensionName();
            String fname;

            k++;
            // ConsoleBar.updateProgressBar(k, getImageCount());

            if (media.isVideoFormat())
            {
                if (skipVideoFiles())
                {
                    LOGGER.info("File [" + media.getPath() + "] skipped");
                    continue;
                }

                fname = originalFileName.toLowerCase();
                LOGGER.info("File [" + media.getPath() + "] is a video media type. Copied only");
            }

            else
            {
                fname = String.format("%s%d%s.%s", getPrefix(), k, (embedDateTime() ? DF.format(media.getTimestamp()) : ""), fileExtension);
            }

            copied = getTargetDirectory().resolve(fname);
            captureTime = FileTime.fromMillis(media.getTimestamp());

            try
            {
                if (isDateChangeForced() || media.isMetadataEmpty())
                {
                    if (media.isJPG())
                    {
                        Files.copy(media.getPath(), copied, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        JpgDatePatcher.patchAllDates(copied, captureTime, false);
                    }

                    else if (media.isTIF())
                    {
                        BatchMetadataUtils.updateDateTakenMetadataTIF(media.getPath().toFile(), copied.toFile(), captureTime);
                    }

                    else if (media.isPNG())
                    {
                        // BatchMetadataUtils.updateDateTakenTextualPNG(media.getPath().toFile(), copied.toFile(), captureTime);
                        Files.copy(media.getPath(), copied, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        PngDatePatcher.patchAllDates(copied, captureTime, true);
                    }

                    else if (media.isHEIC())
                    {
                        Files.copy(media.getPath(), copied, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        HeifDatePatcher.patchAllDates(copied, captureTime, true);
                    }

                    else
                    {
                        Files.copy(media.getPath(), copied, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }

                else
                {
                    Files.copy(media.getPath(), copied, StandardCopyOption.COPY_ATTRIBUTES);
                }

                Utils.updateFileTimeStamps(copied, captureTime);
            }

            catch (IOException exc)
            {
                LOGGER.error("Error detected: [" + exc.getMessage() + "]", exc);
                exc.printStackTrace();
            }
        }
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
            // This is crucial to ensure no silent failures are allowed
            LOGGER.error(exc.getMessage());
            exc.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        BatchConsole.readCommand(args);
    }
}