package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnownPedestrianGeometryScopesTest {
    @Test void newAndEvictedTaskScopesReuseRemoteFactsWithoutInheritingLocalPermissions() {
        var epoch = new Object(); var baseline = new Object();
        var local = new BlockPosition(2, 64, 2); var remote = new BlockPosition(16000, 64, 16000);
        var localRegion = PedestrianGeometryRevision.Region.at(local);
        var remoteRegion = PedestrianGeometryRevision.Region.at(remote);
        var forbidRemoteHash = new java.util.concurrent.atomic.AtomicBoolean();
        Object remoteFact = new Object() {
            @Override public int hashCode() {
                if (forbidRemoteHash.get()) throw new AssertionError("local task change rebuilt remote world facts");
                return 19;
            }
        };
        var ground = new PedestrianGeometryRevision(epoch, baseline, Map.of(remoteRegion, remoteFact));
        var walls = RegionalBlockSet.copyOf(Set.of(local, remote));
        var open = walls.overlay(Set.of(local), Set.of());
        var empty = RegionalBlockSet.copyOf(Set.of());
        var restrictions = RegionalBlockSet.copyOf(Set.of(local));
        var supports = RegionalSurfaceMap.copyOf(Map.of(new TerrainColumn(local.x(), local.z()),
                List.of(SurfaceAnchor.at(local.x(), 63, local.z()))));
        var first = KnownPedestrianGeometryScopes.revision("seed:task", walls, supports, empty, ground);
        forbidRemoteHash.set(true);
        for (int i = 0; i < 300; i++) {
            var next = KnownPedestrianGeometryScopes.revision("new:task-" + i,
                    i % 2 == 0 ? open : walls, supports, i % 2 == 0 ? restrictions : empty, ground);
            assertSame(first.at(remoteRegion.x(), remoteRegion.z()), next.at(remoteRegion.x(), remoteRegion.z()));
            assertNotEquals(first.scope(), next.scope(), "a composition seed never supplies task identity");
            assertEquals(first.geometryScope(), next.geometryScope());
            assertEquals(i % 2 != 0, first.at(localRegion.x(), localRegion.z()).equals(next.at(localRegion.x(), localRegion.z())),
                    "new task facts retain their own walls, supports and denied contacts");
        }
        var restored = KnownPedestrianGeometryScopes.revision("seed:task", walls, supports, empty, ground);
        assertEquals(first, restored, "eviction and unrelated task reads cannot change permission facts");
        assertSame(first.at(remoteRegion.x(), remoteRegion.z()), restored.at(remoteRegion.x(), remoteRegion.z()));
        var historical = KnownPedestrianGeometryScopes.revision("old:branch", open, Map.of(), restrictions, ground);
        assertNotEquals(first.at(localRegion.x(), localRegion.z()), historical.at(localRegion.x(), localRegion.z()));
        assertEquals(first, KnownPedestrianGeometryScopes.revision("seed:task", walls, supports, empty, ground));
    }

    @Test void taskSupportExclusionsArePartOfSharedLocalGeometryFacts() {
        var state = ResourceSiteHarvestProcessTest.initial();
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state)
                .forProfile(io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile.VILLAGER);
        var geometry = knowledge.geometry(); var bounds = geometry.bounds();
        var from = geometry.supportAt(bounds.minX() + 8, bounds.minZ() + 8);
        var to = geometry.supportAt(bounds.minX() + 9, bounds.minZ() + 8);
        assertNotNull(from); assertNotNull(to); assertTrue(geometry.canTraverse(from, to));
        var restricted = knowledge.withTaskExclusions(Set.of(to)).geometry();
        assertTrue(restricted.supportExcluded(to));
        assertFalse(restricted.canTraverse(from, to), "a shared edge result cannot erase an explicit support restriction");
        var local = PedestrianGeometryRevision.Region.at(to.support());
        var before = geometry.regionalRevision().orElseThrow(); var after = restricted.regionalRevision().orElseThrow();
        assertNotEquals(before.at(local.x(), local.z()), after.at(local.x(), local.z()));
        assertEquals(before.at(1000, 1000), after.at(1000, 1000));
        assertTrue(geometry.canTraverse(from, to), "restriction does not mutate the predecessor's permissions");
    }
    private record CollidingFact(String value) { @Override public int hashCode() { return 7; } }
    @Test void regionalPagesPreserveExactCollisionsRemovalAndImmutablePredecessors() {
        var a = new PedestrianGeometryRevision.Region(-1, -1);
        var remote = new PedestrianGeometryRevision.Region(1000, 1000);
        var before = ImmutableRegionMap.copyOf(Map.of(a, new CollidingFact("before"), remote, new CollidingFact("remote")));
        var after = before.with(Map.of(a, java.util.Optional.of(new CollidingFact("after"))));
        assertEquals(before.hashCode(), after.hashCode(), "collision does not establish equality");
        assertNotEquals(before, after); assertEquals(Set.of(a), after.changesFrom(before));
        assertEquals(new CollidingFact("before"), before.get(a));
        assertEquals(Map.of(a, new CollidingFact("after"), remote, new CollidingFact("remote")), after);
        assertEquals(after, Map.of(a, new CollidingFact("after"), remote, new CollidingFact("remote")));
        var removed = after.with(Map.of(a, java.util.Optional.empty()));
        assertEquals(Set.of(a), removed.changesFrom(after)); assertFalse(removed.containsKey(a));
        assertEquals(Map.of(remote, new CollidingFact("remote")), removed);
        assertThrows(UnsupportedOperationException.class, () -> before.entrySet().iterator().next().setValue(new CollidingFact("forged")));
        assertThrows(UnsupportedOperationException.class, () -> before.remove(a));
    }
    @Test void localWideBodyProofRetainsOnlyItsExactNeighbourFacts() {
        var forbidRemoteHash = new java.util.concurrent.atomic.AtomicBoolean();
        var remote = new Object() {
            @Override public int hashCode() {
                if (forbidRemoteHash.get()) throw new AssertionError("local proof consulted remote fact hashes");
                return 19;
            }
        };
        var scope = new Object(); var baseline = new Object();
        var local = new PedestrianGeometryRevision.Region(0, 0);
        var neighbouring = new PedestrianGeometryRevision.Region(1, 0);
        var remoteRegion = new PedestrianGeometryRevision.Region(1000, 1000);
        var terrain = new PedestrianGeometryRevision(scope, baseline, Map.of(local, "local", neighbouring, "wall", remoteRegion, remote));
        forbidRemoteHash.set(true);
        var wide = terrain.profiled(io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile.DONKEY);
        assertEquals(List.of("local", "wall"), wide.at(0, 0));
        var proof = wide.restrictedTo(Set.of(local));
        assertEquals(Set.of(local), proof.revisions().keySet());
        assertTrue(wide.matches(proof, Set.of(local)));
        assertSame(baseline, wide.at(-1000, -1000));
        var changed = terrain.changed(Set.of(neighbouring)).profiled(
                io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile.DONKEY);
        assertFalse(changed.matches(proof, Set.of(local)), "a neighbour affecting body clearance still invalidates the local proof");
        assertTrue(changed.changesSince(wide).contains(local));
        assertFalse(changed.changesSince(wide).contains(remoteRegion));
    }
    @Test void localSuccessorReusesRemoteFactHashesAndMatchesFreshProfileAfterRemoval() {
        var scope = new Object(); var baseline = new Object(); var noHash = new java.util.concurrent.atomic.AtomicBoolean();
        Object remoteFact = new Object() {
            @Override public int hashCode() { if (noHash.get()) throw new AssertionError("unchanged remote facts rehashed"); return 17; }
        };
        var facts = new java.util.HashMap<PedestrianGeometryRevision.Region, Object>();
        facts.put(new PedestrianGeometryRevision.Region(-1, -1), "removed");
        facts.put(new PedestrianGeometryRevision.Region(0, 0), "changed");
        facts.put(new PedestrianGeometryRevision.Region(1, 0), "neighbour");
        facts.put(new PedestrianGeometryRevision.Region(1000, 1000), remoteFact);
        // Outside the local halo, but inside the same spatial hash page as (0,0).
        facts.put(new PedestrianGeometryRevision.Region(15, 15), remoteFact);
        var before = new PedestrianGeometryRevision(scope, baseline, facts);
        assertSame(before.revisions(), before.profiled(io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile.VILLAGER).revisions());
        var oldProfile = before.profiled(io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile.DONKEY);
        assertEquals(List.of(List.of("removed", "changed"), "neighbour"), oldProfile.at(0, 0));
        facts.remove(new PedestrianGeometryRevision.Region(-1, -1));
        facts.put(new PedestrianGeometryRevision.Region(0, 0), "replacement");
        var expected = new PedestrianGeometryRevision(scope, baseline, facts)
                .profiled(io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile.DONKEY);
        oldProfile.hashCode(); expected.hashCode(); // Explicit complete consumers warm the reusable page/hash seed.
        noHash.set(true);
        var pages = ImmutableRegionMap.copyOf(before.revisions()).with(Map.of(
                new PedestrianGeometryRevision.Region(-1, -1), java.util.Optional.empty(),
                new PedestrianGeometryRevision.Region(0, 0), java.util.Optional.of("replacement")));
        var actual = before.withRevisions(pages)
                .profiled(io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile.DONKEY);
        assertEquals(List.of("replacement", "neighbour"), actual.at(0, 0));
        assertTrue(before.withRevisions(Map.of()).profiled(io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile.DONKEY).revisions().isEmpty());
        assertEquals(expected, actual); assertEquals(expected.hashCode(), actual.hashCode());
        assertEquals("removed", before.at(-1, -1));
        assertNotEquals(actual.at(0, 0), oldProfile.at(0, 0));
        assertSame(oldProfile.at(1000, 1000), actual.at(1000, 1000));
        assertEquals(expected.changesSince(oldProfile), actual.changesSince(oldProfile));
        assertFalse(actual.matches(oldProfile, Set.of(new PedestrianGeometryRevision.Region(0, 0))));
        assertTrue(actual.matches(oldProfile, Set.of(new PedestrianGeometryRevision.Region(1000, 1000))));
    }
    @Test void spatialDirectoriesKeepExactContentsAndCountsAcrossLocalReplacementAndRemoval() {
        var local = new BlockPosition(0, 64, 0); var replacement = new BlockPosition(1, 33, 0);
        var remote = new BlockPosition(16000, 64, 16000); var region = PedestrianGeometryRevision.Region.at(local);
        assertEquals(local.hashCode(), replacement.hashCode());
        var blocks = RegionalBlockSet.copyOf(Set.of(local, remote));
        var changed = blocks.replace(Map.of(region, Set.of(replacement)));
        assertEquals(blocks.hashCode(), changed.hashCode()); assertNotEquals(blocks, changed);
        assertEquals(Set.of(region), changed.changesFrom(blocks));
        assertEquals(Set.of(replacement, remote), changed); assertEquals(2, changed.size());
        assertEquals(Set.of(remote), changed.replace(Map.of(region, Set.of())));
        var facts = RegionalBlockMap.copyOf(Map.of(local, new CollidingFact("before"), remote, new CollidingFact("remote")));
        var next = facts.with(Map.of(local, new CollidingFact("after")));
        assertEquals(facts.hashCode(), next.hashCode()); assertNotEquals(facts, next);
        assertEquals(Set.of(region), next.changesFrom(facts));
        assertEquals(Map.of(remote, new CollidingFact("remote")), next.replace(Map.of(region, Map.of())));
        var column = new TerrainColumn(local.x(), local.z()); var remoteColumn = new TerrainColumn(remote.x(), remote.z());
        var surfaces = RegionalSurfaceMap.copyOf(Map.of(column, List.of(new SurfaceAnchor(local)),
                remoteColumn, List.of(new SurfaceAnchor(remote))));
        var reduced = surfaces.replace(Map.of(region, Map.of()));
        assertEquals(Map.of(remoteColumn, List.of(new SurfaceAnchor(remote))), reduced);
        assertEquals(reduced.entrySet().hashCode(), reduced.hashCode()); assertEquals(1, reduced.size());
        assertEquals(2, surfaces.size()); assertEquals(Set.of(region), reduced.changesFrom(surfaces));
        assertSame(blocks.token(PedestrianGeometryRevision.Region.at(remote)), changed.token(PedestrianGeometryRevision.Region.at(remote)));
        assertSame(facts.token(PedestrianGeometryRevision.Region.at(remote)), next.token(PedestrianGeometryRevision.Region.at(remote)));
        assertSame(surfaces.token(PedestrianGeometryRevision.Region.at(remote)), reduced.token(PedestrianGeometryRevision.Region.at(remote)));
    }
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
    @Test void batchedOwnerReplacementRetainsExactRegionalPartsAndIndependentForks() {
        var wall = new BlockPosition(2, 64, 2); var remote = new BlockPosition(160, 64, 160);
        var terrain = RegionalBlockSet.copyOf(Set.of(wall, remote));
        var parts = new RegionalBlockContributions<String>();
        var before = parts.replace("terrain", terrain);
        assertSame(terrain.token(PedestrianGeometryRevision.Region.at(remote)), before.token(PedestrianGeometryRevision.Region.at(remote)),
                "an indexed producer part must not be enumerated and copied into another identical part");
        var fork = parts.fork();
        var transferred = parts.replaceAll(Map.of("terrain", RegionalBlockSet.copyOf(Set.of(remote)),
                "physical", RegionalBlockSet.copyOf(Set.of(wall))));
        assertSame(before, transferred, "a joint owner change has an unchanged final obstacle union");
        var opened = parts.replaceAll(Map.of("physical", RegionalBlockSet.copyOf(Set.of())));
        assertEquals(Set.of(remote), opened); assertEquals(Set.of(wall, remote), before);
        assertSame(before.token(PedestrianGeometryRevision.Region.at(remote)), opened.token(PedestrianGeometryRevision.Region.at(remote)));
        assertEquals(before, fork.snapshot(), "a private projection fork cannot mutate the published predecessor");
        assertEquals(Set.of(wall, remote), fork.replaceAll(Map.of("physical", terrain)), "overlapping declarations retain their exact union");
        assertThrows(UnsupportedOperationException.class, () -> opened.clear());
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
        var before = KnownPedestrianGeometryScopes.revision("test:retained", RegionalBlockSet.copyOf(Set.of(wall)), supports, RegionalBlockSet.copyOf(Set.of()), initial);
        var otherTask = KnownPedestrianGeometryScopes.revision("test:other-task", RegionalBlockSet.copyOf(Set.of()), supports, RegionalBlockSet.copyOf(Set.of()), initial);
        assertNotEquals(before.scope(), otherTask.scope());
        assertEquals(before.geometryScope(), otherTask.geometryScope());
        assertNotEquals(before.at(0, 0), otherTask.at(0, 0), "passage restrictions stay in the local geometry proof");
        assertEquals(before.at(1, 0), otherTask.at(1, 0), "identical distant terrain can be shared across task policies");
        for (int index = 0; index < 300; index++)
            KnownPedestrianGeometryScopes.revision("test:eviction-" + index, RegionalBlockSet.copyOf(Set.of()), Map.of(), RegionalBlockSet.copyOf(Set.of()), initial);
        var reconstructed = KnownPedestrianGeometryScopes.revision("test:retained", RegionalBlockSet.copyOf(Set.of(wall)), supports, RegionalBlockSet.copyOf(Set.of()), initial);
        assertEquals(before.scope(), reconstructed.scope());
        assertSame(before.baseline(), reconstructed.baseline());
        assertTrue(reconstructed.changesSince(before).isEmpty());
        assertEquals(before.at(0, 0), reconstructed.at(0, 0));
        assertEquals(before.at(1, 0), reconstructed.at(1, 0));

        var changedSupports = Map.of(column, List.of(SurfaceAnchor.at(20, 62, 2)));
        var changedGround = ground(versions, bootstrap, Set.of(wall), changedSupports, Set.of());
        var changed = KnownPedestrianGeometryScopes.revision("test:retained", RegionalBlockSet.copyOf(Set.of(wall)), changedSupports, RegionalBlockSet.copyOf(Set.of()), changedGround);
        assertEquals(Set.of(new PedestrianGeometryRevision.Region(1, 0)), changed.changesSince(reconstructed));
        assertEquals(before.at(0, 0), changed.at(0, 0));
        assertNotEquals(before.at(1, 0), changed.at(1, 0));
        var removedWall = KnownPedestrianGeometryScopes.revision("test:retained", RegionalBlockSet.copyOf(Set.of()), changedSupports, RegionalBlockSet.copyOf(Set.of()), changedGround);
        assertEquals(Set.of(new PedestrianGeometryRevision.Region(0, 0)), removedWall.changesSince(changed));
        var foreign = ground(versions, new Object(), Set.of(), Map.of(), Set.of());
        assertFalse(foreign.matches(initial, Set.of(new PedestrianGeometryRevision.Region(30, 30))),
                "even pristine regions from different worlds cannot share a route proof");
        for (int index = 0; index < 12; index++) ground(versions, new Object(), Set.of(), Map.of(), Set.of());
        assertEquals(initial, ground(versions, bootstrap, Set.of(wall), supports, Set.of()),
                "world switching and cache eviction cannot alter an immutable world's regional facts");
        assertNotEquals(before.scope(), KnownPedestrianGeometryScopes.revision("test:retained", RegionalBlockSet.copyOf(Set.of()), Map.of(), RegionalBlockSet.copyOf(Set.of()), foreign).scope());
        assertNotEquals(before.geometryScope(), KnownPedestrianGeometryScopes.revision("test:retained", RegionalBlockSet.copyOf(Set.of()), Map.of(), RegionalBlockSet.copyOf(Set.of()), foreign).geometryScope());
    }
}
