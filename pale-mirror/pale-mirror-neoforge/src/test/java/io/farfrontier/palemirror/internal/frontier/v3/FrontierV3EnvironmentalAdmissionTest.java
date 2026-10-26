package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3EnvironmentalAdmissionTest {
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
