package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxWorldHydratorTest {
    @Test
    void restoresTheCompleteStateAndContinuesFromEachPinnedCheckpoint() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        Set<Integer> checkpoints = Set.of(0, 1, 3, 10, 30);

        for (int day = 0; day <= 30; day++) {
            if (checkpoints.contains(day)) {
                Map<String, Object> expected = ReferenceGrayboxCanonicalState.capture(source);
                ReferenceWorld restored = ReferenceGrayboxWorldHydrator.restore(ReferenceGrayboxStateDocument.encode(expected));
                assertEquals(ReferenceV2PublicSnapshot.canonicalJson(expected),
                        ReferenceV2PublicSnapshot.canonicalJson(ReferenceGrayboxCanonicalState.capture(restored)), "day " + day);
                if (day < 30) {
                    source.tick();
                    restored.tick();
                    assertEquals(null, firstDifference(ReferenceGrayboxCanonicalState.capture(source),
                            ReferenceGrayboxCanonicalState.capture(restored), "state"), "continued day " + (day + 1));
                }
            } else if (day < 30) {
                source.tick();
            }
        }
    }

    @Test
    void restoredWorldRemainsSemanticallyAlignedThroughThirtyMoreDays() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        ReferenceWorld restored = ReferenceGrayboxWorldHydrator.restore(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));

        for (int day = 0; day <= 30; day++) {
            assertEquals(null, firstDifference(ReferenceGrayboxCanonicalState.capture(source),
                    ReferenceGrayboxCanonicalState.capture(restored), "state"), "continued day " + day);
            if (day < 30) {
                source.tick();
                restored.tick();
            }
        }
    }

    @Test
    void rejectsAnIncompleteOwnerBeforeHydratingAnyReplacementWorld() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        Map<String, Object> expected = ReferenceGrayboxCanonicalState.capture(source);
        Map<String, Object> malformed = ReferenceGrayboxStateDocument.decode(ReferenceGrayboxStateDocument.encode(expected));
        Map<String, Object> reference = new LinkedHashMap<>(ReferenceGrayboxStateReader.referenceState(malformed.get("reference_state")));
        reference.put("v2", Map.of());
        malformed.put("reference_state", reference);

        assertThrows(IllegalArgumentException.class,
                () -> ReferenceGrayboxWorldHydrator.restore(ReferenceGrayboxStateDocument.encode(malformed)));
        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(expected),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceGrayboxCanonicalState.capture(source)));
    }

    private static String firstDifference(Object expected, Object actual, String path) {
        if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber) {
            double expectedValue = expectedNumber.doubleValue();
            double actualValue = actualNumber.doubleValue();
            double tolerance = 32.0d * Math.max(Math.ulp(expectedValue), Math.ulp(actualValue));
            return Math.abs(expectedValue - actualValue) <= tolerance ? null
                    : path + " differs: expected=" + expected + ", actual=" + actual;
        }
        if (expected instanceof Map<?, ?> expectedMap && actual instanceof Map<?, ?> actualMap) {
            if (!expectedMap.keySet().equals(actualMap.keySet())) return path + " keys differ";
            for (Object key : expectedMap.keySet()) {
                String difference = firstDifference(expectedMap.get(key), actualMap.get(key), path + "." + key);
                if (difference != null) return difference;
            }
            return null;
        }
        if (expected instanceof List<?> expectedList && actual instanceof List<?> actualList) {
            if (expectedList.size() != actualList.size()) return path + " size differs";
            for (int index = 0; index < expectedList.size(); index++) {
                String difference = firstDifference(expectedList.get(index), actualList.get(index), path + "[" + index + "]");
                if (difference != null) return difference;
            }
            return null;
        }
        return Objects.equals(expected, actual) ? null : path + " differs: expected=" + expected + ", actual=" + actual;
    }
}
