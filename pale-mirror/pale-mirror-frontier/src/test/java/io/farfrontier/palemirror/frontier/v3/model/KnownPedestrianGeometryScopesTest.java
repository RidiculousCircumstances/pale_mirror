package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnownPedestrianGeometryScopesTest {
    @Test void resolvedCollisionFactsInvalidateTheirRegionEvenWhenOccupiedUnionIsUnchanged() {
        var bootstrap = new Object(); var position = new BlockPosition(2, 64, 2);
        KnownPedestrianGeometryScopes.refresh(bootstrap, Set.of(position), Map.of(), Set.of(), Set.of(position), Set.of());
        var before = KnownPedestrianGeometryScopes.groundRevision();
        KnownPedestrianGeometryScopes.refresh(bootstrap, Set.of(position), Map.of(), Set.of(), Set.of(), Set.of());
        var after = KnownPedestrianGeometryScopes.groundRevision();
        assertEquals(Set.of(PedestrianGeometryRevision.Region.at(position)), after.changesSince(before));
    }
    @Test void overlappingProducerWithdrawalPreservesOtherOwnersAndImmutableRegionalSnapshots() {
        var wall = new BlockPosition(2, 64, 2); var remote = new BlockPosition(160, 64, 160);
        var parts = new RegionalBlockContributions<String>();
        parts.replace("terrain", RegionalBlockSet.copyOf(Set.of(wall, remote)));
        var before = parts.replace("physical", RegionalBlockSet.copyOf(Set.of(wall)));
        var after = parts.replace("terrain", RegionalBlockSet.copyOf(Set.of(remote)));
        assertSame(before, after, "another producer still owns the same obstacle");
        after = parts.replace("physical", RegionalBlockSet.copyOf(Set.of()));
        assertFalse(after.contains(wall)); assertTrue(before.contains(wall));
        assertSame(before.token(PedestrianGeometryRevision.Region.at(remote)), after.token(PedestrianGeometryRevision.Region.at(remote)));
        assertEquals(Set.of(PedestrianGeometryRevision.Region.at(wall)), after.changesFrom(before));
        assertThrows(UnsupportedOperationException.class, () -> before.remove(wall));
        var opened = before.overlay(Set.of(wall), Set.of());
        assertFalse(opened.contains(wall)); assertTrue(before.contains(wall));
    }
    @Test void supportContributionsRetainMultipleLevelsAndWithdrawOnlyTheirOwnLayer() {
        var column = new TerrainColumn(-1, -1);
        var lower = SurfaceAnchor.at(-1, 63, -1); var upper = SurfaceAnchor.at(-1, 68, -1);
        var parts = new RegionalSurfaceContributions<String>();
        parts.replace("ground", RegionalSurfaceMap.copyOf(Map.of(column, List.of(lower))));
        var before = parts.replace("bridge", RegionalSurfaceMap.copyOf(Map.of(column, List.of(upper))));
        assertEquals(List.of(lower, upper), before.get(column));
        var after = parts.replace("bridge", RegionalSurfaceMap.copyOf(Map.of()));
        assertEquals(List.of(lower), after.get(column)); assertEquals(List.of(lower, upper), before.get(column));
        assertEquals(Set.of(new PedestrianGeometryRevision.Region(-1, -1)), after.changesFrom(before));
    }
    @Test void evictedUnchangedViewRetainsKnowledgeButActualLocalChangesInvalidateIt() {
        var bootstrap = new Object();
        var wall = new BlockPosition(2, 64, 2);
        var column = new TerrainColumn(20, 2);
        var supports = Map.of(column, List.of(SurfaceAnchor.at(20, 63, 2)));
        KnownPedestrianGeometryScopes.refresh(bootstrap, Set.of(wall), supports);
        var before = KnownPedestrianGeometryScopes.revision("test:retained", Set.of(wall), supports);
        var otherTask = KnownPedestrianGeometryScopes.revision("test:other-task", Set.of(), supports);
        assertNotEquals(before.scope(), otherTask.scope());
        assertEquals(before.geometryScope(), otherTask.geometryScope());
        assertNotEquals(before.at(0, 0), otherTask.at(0, 0), "passage restrictions stay in the local geometry proof");
        assertEquals(before.at(1, 0), otherTask.at(1, 0), "identical distant terrain can be shared across task policies");
        for (int index = 0; index < 300; index++)
            KnownPedestrianGeometryScopes.revision("test:eviction-" + index, Set.of(), Map.of());
        var reconstructed = KnownPedestrianGeometryScopes.revision("test:retained", Set.of(wall), supports);
        assertEquals(before.scope(), reconstructed.scope());
        assertSame(before.baseline(), reconstructed.baseline());
        assertTrue(reconstructed.changesSince(before).isEmpty());
        assertEquals(before.at(0, 0), reconstructed.at(0, 0));
        assertEquals(before.at(1, 0), reconstructed.at(1, 0));

        var changedSupports = Map.of(column, List.of(SurfaceAnchor.at(20, 62, 2)));
        KnownPedestrianGeometryScopes.refresh(bootstrap, Set.of(wall), changedSupports);
        var changed = KnownPedestrianGeometryScopes.revision("test:retained", Set.of(wall), changedSupports);
        assertEquals(Set.of(new PedestrianGeometryRevision.Region(1, 0)), changed.changesSince(reconstructed));
        assertEquals(before.at(0, 0), changed.at(0, 0));
        assertNotEquals(before.at(1, 0), changed.at(1, 0));
        var removedWall = KnownPedestrianGeometryScopes.revision("test:retained", Set.of(), changedSupports);
        assertEquals(Set.of(new PedestrianGeometryRevision.Region(0, 0)), removedWall.changesSince(changed));
        KnownPedestrianGeometryScopes.refresh(new Object(), Set.of(), Map.of());
        assertNotEquals(before.scope(), KnownPedestrianGeometryScopes.revision("test:retained", Set.of(), Map.of()).scope());
        assertNotEquals(before.geometryScope(), KnownPedestrianGeometryScopes.revision("test:retained", Set.of(), Map.of()).geometryScope());
    }
}
