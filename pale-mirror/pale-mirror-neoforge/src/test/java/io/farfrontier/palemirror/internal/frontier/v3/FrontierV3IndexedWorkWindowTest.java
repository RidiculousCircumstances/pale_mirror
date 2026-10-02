package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3IndexedWorkWindowTest {
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
