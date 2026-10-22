package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3PreparationAdmissionTest {
    @Test void disabledCandidateCannotStrandEarlierPreparation() {
        var admission = new FrontierV3PreparationAdmission(List.of("live", "disabled"));
        assertTrue(admission.begin(1, () -> 0).admit("live"));
        var resumed = admission.begin(1, () -> 0);
        assertFalse(resumed.admit("live"));
        resumed.completeCandidate("disabled");
        assertTrue(admission.begin(1, () -> 0).admit("live"));
    }
    @Test void durabilityYieldsCannotPhaseLockPreparationOutOfTheExecutionPlan() {
        var owners = List.of("structures", "fields", "worksites");
        var admission = new FrontierV3PreparationAdmission(owners);
        var execution = new FrontierV3CooperativeStageCursor();
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        var admitted = new java.util.ArrayList<String>();
        for (int tick = 0; tick < 9; tick++) {
            ready.set(true);
            var turn = admission.begin(10, () -> 0);
            execution.run(4, ready::get, stage -> {
                if (stage > 0) {
                    var owner = owners.get(stage - 1);
                    if (turn.admit(owner)) admitted.add(owner);
                    turn.completeCandidate(owner);
                }
                // Observation and field submission each yield on durable publication.
                if (stage == 0 || stage == 2) ready.set(false);
            });
        }
        assertEquals(List.of("structures", "fields", "worksites", "structures", "fields", "worksites",
                "structures", "fields", "worksites"), admitted);
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
