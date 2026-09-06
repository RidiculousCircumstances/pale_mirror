package io.farfrontier.palemirror.internal.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SourceGrayboxConflictPresentationTest {
    @Test
    void retainsExactBackgroundConflictsWithoutTurningCellsIntoFloatingHudBoards() {
        assertFalse(SourceGrayboxConflictPresentation.needsLocalBoard("CELL"));
        assertFalse(SourceGrayboxConflictPresentation.needsLocalBoard("INFECTION_TISSUE"));
        assertFalse(SourceGrayboxConflictPresentation.needsLocalBoard("SECTOR_METRIC"));
        assertTrue(SourceGrayboxConflictPresentation.needsLocalBoard("FACILITY"));
        assertTrue(SourceGrayboxConflictPresentation.needsLocalBoard("HIVE_ORGAN"));
        assertTrue(SourceGrayboxConflictPresentation.needsLocalBoard("INTERACTION"));
        assertTrue(SourceGrayboxConflictPresentation.needsLocalBoard("SOURCE_ACTOR_OBSTRUCTION"));
    }

    @Test
    void oneObjectBoardPrefersAReplayedPlayerActionOverAnOlderPassiveConflict() {
        SourceGrayboxPresentationLedger.Claim olderObstruction = claim("route:4", "route:4:cell:1", false, false);
        SourceGrayboxPresentationLedger.Claim replayedSlot = claim("route:4", "route:4:slot:2", true, true);

        assertSame(replayedSlot, SourceGrayboxConflictPresentation.preferredBoard(olderObstruction, replayedSlot));
        assertSame(replayedSlot, SourceGrayboxConflictPresentation.preferredBoard(replayedSlot, olderObstruction));
    }

    private static SourceGrayboxPresentationLedger.Claim claim(String subjectId, String id, boolean consumed, boolean installed) {
        String interactionKind = consumed ? "route_damaged" : "";
        double interactionWeight = consumed ? 1.0d : 0.0d;
        return new SourceGrayboxPresentationLedger.Claim(id, subjectId, "INTERACTION",
                "0".repeat(64), 0, 64, 0, 1, 1, 1, true, interactionKind, interactionWeight, consumed, installed);
    }
}
