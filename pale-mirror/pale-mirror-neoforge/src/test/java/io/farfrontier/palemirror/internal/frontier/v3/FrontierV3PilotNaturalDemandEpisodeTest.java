package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3PilotNaturalDemandEpisodeTest {
    private static final FrontierV3PilotNaturalDemandEpisode.ChunkKey CENTER = key("pale_mirror:frontier_graybox", 12, -7);
    private static final FrontierV3PilotNaturalDemandEpisode.ChunkKey OVERWORLD_CENTER = key("minecraft:overworld", 12, -7);
    private static final FrontierV3PilotNaturalDemandEpisode.ChunkKey DEPENDENCY = key("pale_mirror:frontier_graybox", 13, -7);
    private final Map<Integer, Object> tokens = new HashMap<>();

    @Test
    void completeHolderClosureWaitsForEveryDependencyAndBothSaveReadinessConjuncts() {
        FrontierV3PilotNaturalDemandEpisode episode = new FrontierV3PilotNaturalDemandEpisode();
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.ARMED, observe(episode, Set.of(CENTER), Map.of(
                CENTER, holder(41, 1, false), DEPENDENCY, holder(42, 1, false), OVERWORLD_CENTER, holder(40, 0, true)), false));
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.WAITING_FOR_WITHDRAWAL, observe(episode, Set.of(CENTER), Map.of(
                CENTER, holder(41, 0, true), DEPENDENCY, holder(42, 0, true), OVERWORLD_CENTER, holder(40, 0, true)), true));
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.WAITING_FOR_READINESS, observe(episode, Set.of(), Map.of(
                CENTER, holder(41, 0, true), DEPENDENCY, holder(42, 1, false), OVERWORLD_CENTER, holder(40, 0, true)), true));
        // Dependency generation completed, but its vanilla saveSync chain remains incomplete.
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.WAITING_FOR_READINESS, observe(episode, Set.of(), Map.of(
                CENTER, holder(41, 0, true), DEPENDENCY, holder(42, 0, false), OVERWORLD_CENTER, holder(40, 0, true)), true));
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.ELIGIBLE, observe(episode, Set.of(), Map.of(
                CENTER, holder(41, 0, true), DEPENDENCY, holder(42, 0, true), OVERWORLD_CENTER, holder(40, 0, true)), true));
        assertEquals(3, episode.terminalMembers(Map.of(CENTER, holder(41, 0, true), DEPENDENCY, holder(42, 0, true),
                OVERWORLD_CENTER, holder(40, 0, true))).size());
    }

    @Test
    void delayedWithdrawalAndPartialOrLateClosureNeverBecomeEligible() {
        FrontierV3PilotNaturalDemandEpisode delayed = new FrontierV3PilotNaturalDemandEpisode();
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.ARMED,
                observe(delayed, Set.of(CENTER), Map.of(CENTER, holder(43, 0, true)), false));
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.WAITING_FOR_WITHDRAWAL,
                observe(delayed, Set.of(CENTER), Map.of(CENTER, holder(43, 0, true)), true));

        FrontierV3PilotNaturalDemandEpisode empty = new FrontierV3PilotNaturalDemandEpisode();
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.INVALID, observe(empty, Set.of(), Map.of(), true));

        FrontierV3PilotNaturalDemandEpisode partial = new FrontierV3PilotNaturalDemandEpisode();
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.ARMED,
                observe(partial, Set.of(CENTER), Map.of(CENTER, holder(44, 0, true)), false));
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.INVALID,
                observe(partial, Set.of(), Map.of(CENTER, holder(44, 0, true), DEPENDENCY, holder(45, 0, true)), true));
    }

    @Test
    void currentMapAbsenceUsesRetainedObjectButNeverAbsenceOfEvidence() {
        FrontierV3PilotNaturalDemandEpisode episode = new FrontierV3PilotNaturalDemandEpisode();
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.ARMED,
                observe(episode, Set.of(CENTER), Map.of(CENTER, holder(46, 1, false)), false));
        // The actual observer supplies the retained exact holder even after ChunkMap no longer
        // lists it. A null value, unlike a retained zero-ready object, is fail-closed.
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.WAITING_FOR_READINESS,
                observe(episode, Set.of(), Map.of(CENTER, holder(46, 1, false)), true));
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.ELIGIBLE,
                observe(episode, Set.of(), Map.of(CENTER, holder(46, 0, true)), true));
        FrontierV3PilotNaturalDemandEpisode missing = new FrontierV3PilotNaturalDemandEpisode();
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.ARMED,
                observe(missing, Set.of(CENTER), Map.of(CENTER, holder(47, 0, true)), false));
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.INVALID, observe(missing, Set.of(), Map.of(), true));
    }

    @Test
    void exactObjectReferenceNotDiagnosticHashGovernsReplacementAndLateInvalidation() {
        FrontierV3PilotNaturalDemandEpisode replacement = eligible(48);
        // Same diagnostic hash but a distinct retained Java object is a real replacement.
        Object collidingDiagnosticOnly = new Object();
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.INVALID, observe(replacement, Set.of(), Map.of(
                CENTER, new FrontierV3PilotNaturalDemandEpisode.Holder(collidingDiagnosticOnly, 48, 0, true)), true));

        FrontierV3PilotNaturalDemandEpisode lateDemand = eligible(49);
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.INVALID, observe(lateDemand, Set.of(DEPENDENCY), Map.of(
                CENTER, holder(49, 0, true), DEPENDENCY, holder(50, 0, true)), true));

        FrontierV3PilotNaturalDemandEpisode lateSave = eligible(51);
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.INVALID, observe(lateSave, Set.of(), Map.of(
                CENTER, holder(51, 0, false)), true));
        assertThrows(IllegalStateException.class, () -> lateSave.terminalMembers(Map.of(CENTER, holder(51, 0, false))));
    }

    private FrontierV3PilotNaturalDemandEpisode eligible(int diagnostic) {
        FrontierV3PilotNaturalDemandEpisode episode = new FrontierV3PilotNaturalDemandEpisode();
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.ARMED,
                observe(episode, Set.of(CENTER), Map.of(CENTER, holder(diagnostic, 0, true)), false));
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.ELIGIBLE,
                observe(episode, Set.of(), Map.of(CENTER, holder(diagnostic, 0, true)), true));
        return episode;
    }

    private FrontierV3PilotNaturalDemandEpisode.Holder holder(int diagnostic, int generationRefCount, boolean readyForSaving) {
        return new FrontierV3PilotNaturalDemandEpisode.Holder(tokens.computeIfAbsent(diagnostic, ignored -> new Object()), diagnostic,
                generationRefCount, readyForSaving);
    }

    private static FrontierV3PilotNaturalDemandEpisode.Status observe(FrontierV3PilotNaturalDemandEpisode episode,
            Set<FrontierV3PilotNaturalDemandEpisode.ChunkKey> playerTickets,
            Map<FrontierV3PilotNaturalDemandEpisode.ChunkKey, FrontierV3PilotNaturalDemandEpisode.Holder> holders, boolean released) {
        return FrontierV3PilotNaturalDemandObserver.observeForTest(episode, playerTickets, holders, released);
    }

    private static FrontierV3PilotNaturalDemandEpisode.ChunkKey key(String dimension, int x, int z) {
        return new FrontierV3PilotNaturalDemandEpisode.ChunkKey(dimension,
                (long) x & 0xFFFF_FFFFL | ((long) z & 0xFFFF_FFFFL) << 32);
    }
}
