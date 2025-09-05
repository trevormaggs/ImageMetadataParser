package batch;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

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
public final class TextUpdater
{
    public TextUpdater() throws BatchErrorException, IOException
    {
    }

    public static void main(String[] args)
    {
        // BatchConsole.readCommand(args);

        try (BufferedReader br = Files.newBufferedReader(Paths.get("ifd0.txt"), StandardCharsets.UTF_8))
        {
            String line;
            int lineNbr = 0;

            while ((line = br.readLine()) != null)
            {
                line = line.trim();

                //System.out.printf("%s\n", line.toUpperCase());

                String[] pwdArr = line.split("\\s", 2);
                String hex = pwdArr[0];
                String tag = pwdArr[1];
                // String hex = pwdArr[2];

                //System.out.printf("%s\t%s\n", tag, hex);

                // String regex = tag.replaceAll("(GPS)(.*)", "$1_$2");//.toUpperCase();

                String regex = tag.replaceAll("([a-z])([A-Z])", "$1_$2");

                System.out.printf("IFD_%s\t%s\n", regex.toUpperCase(), hex.toUpperCase());

                lineNbr++;
            }
        }

        catch (IOException exc)
        {
            exc.printStackTrace();
        }
    }
}