package common;

import java.util.Objects;

/**
 * This class represents a rational number, typically used in the TIFF/EXIF image format. TIFF
 * rational numbers are defined as pairs of 32-bit integers, encompassing numerator and denominator.
 * 
 * This class stores the numerator and denominator as 64-bit long integers to safely handle 32-bit
 * unsigned inputs from TIFF/EXIF specifications. All instances are automatically normalised
 * (reduced to the lowest terms with a positive divisor) upon creation to ensure mathematical
 * equality and consistent hash codes.
 * 
 * @author Trevor Maggs
 * @version 1.1
 * @since 27 November 2025
 */
public class RationalNumber extends Number
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
     * The core private constructor used by all public constructors and the {@code simplify} method
     * to create new {@code RationalNumber} instances.
     * 
     * <p>
     * This method performs two key steps before assignment:
     * </p>
     * 
     * <ol>
     * <li>{@code Sign Normalisation:} Ensures the {@code divisor} is always positive by moving the
     * sign to the {@code numerator} if necessary (e.g., -1 / -2 becomes 1 / 2).</li>
     * <li>{@code Reduction (Simplification):} Optionally reduces the fraction to its lowest terms
     * using the greatest common divisor (GCD) if the {@code normalise} flag is {@code true}.</li>
     * </ol>
     *
     * @param num
     *        the input numerator (64-bit long)
     * @param div
     *        the input divisor (64-bit long)
     * @param type
     *        the original data type interpretation (SIGNED or UNSIGNED)
     * @param normalise
     *        if true, the fraction will be fully reduced (simplified) via GCD
     * 
     * @throws ArithmeticException
     *         if the divisor is zero and the numerator is non-zero
     */
    private RationalNumber(long num, long div, DataType type, boolean normalise)
    {
        if (div == 0 && num != 0)
        {
            throw new ArithmeticException("Denominator cannot be zero for non-zero numerator");
        }

        // Step 1: normalise signs (Divisor should always be positive)
        if (div < 0)
        {
            num = -num;
            div = -div;
        }

        // Step 2: Reduce the fraction
        if (normalise && num != 0)
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
     * Constructs an instance to handle both signed and unsigned 32-bit integers. The input values
     * are masked to 64-bit longs if {@code DataType.UNSIGNED} is specified. The resulting rational
     * number is normalised (reduced).
     *
     * @param num
     *        a numerator in either 32-bit signed or unsigned format. 
     * @param div
     *        a non-zero divisor in either 32-bit signed or unsigned format
     * @param type
     *        specifies whether the values should be interpreted as {@code DataType.UNSIGNED} or
     *        {@code DataType.SIGNED}
     */
    public RationalNumber(int num, int div, DataType type)
    {
        boolean isUnsigned = (type == DataType.UNSIGNED);
        long n = isUnsigned ? (num & 0xFFFFFFFFL) : num;
        long d = isUnsigned ? (div & 0xFFFFFFFFL) : div;

        this.unsignedType = isUnsigned;

        if (n == 0)
        {
            this.numerator = 0;
            this.divisor = 1;
        }

        else
        {
            // Use the normalising constructor to simplify the fraction
            RationalNumber normalised = simplify(n, d, type);
            this.numerator = normalised.numerator;
            this.divisor = normalised.divisor;
        }
    }

    /**
     * Constructs a signed instance, interpreting inputs as standard 32-bit signed integers. The
     * resulting rational number is normalised (reduced).
     *
     * @param num
     *        a numerator in 32-bit signed format
     * @param div
     *        a non-zero divisor in 32-bit signed format
     */
    public RationalNumber(int num, int div)
    {
        this(num, div, DataType.SIGNED);
    }

    /**
     * Simplifies or reduces a fraction by finding the greatest common divisor (GCD). Note, it calls
     * the private normalising constructor to obtain a simplified fraction.
     *
     * @param numerator
     *        the top number of the fraction
     * @param divisor
     *        the bottom number of the fraction
     * @param type
     *        a flag indicating the type for the resulting instance
     * @return An instance of the RationalNumber class, representing the simplified fraction
     */
    public static RationalNumber simplify(long numerator, long divisor, DataType type)
    {
        return new RationalNumber(numerator, divisor, type, true);
    }

    /**
     * Returns a new instance with the value negated (multiplied by -1). The resulting instance is
     * always treated as SIGNED, and the fraction is normalised.
     * 
     * Basically, it negates the numerator and simplifies to ensure the divisor remains positive.
     *
     * @return a valid instance with a negated value
     */
    public RationalNumber negate()
    {
        return simplify(-numerator, divisor, DataType.SIGNED);
    }

    /**
     * Returns the integer representation of the rational number. It performs the conversion by
     * dividing the numerator by the denominator and truncating the result toward zero.
     * 
     * Note that this operation may result in a loss of precision.
     * 
     * @return the integer value after computation (truncated)
     */
    @Override
    public int intValue()
    {
        return (int) getFraction();
    }

    /**
     * Returns the long representation of the rational number. It performs the conversion by
     * dividing the numerator by the denominator and truncating the result toward zero.
     * 
     * Note that this operation may result in a loss of precision.
     * 
     * @return the long value after computation (truncated)
     */
    @Override
    public long longValue()
    {
        // Truncation toward zero
        return (long) getFraction();
    }

    /**
     * Returns the single-precision floating-point representation of the rational number. It obtains
     * a double-precision value during computation to maintain as much precision from the original
     * numerator and denominator as possible before casting to {@code float}.
     *
     * @return the floating-point value after computation
     */
    @Override
    public float floatValue()
    {
        return (float) getFraction();
    }

    /**
     * Returns the double-precision floating-point representation of the rational number.
     *
     * @return the double value after computation
     */
    @Override
    public double doubleValue()
    {
        return getFraction();
    }

    @Override
    public String toString()
    {
        if (divisor == 0)
        {
            return String.format("Invalid rational number detected (%d/%d)", numerator, divisor);
        }

        // Formats to four decimal places using the root locale
        String decimalString = String.format("%.4f", getFraction());

        if (numerator % divisor == 0)
        {
            return decimalString;
        }

        else
        {
            return String.format("%d/%d (%s)", numerator, divisor, decimalString);
        }
    }

    /**
     * Compares this rational number to the specified object. The comparison is based on the
     * mathematically {@code normalised} state (reduced numerator and positive divisor). Two
     * {@code RationalNumber} instances are considered equal if their normalised numerator,
     * normalised divisor, and {@code unsignedType} flag are identical.
     * 
     * @param obj
     *        the object to compare against
     * @return true if the specified object is mathematically equal to this rational number and
     *         shares the same data type interpretation, false otherwise
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (!(obj instanceof RationalNumber))
        {
            return false;
        }

        RationalNumber other = (RationalNumber) obj;

        return (numerator == other.numerator && divisor == other.divisor && unsignedType == other.unsignedType);
    }

    /**
     * Returns the hash code for this rational number. The hash code is generated from the
     * {@code normalised} numerator, normalised divisor, and the {@code unsignedType} flag. This
     * ensures that mathematically equal rational numbers, for example: 1/2 and 2/4, have the same
     * hash code.
     * 
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(numerator, divisor, unsignedType);
    }

    /**
     * Checks if the rational number represents a whole number (an integer value).
     *
     * @return true if the division results in a whole number, false otherwise.
     */
    public boolean hasIntegerValue()
    {
        return divisor == 1 || (divisor != 0 && (numerator % divisor == 0)) || (divisor == 0 && numerator == 0);
    }

    /**
     * Returns the string representation of the rational number, favouring a simple integer if the
     * value is a whole number.
     *
     * @param decimalAllowed
     *        if true, allows a short decimal representation (e.g., "0.5") for simple fractions
     * 
     * @return the simple string representation
     */
    public String toSimpleString(boolean decimalAllowed)
    {
        if (hasIntegerValue())
        {
            return Integer.toString(intValue());
        }

        else
        {
            RationalNumber simplifiedInstance = simplify(numerator, divisor, unsignedType ? DataType.UNSIGNED : DataType.SIGNED);

            if (decimalAllowed)
            {
                String doubleString = Double.toString(simplifiedInstance.doubleValue());

                // Return short decimals like "0.5" or "1.33"
                if (doubleString.length() < 5)
                {
                    return doubleString;
                }
            }

            return simplifiedInstance.toString();
        }
    }

    /**
     * Computes the greatest common divisor in a recursive fashion, based on the Euclidean
     * algorithm.
     *
     * @param a
     *        the first number
     * @param b
     *        the second number
     *
     * @return the greatest common divisor of a and b
     */
    private static long gcd(long a, long b)
    {
        if (b == 0)
        {
            return a;
        }

        return gcd(b, a % b);
    }

    /**
     * Computes and returns the rational number as a double-precision floating-point value.
     *
     * @return the floating-point result of numerator divided by divisor
     *
     * @throws ArithmeticException
     *         if the divisor is zero
     */
    private double getFraction()
    {
        if (divisor == 0)
        {
            throw new ArithmeticException("Denominator cannot be zero");
        }

        return (double) numerator / (double) divisor;
    }
}