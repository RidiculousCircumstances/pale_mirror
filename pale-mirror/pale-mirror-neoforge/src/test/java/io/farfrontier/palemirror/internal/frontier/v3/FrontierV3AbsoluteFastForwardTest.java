package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused admission regression: client delivery cannot become an implicit relative advance. */
class FrontierV3AbsoluteFastForwardTest {
    @Test
    void queuedColdAdvanceHasASmallLiveTickSlice() {
        assertEquals(8, FrontierV3ServerLifecycle.fastForwardSliceTicks());
        assertEquals(4, FrontierV3RuntimeBudgets.fastForwardTick().maxActions(),
                "one coincident due wave may not monopolize the server thread");
        assertTrue(FrontierV3ServerLifecycle.fastForwardSliceTimeRemaining(19_999_999L));
        assertTrue(!FrontierV3ServerLifecycle.fastForwardSliceTimeRemaining(20_000_000L));
    }

    @Test
    void heldAbsoluteTargetStillRunsTheOneOrdinaryPhysicalCustodyTurn() {
        assertTrue(FrontierV3ServerLifecycle.runsObservedPhysicalTurnWhileCanonicalProgressIsHeld(false, true, false));
        assertTrue(FrontierV3ServerLifecycle.runsObservedPhysicalTurnWhileCanonicalProgressIsHeld(true, false, false));
        assertTrue(!FrontierV3ServerLifecycle.runsObservedPhysicalTurnWhileCanonicalProgressIsHeld(false, true, true));
        assertTrue(!FrontierV3ServerLifecycle.runsObservedPhysicalTurnWhileCanonicalProgressIsHeld(false, false, false));
    }

    @Test
    void admitsOnlyOneBoundedFutureTargetFromTheServerCheckpoint() {
        assertEquals(23, FrontierV3ServerLifecycle.absoluteFastForwardDelta(24_600L, 24_623L).orElseThrow());
        assertTrue(FrontierV3ServerLifecycle.absoluteFastForwardDelta(24_623L, 24_623L).isEmpty(), "crossed target");
        assertTrue(FrontierV3ServerLifecycle.absoluteFastForwardDelta(24_624L, 24_623L).isEmpty(), "past target");
        assertTrue(FrontierV3ServerLifecycle.absoluteFastForwardDelta(24_600L, 48_601L).isEmpty(), "unbounded target");
    }

    @Test
    void givesEachAbsoluteRequestAnAttributableOutcomeInsteadOfClearedRequestFields() {
        var admitted = FrontierV3ServerLifecycle.nextFastForwardTargetOutcome(null, 14_000L, 9_000L, null, "ADVANCING", null);
        var held = new FrontierV3ServerLifecycle.FastForwardTargetOutcome(admitted.requestId(), 14_000L, 9_000L, 14_000L, "HELD", null);
        var released = FrontierV3ServerLifecycle.nextFastForwardTargetOutcome(held, 14_000L, 9_000L, 14_000L, "RELEASED", null);
        var rejected = FrontierV3ServerLifecycle.nextFastForwardTargetOutcome(released, 14_100L, null, null, "REJECTED", "physical work is pending at admission");

        assertEquals(1L, admitted.requestId());
        assertEquals(admitted.requestId(), held.requestId(), "holding is the confirmed terminal result of the same request");
        assertEquals(2L, released.requestId(), "release acknowledgement is a distinct operation");
        assertEquals(3L, rejected.requestId(), "a later rejection cannot be mistaken for the earlier held target");
        assertEquals(9_000L, admitted.admittedCheckpointInstant());
        assertEquals(14_000L, held.reachedCheckpointInstant());
        assertEquals(14_100L, rejected.targetInstant());
        assertEquals("REJECTED", rejected.status());
    }

    @Test
    void retainsBoundedServerThreadAttributionAcrossColdSlices() {
        var first = FrontierV3ServerLifecycle.nextFastForwardSliceTelemetry(null, 17L, 5L, 9L, 3);
        var next = FrontierV3ServerLifecycle.nextFastForwardSliceTelemetry(first, 23L, 7L, 11L, 4);

        assertEquals(2L, next.samples());
        assertEquals(40L, next.totalNanos());
        assertEquals(23L, next.maxNanos());
        assertEquals(12L, next.safetyNanos());
        assertEquals(7L, next.maxSafetyNanos());
        assertEquals(20L, next.advanceNanos());
        assertEquals(11L, next.maxAdvanceNanos());
        assertEquals(7L, next.advancedTicks());
    }
}
