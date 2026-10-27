package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnownPedestrianGeometryScopesTest {
    @Test void interiorShapeChangeDoesNotInvalidateUnchangedRegionBoundary() {
        var versions = new KnownPedestrianGroundVersions(); var bootstrap = new Object();
        var interior = new BlockPosition(8, 64, 8);
        var boundary = new BlockPosition(15, 64, 15);
        var before = NavigationGeometryState.empty().observe(0, Map.of(interior, NavigationObservation.known(NavigationCell.SOLID),
                boundary, NavigationObservation.known(NavigationCell.SOLID)));
        var revision = shapes(versions, bootstrap, before);
        var after = before.observe(1, Map.of(interior, NavigationObservation.known(NavigationCell.AIR)));
        var next = shapes(versions, bootstrap, after);
        assertEquals(Set.of(new PedestrianGeometryRevision.Region(0, 0)), next.changesSince(revision));
        var borderChange = after.observe(2, Map.of(boundary, NavigationObservation.unavailable(NavigationObservation.Kind.WRITE_PENDING)));
        assertTrue(shapes(versions, bootstrap, borderChange).changesSince(next)
                .contains(new PedestrianGeometryRevision.Region(1, 1)), "real border changes retain collision halo invalidation");
        assertEquals(revision, shapes(versions, bootstrap, before), "out-of-order reads retain original physical facts");
    }
    private static PedestrianGeometryRevision shapes(KnownPedestrianGroundVersions versions, Object bootstrap, NavigationGeometryState shapes) {
        return versions.capture(bootstrap, Set.of(), Map.of(), Set.of(), Set.of(), Set.of(),
                RetainedNavigationGeometry.facts(shapes, Map.of()), new KnownNaturalGeometry.Composite(List.of()));
    }
    private static PedestrianGeometryRevision ground(KnownPedestrianGroundVersions versions, Object bootstrap,
            Set<BlockPosition> obstacles, Map<TerrainColumn, List<SurfaceAnchor>> supports, Set<BlockPosition> unknown) {
        return versions.capture(bootstrap, obstacles, supports, Set.of(), unknown, Set.of(),
                RegionalBlockMap.copyOf(Map.of()), new KnownNaturalGeometry.Composite(List.of()));
    }
    @Test void resolvedCollisionFactsInvalidateTheirRegionEvenWhenOccupiedUnionIsUnchanged() {
        var bootstrap = new Object(); var position = new BlockPosition(2, 64, 2);
        var versions = new KnownPedestrianGroundVersions();
        var before = ground(versions, bootstrap, Set.of(position), Map.of(), Set.of(position));
        var after = ground(versions, bootstrap, Set.of(position), Map.of(), Set.of());
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
        var versions = new KnownPedestrianGroundVersions();
        var initial = ground(versions, bootstrap, Set.of(wall), supports, Set.of());
        var before = KnownPedestrianGeometryScopes.revision("test:retained", RegionalBlockSet.copyOf(Set.of(wall)), supports, initial);
        var otherTask = KnownPedestrianGeometryScopes.revision("test:other-task", RegionalBlockSet.copyOf(Set.of()), supports, initial);
        assertNotEquals(before.scope(), otherTask.scope());
        assertEquals(before.geometryScope(), otherTask.geometryScope());
        assertNotEquals(before.at(0, 0), otherTask.at(0, 0), "passage restrictions stay in the local geometry proof");
        assertEquals(before.at(1, 0), otherTask.at(1, 0), "identical distant terrain can be shared across task policies");
        for (int index = 0; index < 300; index++)
            KnownPedestrianGeometryScopes.revision("test:eviction-" + index, RegionalBlockSet.copyOf(Set.of()), Map.of(), initial);
        var reconstructed = KnownPedestrianGeometryScopes.revision("test:retained", RegionalBlockSet.copyOf(Set.of(wall)), supports, initial);
        assertEquals(before.scope(), reconstructed.scope());
        assertSame(before.baseline(), reconstructed.baseline());
        assertTrue(reconstructed.changesSince(before).isEmpty());
        assertEquals(before.at(0, 0), reconstructed.at(0, 0));
        assertEquals(before.at(1, 0), reconstructed.at(1, 0));

        var changedSupports = Map.of(column, List.of(SurfaceAnchor.at(20, 62, 2)));
        var changedGround = ground(versions, bootstrap, Set.of(wall), changedSupports, Set.of());
        var changed = KnownPedestrianGeometryScopes.revision("test:retained", RegionalBlockSet.copyOf(Set.of(wall)), changedSupports, changedGround);
        assertEquals(Set.of(new PedestrianGeometryRevision.Region(1, 0)), changed.changesSince(reconstructed));
        assertEquals(before.at(0, 0), changed.at(0, 0));
        assertNotEquals(before.at(1, 0), changed.at(1, 0));
        var removedWall = KnownPedestrianGeometryScopes.revision("test:retained", RegionalBlockSet.copyOf(Set.of()), changedSupports, changedGround);
        assertEquals(Set.of(new PedestrianGeometryRevision.Region(0, 0)), removedWall.changesSince(changed));
        var foreign = ground(versions, new Object(), Set.of(), Map.of(), Set.of());
        assertFalse(foreign.matches(initial, Set.of(new PedestrianGeometryRevision.Region(30, 30))),
                "even pristine regions from different worlds cannot share a route proof");
        for (int index = 0; index < 12; index++) ground(versions, new Object(), Set.of(), Map.of(), Set.of());
        assertEquals(initial, ground(versions, bootstrap, Set.of(wall), supports, Set.of()),
                "world switching and cache eviction cannot alter an immutable world's regional facts");
        assertNotEquals(before.scope(), KnownPedestrianGeometryScopes.revision("test:retained", RegionalBlockSet.copyOf(Set.of()), Map.of(), foreign).scope());
        assertNotEquals(before.geometryScope(), KnownPedestrianGeometryScopes.revision("test:retained", RegionalBlockSet.copyOf(Set.of()), Map.of(), foreign).geometryScope());
    }
}
