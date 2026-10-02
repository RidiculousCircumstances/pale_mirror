package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AreaWorkSelectionTest {
    @Test void workersSpreadAcrossIrregularAreaThenFinishEachOthersRemainingCells() {
        var targets = java.util.List.of(SurfaceAnchor.at(0, 0, 0), SurfaceAnchor.at(1, 0, 0),
                SurfaceAnchor.at(2, 0, 1), SurfaceAnchor.at(8, 1, 3), SurfaceAnchor.at(9, 1, 3));
        var peers = java.util.List.of(targets.getFirst());
        var policy = AreaWorkSelection.SpatialPolicy.SPREAD_STARTS;
        assertEquals(4, AreaWorkSelection.spatiallySeparated(targets.size(), i -> i != 0,
                targets.getFirst(), targets::get, peers, policy).orElseThrow());
        assertEquals(3, AreaWorkSelection.spatiallySeparated(targets.size(), i -> i != 0 && i != 4,
                targets.get(4), targets::get, peers, AreaWorkSelection.SpatialPolicy.LOCAL_CONTINUATION).orElseThrow());
        // Proximity never reserves territory: the only remaining available target still gets work.
        assertEquals(1, AreaWorkSelection.spatiallySeparated(targets.size(), i -> i == 1,
                targets.get(4), targets::get, peers, AreaWorkSelection.SpatialPolicy.LOCAL_CONTINUATION).orElseThrow());
        assertTrue(AreaWorkSelection.spatiallySeparated(targets.size(), i -> false,
                targets.get(4), targets::get, peers, policy).isEmpty());
    }

    @Test void unreachableTargetStaysPendingWhileAnotherTargetIsChosen() {
        assertEquals(3, AreaWorkSelection.reachableAfter(5, 1, index -> index == 3).orElseThrow());
        assertTrue(AreaWorkSelection.reachableAfter(5, 1, index -> index == 1).isEmpty());
        assertEquals(1, AreaWorkSelection.nextAfter(5, 3, index -> index == 1,
                index -> index == 1 || index == 3).orElseThrow());
    }
}
