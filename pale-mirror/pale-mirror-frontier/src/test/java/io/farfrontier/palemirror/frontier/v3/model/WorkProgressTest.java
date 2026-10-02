package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkProgressTest {
    @Test void interruptionRetainsLabourButTravelAndPausedTimeDoNotAccrue() {
        var initial = WorkProgress.pending(200, 10).resume(10, 1_000, 1_000);
        var paused = initial.pause(60);
        assertEquals(50_000, paused.completedMilliWork());
        assertEquals(50_000, paused.completedAt(5_000));
        var resumed = paused.resume(5_000, 2_000, 6_000);
        assertEquals(5_075, resumed.activeUntilTick());
        assertFalse(resumed.pause(5_074).complete());
        assertTrue(resumed.pause(5_075).complete());
    }
    @Test void knownBoundaryLimitsAccrualAndRejectsBackwardTime() {
        var work = WorkProgress.pending(200, 10).resume(10, 1_000, 30);
        assertEquals(20_000, work.completedAt(10_000));
        assertThrows(IllegalArgumentException.class, () -> work.pause(9));
        assertThrows(IllegalArgumentException.class, () -> work.resume(30, 1_000, 40));
    }
}
