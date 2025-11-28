package common;

/**
 * Utility class for common string constants used for logging and formatting metadata output across
 * various components.
 */
public final class MetadataConstants
{
    /**
     * Format string for printing key/value pairs (i.e. Tag: Value).
     * Output: -20s: %s\n
     */
    public static final String FORMATTER = "%-20s:\t%s%n";

    /**
     * Separator line used for visual distinction between directories.
     */
    public static final String DIVIDER = "--------------------------------------------------";

    private MetadataConstants()
    {
        // Prevent instantiation
    }
}