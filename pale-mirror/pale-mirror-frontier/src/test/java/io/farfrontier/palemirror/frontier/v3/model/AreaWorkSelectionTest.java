package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AreaWorkSelectionTest {
    @Test void unreachableTargetStaysPendingWhileAnotherTargetIsChosen() {
        assertEquals(3, AreaWorkSelection.reachableAfter(5, 1, index -> index == 3).orElseThrow());
        assertTrue(AreaWorkSelection.reachableAfter(5, 1, index -> index == 1).isEmpty());
        assertEquals(1, AreaWorkSelection.nextAfter(5, 3, index -> index == 1,
                index -> index == 1 || index == 3).orElseThrow());
    }
}
