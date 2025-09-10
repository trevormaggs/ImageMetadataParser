package tif;

import java.nio.ByteOrder;
import batch.BatchErrorException;

/**
 * Defines a contract for parsing raw byte data from a TIFF file into
 * a specific Java object type.
 */
public interface TifDataParser {
    /**
     * Parses the raw byte array into a typed Java object.
     * @param value The raw byte array.
     * @param count The number of values represented by the data.
     * @param order The byte order (endianness) of the data.
     * @return The parsed object.
     * @throws BatchErrorException if parsing fails.
     */
    Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException;
}