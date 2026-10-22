package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3PreparationWindowTest {
    @Test void blockedNearestCannotMonopolizeDiscoveryAndRetiredOwnersDisappear() {
        var window = new FrontierV3PreparationWindow<Integer>();
        var candidates = java.util.stream.IntStream.range(0, 20).boxed().toList();
        var visited = new HashSet<Integer>();
        for (int turn = 0; turn < 20; turn++) {
            var next = window.next(candidates, id -> id == 19 ? 0 : 100, 2);
            assertEquals(19, next.getFirst());
            visited.addAll(next);
        }
        assertEquals(20, visited.size());
        assertEquals(List.of(5), window.next(List.of(5), id -> 0, 8));
        assertTrue(window.next(List.of(), id -> 0, 8).isEmpty());
    }
}
