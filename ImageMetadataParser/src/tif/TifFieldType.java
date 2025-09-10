package tif;

import java.nio.ByteOrder;
import batch.BatchErrorException;
import common.ByteValueConverter;
import common.RationalNumber;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;

/**
 * An enumeration class of data format types defined in the TIFF specification 6.0 document.
 * 
 * @author Trevor Maggs
 * @version 1.1
 * @since 14 August 2025
 */
public enum TifFieldType
{
    TYPE_ERROR(0, "Unknown type. Error", 0)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            throw new BatchErrorException("No parser defined for TifFieldType [" + this.name() + "]");
        }
    },

    TYPE_BYTE_U(1, "Flag for 8-bit unsigned integer", 1)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            if (count > 1)
            {
                int[] unsignedBytes = new int[count];

                for (int i = 0; i < count; i++)
                {
                    unsignedBytes[i] = Byte.toUnsignedInt(value[i]);
                }

                return unsignedBytes;
            }

            else
            {
                return Byte.toUnsignedInt(value[0]);
            }
        }
    },

    TYPE_ASCII(2, "Flag for null terminated ASCII string", 1)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            if (value == null || value.length == 0)
            {
                return "";
            }

            int nullIndex = 0;

            while (nullIndex < value.length && value[nullIndex] != 0)
            {
                nullIndex++;
            }

            return new String(value, 0, nullIndex, StandardCharsets.UTF_8);
        }
    },

    TYPE_SHORT_U(3, "Flag for 16-bit unsigned integer (2 bytes)", 2)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            if (count > 1)
            {
                int[] unsignedShorts = new int[count];

                for (int i = 0; i < count; i++)
                {
                    unsignedShorts[i] = ByteValueConverter.toUnsignedShort(value, i * 2, order);
                }

                return unsignedShorts;
            }

            else
            {
                return ByteValueConverter.toUnsignedShort(value, 0, order);
            }
        }
    },

    TYPE_LONG_U(4, "Flag for 32-bit unsigned integer (4 bytes)", 4)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            if (count > 1)
            {
                long[] unsignedLongs = new long[count];

                for (int i = 0; i < count; i++)
                {
                    unsignedLongs[i] = ByteValueConverter.toUnsignedInteger(value, i * 4, order);
                }

                return unsignedLongs;
            }

            else
            {
                return ByteValueConverter.toUnsignedInteger(value, 0, order);
            }
        }
    },

    TYPE_RATIONAL_U(5, "Flag for pairs of unsigned integers", 8)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            if (count > 1)
            {
                RationalNumber[] rationalsU = new RationalNumber[count];

                for (int i = 0; i < count; i++)
                {
                    rationalsU[i] = ByteValueConverter.toRational(value, i * 8, order, RationalNumber.DataType.UNSIGNED);
                }

                return rationalsU;
            }

            else
            {
                return ByteValueConverter.toRational(value, 0, order, RationalNumber.DataType.UNSIGNED);
            }
        }
    },

    TYPE_BYTE_S(6, "Flag for 8-bit signed integer", 1)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            return (count == 1) ? value[0] : Arrays.copyOf(value, count);
        }
    },

    TYPE_UNDEFINED(7, "Flag for 8 bit uninterpreted byte", 1)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            return Arrays.copyOf(value, value.length);
        }
    },

    TYPE_SHORT_S(8, "Flag for 16-bit signed integer (2 bytes)", 2)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            if (count > 1)
            {
                short[] signedShorts = new short[count];

                for (int i = 0; i < count; i++)
                {
                    signedShorts[i] = ByteValueConverter.toShort(value, i * 2, order);
                }

                return signedShorts;
            }

            else
            {
                return ByteValueConverter.toShort(value, 0, order);
            }
        }
    },

    TYPE_LONG_S(9, "Flag for 32-bit signed integer (4 bytes)", 4)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            if (count > 1)
            {
                int[] signedLongs = new int[count];

                for (int i = 0; i < count; i++)
                {
                    signedLongs[i] = ByteValueConverter.toInteger(value, i * 4, order);
                }

                return signedLongs;
            }

            else
            {
                return ByteValueConverter.toInteger(value, 0, order);
            }
        }
    },

    TYPE_RATIONAL_S(10, "Flag for pairs of signed integers", 8)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            if (count > 1)
            {
                RationalNumber[] rationalsS = new RationalNumber[count];

                for (int i = 0; i < count; i++)
                {
                    rationalsS[i] = ByteValueConverter.toRational(value, i * 8, order, RationalNumber.DataType.SIGNED);
                }

                return rationalsS;
            }

            else
            {
                return ByteValueConverter.toRational(value, 0, order, RationalNumber.DataType.SIGNED);
            }
        }
    },

    TYPE_FLOAT(11, "Flag for single precision float (4 bytes)", 4)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            if (count > 1)
            {
                float[] floats = new float[count];

                for (int i = 0; i < count; i++)
                {
                    floats[i] = ByteValueConverter.toFloat(value, i * 4, order);
                }

                return floats;
            }

            else
            {
                return ByteValueConverter.toFloat(value, 0, order);
            }
        }
    },

    TYPE_DOUBLE(12, "Flag for double precision double (8 bytes)", 8)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            if (count > 1)
            {
                double[] doubles = new double[count];

                for (int i = 0; i < count; i++)
                {
                    doubles[i] = ByteValueConverter.toDouble(value, i * 8, order);
                }

                return doubles;
            }

            else
            {
                return ByteValueConverter.toDouble(value, 0, order);
            }
        }
    },

    TYPE_IFD_POINTER(13, "Flag for IFD pointer defined in TIFF Tech Note 1 in TIFF Specification Supplement 1", 4)
    {
        @Override
        public Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException
        {
            if (count > 1)
            {
                long[] unsignedLongs = new long[count];

                for (int i = 0; i < count; i++)
                {
                    unsignedLongs[i] = ByteValueConverter.toUnsignedInteger(value, i * 4, order);
                }

                return unsignedLongs;
            }

            else
            {
                return ByteValueConverter.toUnsignedInteger(value, 0, order);
            }
        }
    };

    public static final int MIN_DATATYPE = TYPE_BYTE_U.getDataType();
    public static final int MAX_DATATYPE = TYPE_IFD_POINTER.getDataType();
    private final int dataType;
    private final String description;
    private final int elementLength;

    private TifFieldType(int fmt, String desc, int len)
    {
        this.dataType = fmt;
        this.description = desc;
        this.elementLength = len;
    }

    /**
     * Retrieves the data format type associated with this field.
     *
     * @return the data format type as an integer
     */
    public int getDataType()
    {
        return dataType;
    }

    /**
     * Retrieves the description.
     *
     * @return the description as a string
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Retrieves the length associated with this field.
     *
     * @return the length as an integer
     */
    public int getElementLength()
    {
        return elementLength;
    }

    /**
     * Searches through the enumeration to find the TifFieldType constant that matches the specified
     * data type.
     *
     * @param typeCode
     *        the data type to search for
     * 
     * @return the TifFieldType constant representing the data type
     */
    public static TifFieldType getTiffType(int typeCode)
    {
        for (TifFieldType type : values())
        {
            if (type.dataType == typeCode)
            {
                return type;
            }
        }

        return TYPE_ERROR;
    }

    /**
     * Verifies if the given data type can be used for the data associated with this tag.
     *
     * @param dataType
     *        the data type to be checked, such as {@code TYPE_BYTE_U}, {@code TYPE_SHORT_S}, etc
     * 
     * @return a boolean indicating whether the given data type can be used with this tag
     * 
     * @throws IllegalArgumentException
     *         if the data type is less than {@code MIN_DATATYPE} or greater than
     *         {@code MAX_DATATYPE}
     */
    public static boolean dataTypeinRange(int dataType)
    {
        if (dataType < MIN_DATATYPE || dataType > MAX_DATATYPE)
        {
            throw new IllegalArgumentException("Data format type not in range. Illegal value detected.");
        }

        return (1 << dataType > 0);
    }

    /**
     * Verifies that this tag points to an IFD structure containing additional tags.
     *
     * @return boolean {@code true} if this tag indicates an IFD structure
     */
    public boolean isIFDPointer()
    {
        return (this == TYPE_IFD_POINTER);
    }

    /**
     * Verifies that this tag contains a numeric value.
     *
     * @return boolean {@code true} if the value is numeric
     */
    public boolean isNumber()
    {
        switch (this)
        {
            case TYPE_SHORT_U:
            case TYPE_LONG_U:
            case TYPE_SHORT_S:
            case TYPE_LONG_S:
            case TYPE_FLOAT:
            case TYPE_DOUBLE:
                return true;

            default:
                return false;
        }
    }

    /**
     * Verifies that this tag contains a string value.
     *
     * @return boolean {@code true} if the value is a string
     */
    public boolean isString()
    {
        return (this == TYPE_ASCII);
    }

    /**
     * Confirms that this tag contains a Rational Number class object.
     *
     * @return boolean {@code true} if the value is stored in a Rational Number object
     */
    public boolean isRationalNumber()
    {
        return (this == TYPE_RATIONAL_U || this == TYPE_RATIONAL_S);
    }

    /**
     * Confirms that this tag contains a byte value.
     *
     * @return boolean {@code true} if the value is a byte
     */
    public boolean isByteData()
    {
        return (this == TYPE_BYTE_U || this == TYPE_BYTE_S);
    }

    /**
     * Parses the raw bytes according to this type's implementation.
     * 
     * @param value
     *        the raw bytes
     * @param count
     *        the number of values
     * @param order
     *        the byte order
     * @return the parsed object
     * 
     * @throws BatchErrorException
     *         if parsing fails
     */
    public abstract Object parse(byte[] value, int count, ByteOrder order) throws BatchErrorException;
}