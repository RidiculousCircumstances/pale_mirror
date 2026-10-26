package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3EnvironmentalAdmissionTest {
    @Test void independentNeighbourWritesYieldButAnAdmittedMutationKeepsItsWholeEffect() {
        var clock = new AtomicLong(); var admission = new FrontierV3EnvironmentalAdmission(clock::get);
        admission.begin(10);
        assertTrue(admission.run(() -> {
            assertTrue(admission.mutate(() -> {
                clock.addAndGet(11);
                assertTrue(admission.mutate(() -> true), "nested physics belongs to the admitted mutation");
                return true;
            }));
            assertFalse(admission.mutate(() -> { fail("a later independent neighbour must resample"); return true; }));
        }));
        assertTrue(admission.mutate(() -> true), "an explicit player write cannot inherit the exhausted random budget");
        admission.begin(10);
        assertTrue(admission.run(() -> {
            assertThrows(IllegalStateException.class, () -> admission.mutate(() -> { throw new IllegalStateException("native failure"); }));
            clock.addAndGet(11);
            assertFalse(admission.mutate(() -> true), "failed mutations must not leave an active nesting bypass");
        }));
    }
    @Test void manyCallbacksShareOneAllowanceAndTheNextTickResamplesWithoutReplaying() {
        var clock = new AtomicLong(); var admission = new FrontierV3EnvironmentalAdmission(clock::get);
        admission.begin(10);
        assertTrue(admission.run(() -> clock.addAndGet(6)));
        assertTrue(admission.run(() -> clock.addAndGet(6)), "an admitted native sample finishes atomically");
        for (int i = 0; i < 100; i++) assertFalse(admission.run(() -> fail("exhausted native samples must not mutate")));
        admission.begin(10);
        assertTrue(admission.run(() -> assertEquals(12, clock.get(), "skipped samples were not retained as effects")));
        assertThrows(IllegalArgumentException.class, () -> admission.begin(0));
    }
}
