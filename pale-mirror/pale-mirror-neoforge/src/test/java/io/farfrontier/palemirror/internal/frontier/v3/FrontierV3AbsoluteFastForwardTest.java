package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused admission regression: client delivery cannot become an implicit relative advance. */
class FrontierV3AbsoluteFastForwardTest {
    @Test
    void admitsOnlyOneBoundedFutureTargetFromTheServerCheckpoint() {
        assertEquals(23, FrontierV3ServerLifecycle.absoluteFastForwardDelta(24_600L, 24_623L).orElseThrow());
        assertTrue(FrontierV3ServerLifecycle.absoluteFastForwardDelta(24_623L, 24_623L).isEmpty(), "crossed target");
        assertTrue(FrontierV3ServerLifecycle.absoluteFastForwardDelta(24_624L, 24_623L).isEmpty(), "past target");
        assertTrue(FrontierV3ServerLifecycle.absoluteFastForwardDelta(24_600L, 48_601L).isEmpty(), "unbounded target");
    }
}
