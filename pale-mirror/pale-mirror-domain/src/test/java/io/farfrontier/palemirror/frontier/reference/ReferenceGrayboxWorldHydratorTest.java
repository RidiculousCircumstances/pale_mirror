package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void restoresAnImmediateOrganCasualtyBeforeTheNextDailyHiveCleanup() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(9031746258841137206L));
        source.tick();

        ReferenceNetworkFlow flow = source.infection().networkFlows().stream().findFirst().orElseThrow();
        int destroyedOrganId = flow.organId();
        ReferenceHiveOrgan destroyed = source.infection().organs().get(destroyedOrganId);
        assertTrue(source.infection().nestEconomy().containsKey(destroyedOrganId));
        assertTrue(destroyed != null, "daily flow recipient must still be live before the physical fact");
        assertTrue(startsProjectFor(source, destroyed), "the live source organ must accept one normal pending project");

        ReferenceGrayboxObservationOutcome outcome = source.observe(new ReferenceGrayboxStructureObservation(
                ReferenceGrayboxStructureObservation.VERSION, "test:between-days-organ-casualty",
                ReferenceGrayboxProjection.from(source).stateRevision(), ReferenceGrayboxStructureObservation.Kind.ORGAN_DAMAGED,
                "organ:" + destroyedOrganId, destroyed.vitality()));
        assertTrue(outcome.applied());
        assertFalse(source.infection().organs().containsKey(destroyedOrganId));
        assertTrue(source.infection().networkFlows().stream().anyMatch(item -> item.organId() == destroyedOrganId));
        assertTrue(source.infection().nestEconomy().containsKey(destroyedOrganId));
        assertTrue(source.infection().nestProjects().stream().anyMatch(item -> item.sourceOrganId() == destroyedOrganId));

        Map<String, Object> expected = ReferenceGrayboxCanonicalState.capture(source);
        ReferenceWorld restored = ReferenceGrayboxWorldHydrator.restore(ReferenceGrayboxStateDocument.encode(expected));
        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(expected),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceGrayboxCanonicalState.capture(restored)));

        source.tick();
        restored.tick();
        assertEquals(null, firstDifference(ReferenceGrayboxCanonicalState.capture(source),
                ReferenceGrayboxCanonicalState.capture(restored), "after daily cleanup"));
    }

    private static boolean startsProjectFor(ReferenceWorld world, ReferenceHiveOrgan source) {
        for (int y = 0; y < world.infection().height(); y++) {
            for (int x = 0; x < world.infection().width(); x++) {
                int candidateX = x;
                int candidateY = y;
                boolean occupied = world.infection().organs().values().stream()
                        .anyMatch(organ -> organ.x() == candidateX && organ.y() == candidateY);
                if (!occupied && world.infection().startMorphogenesis(source, ReferenceOrganKind.SYNAPSE, candidateX, candidateY, world.day())) {
                    return true;
                }
            }
        }
        return false;
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
