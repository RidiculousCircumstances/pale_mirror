package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3IndexedWorkWindowTest {
    @Test void presentationDemandBypassesGlobalSweepWithoutStarvingOrdinaryOrOtherDemandedOwners() {
        var window = new FrontierV3IndexedWorkWindow<Integer, String>();
        var owners = new TreeMap<Integer, String>();
        for (int i = 0; i < 100; i++) owners.put(i, "owner-" + i);
        var seen = new HashSet<String>();
        for (int turn = 0; turn < 200; turn++) {
            var preferred = window.nextPreferred(owners, List.of(97, 98, 99), 2);
            if (turn % 2 == 0) {
                assertEquals("owner-" + (97 + (turn / 2) % 3), preferred.getFirst());
            } else {
                assertTrue(preferred.isEmpty());
                seen.add(window.next(owners, 8).getFirst());
            }
        }
        assertEquals(Set.copyOf(owners.values()), seen);
        assertEquals(List.of("replaced"), window.nextPreferred(Map.of(99, "replaced"), List.of(98, 99), 8));
        assertTrue(window.nextPreferred(Map.of(), List.of(99), 8).isEmpty());
        assertTrue(window.nextPreferred(Map.of(), List.of(99), 8).isEmpty(), "removed demand grants no retained owner");
    }
    @Test void boundedDiscoveryRotatesPastWaitingOwnersAndReadsCurrentReplacements() {
        var window = new FrontierV3IndexedWorkWindow<Integer, String>();
        var owners = Map.of(1, "waiting", 2, "ready", 3, "recovery");
        assertEquals(List.of("waiting", "ready"), window.next(owners, 2));
        assertEquals(List.of("updated", "recovery"), window.next(Map.of(1, "waiting", 2, "updated", 3, "recovery"), 2));
        assertEquals(List.of("recovery", "waiting"), window.next(owners, 2));
        assertEquals(List.of("new"), window.next(Map.of(4, "new"), 2));
        assertEquals(List.of(), window.next(Map.of(), 2));
    }
}
