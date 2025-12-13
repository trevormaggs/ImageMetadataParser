package common;

import java.io.IOException;

/**
 * Defines the contract for a handler that processes image files and extracts structured metadata.
 * Implementations are responsible for parsing binary image data and producing metadata in the form
 * of a {@link common.MetadataStrategy} instance.
 *
 * <p>
 * This interface allows for extensibility to support different image formats, for example: TIFF,
 * PNG, JPEG, etc, each with its own parsing and metadata representation logic.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.0
 * @since 13 August 2025
 */
public interface ImageHandler
{
    /**
     * Parses the image data and attempts to extract metadata.
     * 
     * <p>
     * Implementations should read the relevant sections of the image file and populate their
     * internal metadata structures.
     * </p>
     *
     * @return true if metadata was successfully extracted, otherwise false
     *
     * @throws IOException
     *         if a low-level I/O error occurs while reading the image file
     */
    boolean parseMetadata() throws IOException;
}