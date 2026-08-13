package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MineUndergroundLayoutTest {
    @Test void compactGrammarHasOneTrunkAndTwoDistinctBranches() {
        var corridors = MineUndergroundLayout.corridors();

        assertEquals(3, corridors.size());
        assertEquals(MineUndergroundLayout.PORTAL, corridors.getFirst().getFirst());
        assertEquals(MineUndergroundLayout.JUNCTION, corridors.getFirst().getLast());
        assertEquals(MineUndergroundLayout.JUNCTION, corridors.get(1).getFirst());
        assertEquals(MineUndergroundLayout.JUNCTION, corridors.get(2).getFirst());
        assertEquals(MineUndergroundLayout.GALLERY, corridors.get(1).getLast());
        assertEquals(MineUndergroundLayout.CONTROLLER, corridors.get(2).getLast());
        assertTrue(MineUndergroundLayout.GALLERY.right() > MineUndergroundLayout.JUNCTION.right());
        assertTrue(MineUndergroundLayout.CONTROLLER.right() < MineUndergroundLayout.JUNCTION.right());
        assertTrue(corridors.stream().flatMap(java.util.Collection::stream)
                .allMatch(node -> node.inward() <= 34 && node.up() >= -12));
    }
}
