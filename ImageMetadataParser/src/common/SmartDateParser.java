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
 * This utility class provides a method to convert a date string into a Date object. While it may
 * not be entirely infallible, every effort has been made to accurately interpret and process the
 * date string using internal logic.
 *
 * If the conversion fails, an exception of type {@code IllegalArgumentException} will be thrown.
 * 
 * <ul>
 * <li>Trevor Maggs created on 19 January 2026</li>
 * </ul>
 *
 * @author Trevor Maggs
 * @version 0.1
 * @since 19 January 2026
 */
public final class SmartDateParser
{
    private static final String[] DATE_SEPARATORS = {"/", "-", ":", " "};
    private static final String[] TIME_FORMATS = {" HH:mm:ss", " HH:mm", ""};
    private static final List<DatePattern> MAP_TEMPLATE = new ArrayList<>();

    static
    {
        // ISO-8601 / T-Format (Strict Pattern)
        MAP_TEMPLATE.add(new DatePattern("\\d{4}-\\d{1,2}-\\d{1,2}T\\d{1,2}:\\d{1,2}:\\d{1,2}.*", "yyyy-M-d'T'HH:mm:ss", true));

        // regexMap (Using single 'd' and 'M' allows the parser to handle 4/4/1966 and 30/12/1966).
        // Same with 'y' which handles 1966 and 66.
        Map<String, String> regexMap = new LinkedHashMap<String, String>();

        // For Australian Date formats
        regexMap.put("y[sep]M[sep]d", "\\d{4}[sep]\\d{1,2}[sep]\\d{1,2}");
        regexMap.put("y[sep]MMM[sep]d", "\\d{4}[sep]\\w{3}[sep]\\d{1,2}");
        regexMap.put("d[sep]M[sep]y", "\\d{1,2}[sep]\\d{1,2}[sep]\\d{4}");
        regexMap.put("d[sep]MMM[sep]y", "\\d{1,2}[sep]\\w{3}[sep]\\d{4}");
        regexMap.put("d[sep]MMM[sep]yy", "\\d{1,2}[sep]\\w{3}[sep]\\d{2}");

        // For Indian Date formats
        regexMap.put("MMM[sep]d,[sep]y", "\\w{3}[sep]\\d{1,2},[sep]\\d{4}");

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
     * Internal helper to parse ISO-8601 "T" delimited strings. Handles stripping sub-seconds and
     * 'Z' indicators to normalise the string before parsing.
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
            // Normalise string by removing fractional seconds or 'Z' indicators
            String part = input.split("\\.")[0].split("Z")[0];
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);

            return Date.from(LocalDateTime.parse(part, dtf).atZone(ZoneId.systemDefault()).toInstant());
        }

        catch (Exception exc)
        {
            return null;
        }
    }

    /**
     * Internal helper to try various time suffixes against a base date pattern. This method
     * iterates through {@code TIME_FORMATS} until a successful match is found.
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
        for (String suffix : TIME_FORMATS)
        {
            try
            {
                String fullPattern = pattern + suffix;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(fullPattern, Locale.ENGLISH);

                if (fullPattern.contains("HH"))
                {
                    // atZone(zone) adds Zone to Date and Time
                    return Date.from(LocalDateTime.parse(input, dtf).atZone(ZoneId.systemDefault()).toInstant());
                }
                else
                {
                    // atStartOfDay(zone) creates both Time and Zone to Date in one go
                    return Date.from(LocalDate.parse(input, dtf).atStartOfDay(ZoneId.systemDefault()).toInstant());
                }
            }
            catch (DateTimeParseException exc)
            {
                /* continue loop to next time format */
            }
        }

        return null;
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
}