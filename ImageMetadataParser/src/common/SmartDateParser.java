package common;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A flexible utility for converting date strings of varying formats into {@link Date} objects.
 * 
 * <p>
 * <b>Regional Support and Ambiguity:</b><br>
 * This parser is optimised for <b>Australian/British (DD/MM/YYYY)</b> date formats. In cases of
 * numerical ambiguity, for example: {@code 01/02/2026}, the parser prioritises the Day-Month-Year
 * interpretation (1st February).
 * </p>
 * 
 * <p>
 * <b>Supported Standards:</b>
 * </p>
 * 
 * <ul>
 * <li><b>EXIF Standard:</b> Handles the default {@code yyyy:MM:dd} camera format.</li>
 * <li><b>ISO-8601:</b> Supports {@code T} delimited timestamps with optional sub-seconds.</li>
 * <li><b>International/US:</b> Supports textual month patterns with optional commas, for example:
 * {@code Jan 19, 2026} or {@code Jan 19 2026}.</li>
 * </ul>
 * 
 * @author Trevor Maggs
 * @version 1.0
 * @since 19 January 2026
 */
public final class SmartDateParser
{
    private static final String[] DATE_SEPARATORS = {"/", "-", ":", " "};
    private static final String[] TIME_FORMATS = {" HH:mm:ss", " HH:mm", ""};
    private static final List<DatePattern> MAP_TEMPLATE = new ArrayList<>();
    private static final Map<String, String> regexMap = new LinkedHashMap<String, String>();

    static
    {
        /*
         * Note, for the regexMap, it uses single 'd' and 'M' to allow the parser to handle 4/4/1966
         * and 30/12/1966). Likewise with 'y', which handles Year 1966 and 66.
         */

        // ISO-8601 / T-Format (Strict Pattern)
        MAP_TEMPLATE.add(new DatePattern("\\d{4}-\\d{1,2}-\\d{1,2}T\\d{1,2}:\\d{1,2}:\\d{1,2}.*", "yyyy-M-d'T'HH:mm:ss", true));

        // For Australian Date formats
        regexMap.put("y[sep]M[sep]d", "\\d{4}[sep]\\d{1,2}[sep]\\d{1,2}"); // EXIF Standard
        regexMap.put("d[sep]M[sep]y", "\\d{1,2}[sep]\\d{1,2}[sep]\\d{4}"); // AU Numerical
        regexMap.put("d[sep]MMM[sep]y", "\\d{1,2}[sep]\\w{3}[sep]\\d{4}"); // AU Textual
        regexMap.put("y[sep]MMM[sep]d", "\\d{4}[sep]\\w{3}[sep]\\d{1,2}"); // Pro-software Standard

        // For Indian/US format with optional comma, i.e. "Jan 19, 2026" or "Jan 19 2026"
        regexMap.put("MMM[sep]d[sep]y", "\\w{3}[sep]\\d{1,2},?[sep]\\d{4}");

        for (Map.Entry<String, String> entry : regexMap.entrySet())
        {
            for (String sep : DATE_SEPARATORS)
            {
                String pattern = entry.getKey().replace("[sep]", sep);
                String regex = entry.getValue().replace("[sep]", sep) + ".*";

                MAP_TEMPLATE.add(new DatePattern(regex, pattern, false));
            }
        }
    }

    /**
     * Container for mapping a regex identification string to a specific DateTime pattern.
     */
    private static class DatePattern
    {
        final String regex;
        final String formatPattern;
        final boolean isFullDateTime;

        private DatePattern(String regex, String formatPattern, boolean isFullDateTime)
        {
            this.regex = regex;
            this.formatPattern = formatPattern;
            this.isFullDateTime = isFullDateTime;
        }
    }

    /**
     * Attempts to parse a string into a {@link Date} object by matching it against a list of known
     * patterns.
     *
     * @param input
     *        the date string to parse
     * @return a {@link Date} object if parsing is successful
     * 
     * @throws IllegalArgumentException
     *         if the input is null or does not match any known format
     */
    public static Date convertToDate(String input)
    {
        if (input != null)
        {
            String cleaned = input.trim();

            for (DatePattern map : MAP_TEMPLATE)
            {
                if (cleaned.matches(map.regex))
                {
                    if (map.isFullDateTime)
                    {
                        Date d = parseISO_8601(cleaned, map.formatPattern);

                        if (d != null)
                        {
                            return d;
                        }
                    }

                    else
                    {
                        Date d = parseDateTime(cleaned, map.formatPattern);

                        if (d != null)
                        {
                            return d;
                        }
                    }
                }
            }
        }

        throw new IllegalArgumentException("Unsupported date format [" + input + "]");
    }

    /**
     * Internal helper to parse ISO-8601 "T" delimited strings. This method handles optional
     * sub-seconds and 'Z' indicators to normalise the string before parsing into the system's local
     * time zone.
     * 
     * @param input
     *        the raw date string, i.e. 2026-01-19T18:30:00.123Z
     * @param pattern
     *        the DateTimeFormatter pattern to apply, i.e. yyyy-M-d'T'HH:mm:ss
     * @return a {@link Date} object if successful, otherwise {@code null} if parsing fails
     */
    private static Date parseISO_8601(String input, String pattern)
    {
        try
        {
            // Remove everything after the seconds: sub-seconds, 'Z', or Timezone offsets This
            // targets the 'T' format specifically.

            // Example: Raw: 2026-01-19T18:30:00-05:00, Cleaned: 2026-01-19T18:30:00
            String normalised = input.replaceAll("(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?$", "");

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);

            return Date.from(LocalDateTime.parse(normalised, dtf).atZone(ZoneId.systemDefault()).toInstant());
        }

        catch (Exception exc)
        {
            return null;
        }
    }

    /**
     * Internal helper to try various time suffixes against a base date pattern. This method
     * iterates through {@code TIME_FORMATS} until a successful match is found. Note that it also
     * handles the "optional comma" scenario, specifically in Indian and US formats.
     *
     * @param input
     *        the cleaned date string to parse
     * @param pattern
     *        the base date pattern without time, i.e. d/M/y
     * @return a {@link Date} object representing the date/time, otherwise {@code null} if no
     *         suffixes result in a valid match
     */
    private static Date parseDateTime(String input, String pattern)
    {
        String normalisedInput = input.replace(",", "");
        String normalisedPattern = pattern.replace(",", "");

        for (String suffix : TIME_FORMATS)
        {
            try
            {
                String fullPattern = normalisedPattern + suffix;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(fullPattern, Locale.ENGLISH);

                if (fullPattern.contains("HH"))
                {
                    return Date.from(LocalDateTime.parse(normalisedInput, dtf).atZone(ZoneId.systemDefault()).toInstant());
                }

                else
                {
                    return Date.from(LocalDate.parse(normalisedInput, dtf).atStartOfDay(ZoneId.systemDefault()).toInstant());
                }
            }

            catch (DateTimeParseException exc)
            {
                // Continue to next time format
            }
        }

        return null;
    }
}