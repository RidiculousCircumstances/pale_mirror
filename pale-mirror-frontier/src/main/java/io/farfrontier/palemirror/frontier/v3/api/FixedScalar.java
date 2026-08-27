package io.farfrontier.palemirror.frontier.v3.api;

import java.math.RoundingMode;
import java.util.Objects;

/** Signed fixed-point scalar with six decimal places and checked arithmetic. */
public record FixedScalar(long raw) implements Comparable<FixedScalar> {
    public static final long SCALE = 1_000_000L;
    public static final FixedScalar ZERO = new FixedScalar(0L);
    public static final FixedScalar ONE = new FixedScalar(SCALE);

    public static FixedScalar whole(long whole) {
        return new FixedScalar(Math.multiplyExact(whole, SCALE));
    }

    public static FixedScalar fraction(long numerator, long denominator, RoundingMode rounding) {
        if (denominator == 0L) {
            throw new ArithmeticException("fixed scalar denominator cannot be zero");
        }
        Objects.requireNonNull(rounding, "rounding");
        long scaledNumerator = Math.multiplyExact(numerator, SCALE);
        long quotient = scaledNumerator / denominator;
        long remainder = scaledNumerator % denominator;
        if (remainder == 0L) {
            return new FixedScalar(quotient);
        }
        return new FixedScalar(adjust(quotient, remainder, denominator, rounding));
    }

    public FixedScalar plus(FixedScalar other) {
        return new FixedScalar(Math.addExact(raw, other.raw));
    }

    public FixedScalar minus(FixedScalar other) {
        return new FixedScalar(Math.subtractExact(raw, other.raw));
    }

    public FixedScalar multiply(long multiplier) {
        return new FixedScalar(Math.multiplyExact(raw, multiplier));
    }

    public FixedScalar multiply(FixedScalar other, RoundingMode rounding) {
        return fraction(Math.multiplyExact(raw, other.raw), SCALE, rounding);
    }

    @Override
    public int compareTo(FixedScalar other) {
        return Long.compare(raw, other.raw);
    }

    private static long adjust(long quotient, long remainder, long denominator, RoundingMode rounding) {
        int sign = Long.signum(remainder) == Long.signum(denominator) ? 1 : -1;
        return switch (rounding) {
            case DOWN -> quotient;
            case UP -> Math.addExact(quotient, sign);
            case FLOOR -> sign < 0 ? Math.addExact(quotient, -1L) : quotient;
            case CEILING -> sign > 0 ? Math.addExact(quotient, 1L) : quotient;
            case HALF_UP, HALF_DOWN, HALF_EVEN -> half(quotient, remainder, denominator, sign, rounding);
            case UNNECESSARY -> throw new ArithmeticException("rounding required");
        };
    }

    private static long half(long quotient, long remainder, long denominator, int sign, RoundingMode rounding) {
        long magnitude = Math.abs(remainder);
        long divisorMagnitude = Math.abs(denominator);
        int comparison = Long.compare(magnitude, divisorMagnitude - magnitude);
        if (comparison < 0 || (comparison == 0 && rounding == RoundingMode.HALF_DOWN)) {
            return quotient;
        }
        if (comparison == 0 && rounding == RoundingMode.HALF_EVEN && (quotient & 1L) == 0L) {
            return quotient;
        }
        return Math.addExact(quotient, sign);
    }
}
