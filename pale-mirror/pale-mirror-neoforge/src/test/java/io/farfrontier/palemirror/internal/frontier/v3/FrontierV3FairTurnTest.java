package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3FairTurnTest {
    @Test
    void waitingFirstItemCannotMonopolizeTurnsAndInventoryOrderDoesNotMatter() {
        var turns = new FrontierV3FairTurn<String>();
        var visits = new ArrayList<String>();
        for (int tick = 0; tick < 6; tick++) {
            turns.next(List.of("c", "a", "b"), Function.identity()).ifPresent(visits::add);
        }
        assertEquals(List.of("a", "b", "c", "a", "b", "c"), visits);
    }

    @Test
    void removedCursorAndNewEarlierCandidatesDoNotStallOrResetService() {
        var turns = new FrontierV3FairTurn<String>();
        assertEquals("b", turns.next(List.of("b", "d"), Function.identity()).orElseThrow());
        assertEquals("c", turns.next(List.of("a", "c", "d"), Function.identity()).orElseThrow());
        assertEquals("d", turns.next(List.of("a", "d"), Function.identity()).orElseThrow());
        assertTrue(turns.next(List.<String>of(), Function.identity()).isEmpty());
        assertEquals("a", turns.next(List.of("a", "d"), Function.identity()).orElseThrow());
    }

    @Test
    void admissionAndExistingWorkAlternateEvenWhenEveryAttemptWaits() {
        var turns = new FrontierV3FairTurn<String>();
        var admissions = new FrontierV3FairTurn<String>();
        var visits = new ArrayList<String>();
        for (int tick = 0; tick < 8; tick++) {
            int before = visits.size();
            assertTrue(turns.run(List.of("active:a", "active:b"), Function.identity(), visits::add, () -> {
                visits.add(admissions.next(List.of("candidate:a", "candidate:b"), Function.identity()).orElseThrow());
                return true;
            }));
            assertEquals(before + 1, visits.size(), "one bounded attempt, never all active scenes per tick");
        }
        assertEquals(List.of("active:a", "candidate:a", "active:b", "candidate:b",
                "active:a", "candidate:a", "active:b", "candidate:b"), visits);
    }

    @Test
    void noAdmissionDoesNotWasteAlternateActiveTurnsAndEmptyActiveStillAdmits() {
        var turns = new FrontierV3FairTurn<String>();
        var visits = new ArrayList<String>();
        for (int tick = 0; tick < 4; tick++) {
            assertTrue(turns.run(List.of("a", "b"), Function.identity(), visits::add, () -> false));
        }
        assertEquals(List.of("a", "b", "a", "b"), visits);
        assertFalse(turns.run(List.<String>of(), Function.identity(), visits::add, () -> false));
        assertTrue(turns.run(List.<String>of(), Function.identity(), visits::add, () -> true));
    }
}
