package tif;

import logger.LogFactory;

public final class TiffDatePatcher
{
    private static final LogFactory LOGGER = LogFactory.getLogger(TiffDatePatcher.class);

    /**
     * Default constructor is unsupported and will always throw an exception.
     *
     * @throws UnsupportedOperationException
     *         to indicate that instantiation is not supported
     */
    private TiffDatePatcher()
    {
        throw new UnsupportedOperationException("Not intended for instantiation");
    }
}