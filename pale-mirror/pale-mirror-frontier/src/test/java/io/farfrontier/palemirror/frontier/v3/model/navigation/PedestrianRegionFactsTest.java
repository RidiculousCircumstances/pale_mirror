package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianRegionFactsTest {
    @Test void compactFootprintPreservesHolesNegativeCoordinatesAndCollidingTokens() {
        var source = new HashMap<PedestrianRegion.Tile, Object>();
        for (int x = -24; x < 24; x++) for (int z = -24; z < 24; z++)
            if (x != 0 || z != 0) source.put(new PedestrianRegion.Tile(x, z), x < 0 ? "Aa" : "BB");
        assertEquals("Aa".hashCode(), "BB".hashCode());
        var exact = Map.copyOf(source); var packed = PedestrianRegionFacts.copyOf(source); source.clear();
        assertEquals(exact, packed); assertEquals(packed, exact); assertEquals(exact.hashCode(), packed.hashCode());
        assertNull(packed.get(new PedestrianRegion.Tile(0, 0)));
        assertNull(packed.get(new PedestrianRegion.Tile(25, 0)));
        assertTrue(packed.retainedUnits() < exact.size() / 4, "repeated tokens share exact spatial masks");
        assertThrows(UnsupportedOperationException.class, () -> packed.put(new PedestrianRegion.Tile(0, 0), "forged"));
    }
    @Test void heterogeneousPagesStayExactAndOnlyTheirActualFootprintInvalidates() {
        var revision = PedestrianGeometryRevision.initial();
        var source = new HashMap<PedestrianRegion.Tile, Object>();
        for (int x = 0; x < 16; x++) source.put(new PedestrianRegion.Tile(x, 0), new Object());
        var sparse = PedestrianRegionFacts.copyOf(source); assertEquals(source, sparse);
        var footprint = new HashMap<PedestrianRegion.Tile, Object>();
        for (int x = -16; x < 16; x++) for (int z = -16; z < 16; z++)
            if (x != 0 || z != 0) footprint.put(new PedestrianRegion.Tile(x, z), revision.at(x, z));
        var packed = PedestrianRegionFacts.copyOf(footprint);
        assertTrue(packed.matches(revision));
        assertTrue(packed.matches(revision.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0)))));
        assertFalse(packed.matches(revision.changed(Set.of(new PedestrianGeometryRevision.Region(-1, -1)))));
    }
}
