package common;

import java.util.Objects;

/**
 * Represents a rational number (numerator/divisor) used in TIFF/EXIF metadata.
 *
 * <p>
 * This class stores values as 64-bit longs to safely accommodate 32-bit unsigned integers from EXIF
 * specifications. All instances are normalised (reduced to lowest terms with a positive divisor)
 * upon creation to ensure mathematical equality and consistent hashing.
 * </p>
 *
 * @author Trevor Maggs
 * @version 1.1
 * @since 27 November 2025
 */
public class RationalNumber2 extends Number
{
    public enum DataType
    {
        SIGNED,
        UNSIGNED;
    }

    public final long numerator;
    public final long divisor;
    public final boolean unsignedType;

    /**
     * Core private constructor used to handle sign normalisation and fraction reduction. Firstly,
     * it normalises signs (Divisor should always be positive) and then reduces the fraction.
     *
     * @param num
     *        the input numerator
     * @param div
     *        the input divisor
     * @param type
     *        original data type (SIGNED or UNSIGNED)
     * @param normalise
     *        if true, it reduces the fraction via GCD
     * @throws ArithmeticException
     *         if the divisor is zero and numerator is non-zero
     */
    private RationalNumber2(long num, long div, DataType type, boolean normalise)
    {
        if (div == 0 && num != 0)
        {
            throw new ArithmeticException("Denominator cannot be zero for non-zero numerator");
        }

        if (div < 0)
        {
            num = -num;
            div = -div;
        }

        if (normalise && num != 0 && div != 0)
        {
            long common = gcd(Math.abs(num), div);

            if (common != 0)
            {
                num /= common;
                div /= common;
            }
        }

        this.unsignedType = (type == DataType.UNSIGNED);
        this.numerator = num;
        this.divisor = div;
    }

    /**
     * Constructs an instance handling 32-bit signed or unsigned integers.
     *
     * @param num
     *        32-bit numerator
     * @param div
     *        32-bit divisor
     * @param type
     *        specifies interpretation as SIGNED or UNSIGNED
     */
    public RationalNumber2(int num, int div, DataType type)
    {
        boolean isUnsigned = (type == DataType.UNSIGNED);
        long n = isUnsigned ? (num & 0xFFFFFFFFL) : num;
        long d = isUnsigned ? (div & 0xFFFFFFFFL) : div;

        if (n == 0 && d != 0)
        {
            this.numerator = 0;
            this.divisor = 1;
        }

        else
        {
            // Initialise using the internal logic to ensure reduction
            RationalNumber2 result = new RationalNumber2(n, d, type, true);
            this.numerator = result.numerator;
            this.divisor = result.divisor;
        }

        this.unsignedType = isUnsigned;
    }

    /**
     * Constructs a signed instance from 32-bit signed integers.
     *
     * @param num
     *        32-bit signed numerator
     * @param div
     *        32-bit signed divisor
     */
    public RationalNumber2(int num, int div)
    {
        this(num, div, DataType.SIGNED);
    }

    /**
     * Static factory to obtain a simplified fraction.
     *
     * @param numerator
     *        the top number
     * @param divisor
     *        the bottom number
     * @param type
     *        the data type flag
     * @return a normalised RationalNumber2 instance
     */
    public static RationalNumber2 simplify(long numerator, long divisor, DataType type)
    {
        return new RationalNumber2(numerator, divisor, type, true);
    }

    /**
     * @return the integer value of the fraction, truncated toward zero
     */
    @Override
    public int intValue()
    {
        return (int) getFraction();
    }

    /**
     * @return the long value of the fraction, truncated toward zero
     */
    @Override
    public long longValue()
    {
        return (long) getFraction();
    }

    /**
     * @return the floating-point value of the fraction
     */
    @Override
    public float floatValue()
    {
        return (float) getFraction();
    }

    /**
     * @return the double-precision floating-point value of the fraction
     */
    @Override
    public double doubleValue()
    {
        return getFraction();
    }

    /**
     * Compares this instance with another object for mathematical equality.
     * 
     * <p>
     * Equality is determined by comparing the normalised numerator, divisor, and the original data
     * type interpretation.
     * </p>
     * 
     * @param obj
     *        the object to compare
     * @return true if mathematically equal and of the same data type
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (!(obj instanceof RationalNumber2))
        {
            return false;
        }

        RationalNumber2 other = (RationalNumber2) obj;

        return numerator == other.numerator && divisor == other.divisor && unsignedType == other.unsignedType;
    }

    /**
     * @return a hash code based on the normalised state and data type
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(numerator, divisor, unsignedType);
    }

    /**
     * Returns a string representation. Returns a decimal if the value is a whole
     * number, otherwise returns the fraction with a decimal approximation.
     */
    @Override
    public String toString()
    {
        if (divisor == 0)
        {
            return String.format("Invalid rational (NaN) [%d/%d]", numerator, divisor);
        }

        double val = getFraction();

        if (numerator % divisor == 0)
        {
            return String.format("%.0f", val);
        }

        return String.format("%d/%d (%.4f)", numerator, divisor, val);
    }

    /**
     * Negates the rational number.
     * <p>
     * Returns a new normalised instance with the numerator's sign flipped.
     * The resulting instance is always cast to {@link DataType#SIGNED}.
     * </p>
     * 
     * @return a negated RationalNumber2 instance
     */
    public RationalNumber2 negate()
    {
        return simplify(-numerator, divisor, DataType.SIGNED);
    }

    /**
     * @return true if the rational represents a whole number
     */
    public boolean hasIntegerValue()
    {
        return divisor != 0 && (numerator % divisor == 0);
    }
    /**
     * Returns a user-friendly string representation.
     * 
     * @param decimalAllowed
     *        if true, allows short decimal output for non-integers
     * @return simplified string representation
     */
    public String toSimpleString(boolean decimalAllowed)
    {
        if (hasIntegerValue())
        {
            return Long.toString(longValue());
        }

        if (decimalAllowed)
        {
            String doubleString = Double.toString(doubleValue());

            if (doubleString.length() < 6)
            {
                return doubleString;
            }
        }

        return numerator + "/" + divisor;
    }

    /**
     * Recursive implementation of the Euclidean algorithm to find the GCD.
     */
    private static long gcd(long a, long b)
    {
        return (b == 0) ? a : gcd(b, a % b);
    }

    /**
     * Computes the floating-point value.
     * 
     * @throws ArithmeticException
     *         if divisor is zero
     */
    private double getFraction()
    {
        if (divisor == 0)
        {
            throw new ArithmeticException("Division by zero");
        }

        return (double) numerator / (double) divisor;
    }
}