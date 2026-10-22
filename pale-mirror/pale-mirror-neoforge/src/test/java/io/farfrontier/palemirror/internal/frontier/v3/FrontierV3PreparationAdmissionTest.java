package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3PreparationAdmissionTest {
    @Test void disabledCandidateCannotStrandEarlierPreparation() {
        var admission = new FrontierV3PreparationAdmission(List.of("live", "disabled"));
        assertTrue(admission.begin(1, () -> 0).admit("live"));
        assertFalse(admission.begin(1, () -> 0).admit("live"));
        assertTrue(admission.begin(1, () -> 0).admit("live"));
    }
    @Test void overrunRotatesAdmissionWithoutReorderingOwnersOrStarvingTheLastOne() {
        var clock = new AtomicLong();
        var admission = new FrontierV3PreparationAdmission(List.of("structures", "fields", "worksites"));
        var first = admission.begin(10, clock::get);
        assertTrue(first.admit("structures")); clock.set(20);
        assertFalse(first.admit("fields")); assertFalse(first.admit("worksites"));
        var second = admission.begin(10, clock::get);
        assertFalse(second.admit("structures")); assertTrue(second.admit("fields")); clock.set(40);
        assertFalse(second.admit("worksites"));
        var third = admission.begin(10, clock::get);
        assertFalse(third.admit("structures")); assertFalse(third.admit("fields"));
        assertTrue(third.admit("worksites"));
        var fourth = admission.begin(10, clock::get);
        assertTrue(fourth.admit("structures")); assertTrue(fourth.admit("fields")); assertTrue(fourth.admit("worksites"));
        assertThrows(IllegalArgumentException.class, () -> fourth.admit("undeclared"));
    }
}
