package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Focused admission regression: client delivery cannot become an implicit relative advance. */
class FrontierV3AbsoluteFastForwardTest {
    @Test
    void queuedColdAdvanceHasASmallLiveTickSlice() {
        assertEquals(10, FrontierV3ServerLifecycle.fastForwardSliceTicks());
        var ruleset = io.farfrontier.palemirror.frontier.v3.model.FrontierRulesets.installed("frontier-v3-quarry-graybox-r2");
        assertEquals(ruleset.execution().budget(), FrontierV3RuntimeBudgets.fastForwardTick(ruleset));
        assertEquals(FrontierV3RuntimeBudgets.ordinaryTick(ruleset), FrontierV3RuntimeBudgets.fastForwardTick(ruleset),
                "operator advancement must not silently change the world's admission policy");
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
    void activeRelativeAdvanceDoesNotRunOneExtraOrdinaryPhysicalTurnAlongsideItsExactCanonicalSlice() {
        assertFalse(FrontierV3ServerLifecycle.runsObservedPhysicalTurnWhileCanonicalProgressIsHeld(false, false, true));
        assertTrue(FrontierV3ServerLifecycle.advancesOnlyQueuedCanonicalTime(true));
        assertFalse(FrontierV3ServerLifecycle.advancesOnlyQueuedCanonicalTime(false));
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

    @Test void zeroTickInitialHoldCanBeReleasedWithoutAuthorizingAZeroTickAdvance() {
        var released = FrontierV3ServerLifecycle.nextFastForwardTargetOutcome(null, 0L, 0L, 0L, "RELEASED", null);
        assertEquals("RELEASED", released.status());
        assertDoesNotThrow(() -> new FrontierV3ServerLifecycle.FastForwardRequestOutcome(1, "ABSOLUTE", 0, 0L, 0L, 0L, "RELEASED", null));
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3ServerLifecycle.FastForwardTargetOutcome(1, 0, 0L, 0L, "HELD", null));
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3ServerLifecycle.FastForwardRequestOutcome(1, "ABSOLUTE", 1, 0L, 0L, null, "QUEUED", null));
        assertTrue(FrontierV3ServerLifecycle.absoluteFastForwardDelta(0, 0).isEmpty());
    }

    @Test
    void pendingPhysicalProjectionTerminalizesTheQueuedRelativeReceiptWithoutThrowingOrRetargetingIt() {
        var queued = new FrontierV3ServerLifecycle.FastForwardRequestOutcome(1L, "RELATIVE", 10_000, 15_310L,
                5_310L, null, "QUEUED", null);

        var terminal = FrontierV3ServerLifecycle.terminalizeQueuedFastForwardRequest(List.of(queued), "RELATIVE", 5_342L,
                "REJECTED", "physical work became pending during the relative interval: resource-site-projection:site:7-wheat-field");

        assertEquals(1, terminal.size());
        var receipt = terminal.getFirst();
        assertEquals(1L, receipt.requestId(), "the terminal receipt belongs to the queued operator request");
        assertEquals(15_310L, receipt.targetInstant(), "mid-flight rejection retains the original computed relative target");
        assertEquals(5_342L, receipt.reachedCheckpointInstant(), "the stop boundary is recoverable rather than inferred from queueing");
        assertEquals("REJECTED", receipt.status());
        assertTrue(receipt.reason().contains("resource-site-projection:site:7-wheat-field"));
    }

    @Test
    void physicalAdmissionRejectionStillHasOneCompleteRelativeOperatorReceipt() {
        var rejected = new FrontierV3ServerLifecycle.FastForwardRequestOutcome(2L, "RELATIVE", 10_000, 15_310L,
                5_310L, 5_310L, "REJECTED", "physical work is pending at admission: resource-site-projection:site:7-wheat-field");

        assertEquals(2L, rejected.requestId());
        assertEquals(15_310L, rejected.targetInstant());
        assertEquals(5_310L, rejected.admittedCheckpointInstant());
        assertEquals(5_310L, rejected.reachedCheckpointInstant());
        assertEquals("REJECTED", rejected.status());
        assertTrue(rejected.reason().contains("resource-site-projection:site:7-wheat-field"));
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
