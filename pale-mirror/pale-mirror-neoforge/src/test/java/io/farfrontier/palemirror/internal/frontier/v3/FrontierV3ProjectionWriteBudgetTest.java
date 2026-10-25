package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ProjectionWriteBudgetTest {
    @Test void durablePreparationCannotConsumeTheEntireEffectWindowButEffectsRemainBounded() {
        var clock = new AtomicLong();
        var budget = new FrontierV3ProjectionWriteBudget(7, 256, clock::get);
        clock.set(30_000_000); // Waiting for the prepared journal group, no block changed yet.
        assertTrue(budget.available());
        assertTrue(budget.take());
        clock.addAndGet(4_000_000);
        assertTrue(budget.take());
        clock.addAndGet(1_000_000);
        assertFalse(budget.take(), "a second invocation cannot restart this turn's effect window");
        assertEquals(254, budget.remaining());
    }
    @Test void fastWritesStillRespectTheCountCeiling() {
        var budget = new FrontierV3ProjectionWriteBudget(7, 2, () -> 0L);
        assertTrue(budget.take()); assertTrue(budget.take()); assertFalse(budget.take());
    }
    @Test void pendingDurabilityDoesNotSpendAnAttemptOrTheSharedCapacity() {
        var budget = new FrontierV3ProjectionWriteBudget(7, 1, () -> 0L);
        var waiting = FrontierV3NativeEffect.Admission.waiting("exact-durable-predecessor");
        assertSame(waiting, budget.admit(waiting));
        assertEquals(1, budget.remaining());
        assertTrue(budget.admit(FrontierV3NativeEffect.Admission.READY).ready());
        assertFalse(budget.admit(FrontierV3NativeEffect.Admission.READY).ready());
        assertEquals("projection-turn-capacity", budget.admit(FrontierV3NativeEffect.Admission.READY).waitingFor().orElseThrow());
    }
}
