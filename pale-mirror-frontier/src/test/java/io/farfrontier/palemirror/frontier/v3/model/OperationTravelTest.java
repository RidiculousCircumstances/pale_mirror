package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationTravelTest {
    private static final SubjectId HAULER = new SubjectId("resident:1-1");

    @Test
    void retainsOneAdjacentCursorAndRejectsTeleportOrOversizedColdAdvance() {
        OperationTravel travel = new OperationTravel(List.of(new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0),
                        new BlockPosition(2, 64, 0)), 0, Map.of(HAULER, new BlockPosition(0, 64, 1)), new BlockPosition(0, 64, 0));

        assertEquals(1, travel.nextHotCursor());
        OperationTravel advanced = travel.advance(1, Map.of(HAULER, new BlockPosition(1, 64, 1)), new BlockPosition(1, 64, 0));
        assertEquals(new BlockPosition(1, 64, 0), advanced.currentPosition());
        org.junit.jupiter.api.Assertions.assertTrue(advanced.isExactHotAdvanceFrom(travel));
        org.junit.jupiter.api.Assertions.assertFalse(travel.advance(1, Map.of(HAULER, new BlockPosition(7, 64, 1)), new BlockPosition(1, 64, 0))
                .isExactHotAdvanceFrom(travel), "a HOT observation may not move one participant independently of its route step");
        assertThrows(IllegalArgumentException.class, () -> travel.advance(0, Map.of(HAULER, new BlockPosition(0, 64, 1)), new BlockPosition(0, 64, 0)));
        assertThrows(IllegalArgumentException.class, () -> new OperationTravel(List.of(new BlockPosition(0, 64, 0), new BlockPosition(2, 64, 0)),
                0, Map.of(HAULER, new BlockPosition(0, 64, 1)), new BlockPosition(0, 64, 0)));
    }
}
