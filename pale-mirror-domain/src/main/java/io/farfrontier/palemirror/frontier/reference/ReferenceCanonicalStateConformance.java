package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cross-runtime state fingerprint with bounded binary64 tail-bit tolerance. */
final class ReferenceCanonicalStateConformance {
    private static final int FLOAT_ULP_BUCKET_BITS = 12;
    private static final long FRACTION_MASK = (1L << 52) - 1L;

    private ReferenceCanonicalStateConformance() { }

    static String sha256(Object state) {
        return ReferenceV2PublicSnapshot.sha256(normalize(state));
    }

    private static Object normalize(Object value) {
        if (value instanceof Double number) return Map.of("$float_ulp4096", floatBucket(number));
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("canonical conformance map key must be a string");
                }
                result.put(key, normalize(entry.getValue()));
            }
            return Map.copyOf(result);
        }
        if (value instanceof List<?> sequence) {
            ArrayList<Object> result = new ArrayList<>(sequence.size());
            for (Object item : sequence) result.add(normalize(item));
            return List.copyOf(result);
        }
        return value;
    }

    private static String floatBucket(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("canonical state codec rejects non-finite float");
        long bits = Double.doubleToRawLongBits(value);
        long sign = bits >>> 63;
        long exponent = (bits >>> 52) & 0x7ffL;
        long fraction = bits & FRACTION_MASK;
        if (exponent == 0L) return "subnormal:" + sign + ":" + fraction;
        long retained = fraction >>> FLOAT_ULP_BUCKET_BITS;
        long discarded = fraction & ((1L << FLOAT_ULP_BUCKET_BITS) - 1L);
        long midpoint = 1L << (FLOAT_ULP_BUCKET_BITS - 1);
        if (discarded > midpoint || (discarded == midpoint && (retained & 1L) == 1L)) retained++;
        if (retained == (1L << (52 - FLOAT_ULP_BUCKET_BITS))) {
            retained = 0L;
            exponent++;
        }
        return "normal:" + sign + ":" + exponent + ":" + retained;
    }
}
