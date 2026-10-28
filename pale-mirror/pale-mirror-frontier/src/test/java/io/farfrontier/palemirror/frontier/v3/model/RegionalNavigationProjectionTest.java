package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionalNavigationProjectionTest {
    @Test void regionalUnionsShareRemoteContentsAndColumnsPreserveSetSemantics() {
        var a = new BlockPosition(1, 60, 1); var b = new BlockPosition(33, 70, 1); var c = a.offset(1, 0, 0);
        var first = RegionalBlockSet.copyOf(Set.of(a, b)); var second = RegionalBlockSet.copyOf(Set.of(a, c));
        var union = RegionalBlockSet.union(List.of(first, second));
        assertEquals(Set.of(a, b, c), union); assertEquals(Set.of(a, b), first);
        assertSame(first.token(Region.at(b)), union.token(Region.at(b)), "remote blocks need no duplicated set");
        var columns = RegionalColumnSet.fromPositions(List.of(a, a.offset(0, 20, 0), b));
        var expected = Set.of(new TerrainColumn(a.x(), a.z()), new TerrainColumn(b.x(), b.z()));
        assertEquals(expected, columns); assertEquals(columns, expected); assertEquals(expected.hashCode(), columns.hashCode());
        var projectedUnion = RegionalColumnSet.union(List.of(columns, RegionalColumnSet.fromPositions(List.of(c))));
        assertEquals(3, projectedUnion.size());
        assertSame(columns.token(Region.at(b)), projectedUnion.token(Region.at(b)));
        assertNotEquals(columns.token(Region.at(a)), projectedUnion.token(Region.at(a)));
        assertThrows(UnsupportedOperationException.class, () -> projectedUnion.add(new TerrainColumn(4, 4)));
    }
    @Test void independentSnapshotsReuseExactEqualityWithoutHidingHashCollisionsOrEdits() {
        var position = new BlockPosition(1, 60, 1);
        var comparisons = new java.util.concurrent.atomic.AtomicInteger();
        var first = RegionalBlockMap.copyOf(Map.of(position, new CountedValue("Aa", comparisons)));
        var same = RegionalBlockMap.copyOf(Map.of(position, new CountedValue("Aa", comparisons)));
        assertTrue(first.changesFrom(same).isEmpty());
        assertTrue(comparisons.get() > 0, "first comparison must inspect actual facts");
        comparisons.set(0);
        assertTrue(same.changesFrom(first).isEmpty());
        assertTrue(first.changesFrom(same).isEmpty());
        assertEquals(0, comparisons.get(), "already proven immutable pair must not rescan blocks");
        var collision = same.with(Map.of(position, new CountedValue("BB", comparisons)));
        assertEquals(first.hashCode(), collision.hashCode());
        assertEquals(Set.of(Region.at(position)), collision.changesFrom(first));
        assertEquals(Set.of(Region.at(position)), first.changesFrom(collision));
        assertTrue(first.changesFrom(same).isEmpty(), "successor cannot mutate the proven snapshots");
        assertEquals(Set.of(Region.at(position)), same.changesFrom(same.replace(Map.of(Region.at(position), Map.of()))));
        var block = new BlockPosition(1, 0, 0); var differentBlock = new BlockPosition(0, 31, 0);
        assertEquals(block.hashCode(), differentBlock.hashCode());
        var blocks = RegionalBlockSet.copyOf(Set.of(block)); var equalBlocks = RegionalBlockSet.copyOf(Set.of(block));
        assertTrue(blocks.changesFrom(equalBlocks).isEmpty());
        var collidingBlocks = RegionalBlockSet.copyOf(Set.of(differentBlock));
        assertEquals(blocks.hashCode(), collidingBlocks.hashCode());
        assertEquals(Set.of(Region.at(block)), blocks.changesFrom(collidingBlocks));
        assertTrue(equalBlocks.changesFrom(blocks).isEmpty(), "a colliding new part cannot poison earlier exact equality");
        var column = new TerrainColumn(1, 0);
        var levels = RegionalSurfaceMap.copyOf(Map.of(column, List.of(new SurfaceAnchor(block))));
        var equalLevels = RegionalSurfaceMap.copyOf(Map.of(column, List.of(new SurfaceAnchor(block))));
        assertTrue(levels.changesFrom(equalLevels).isEmpty());
        var changedLevels = RegionalSurfaceMap.copyOf(Map.of(column, List.of(new SurfaceAnchor(block.offset(0, 1, 0)))));
        assertEquals(Set.of(Region.at(block)), changedLevels.changesFrom(levels));
        assertTrue(equalLevels.changesFrom(levels).isEmpty());
    }
    private record CountedValue(String text, java.util.concurrent.atomic.AtomicInteger comparisons) {
        @Override public int hashCode() { return text.hashCode(); }
        @Override public boolean equals(Object other) {
            comparisons.incrementAndGet();
            return other instanceof CountedValue value && text.equals(value.text);
        }
    }
    @Test void catalogInvalidationTouchesOnlyRegionsUsingAChangedClassification() {
        var stone = new NavigationBlockState("minecraft:stone", Map.of());
        var dirt = new NavigationBlockState("minecraft:dirt", Map.of());
        var torch = new NavigationBlockState("minecraft:torch", Map.of());
        var left = new BlockPosition(1, 60, 1); var right = new BlockPosition(32, 60, 1);
        var facts = RegionalBlockMap.copyOf(Map.of(left, Optional.of(stone), right, Optional.of(dirt)));
        var projector = new RegionalNavigationProjection();
        var empty = NavigationGeometryState.empty();
        var original = projector.read(facts, empty);
        var unknown = NavigationObservation.unavailable(NavigationObservation.Kind.POSITION_REQUIRED);
        var unrelated = empty.observe(0, Map.of(), Map.of(torch, unknown));
        assertSame(original, projector.read(facts, unrelated));
        var affected = unrelated.observe(1, Map.of(), Map.of(stone, unknown));
        var changed = projector.read(facts, affected);
        assertTrue(changed.geometry().unknown().contains(left));
        assertFalse(changed.geometry().unknown().contains(right));
        assertSame(original.blocks().in(Region.at(right)), changed.blocks().in(Region.at(right)));
        assertEquals(original, projector.read(facts, empty), "catalog rollback restores exact contacts");
        var removed = facts.replace(Map.of(Region.at(left), Map.of()));
        var afterRemoval = projector.read(removed, empty);
        assertSame(afterRemoval, projector.read(removed, affected), "retired type cannot invalidate unrelated regions");
    }

    @Test void layeredContactsRegionRemovalUnknownAndCatalogChangesUseOneProjectionContract() {
        var stone = new NavigationBlockState("minecraft:stone", Map.of());
        var air = new NavigationBlockState("minecraft:air", Map.of());
        var lower = new BlockPosition(15, 60, 0); var upper = lower.offset(0, 3, 0); var neighbour = new BlockPosition(16, 60, 0);
        var values = new HashMap<BlockPosition, Optional<NavigationBlockState>>();
        for (int y = 60; y <= 66; y++) values.put(new BlockPosition(15, y, 0), Optional.of(y == 60 || y == 63 ? stone : air));
        values.put(neighbour, Optional.of(stone));
        var original = RegionalBlockMap.copyOf(values); var catalog = NavigationGeometryState.empty();
        var projector = new RegionalNavigationProjection(); var before = projector.read(original, catalog);
        assertEquals(List.of(new SurfaceAnchor(lower), new SurfaceAnchor(upper)), before.geometry().supports().get(new TerrainColumn(15, 0)));
        var broken = original.with(Map.of(upper, Optional.of(air), lower.offset(0, 1, 0), Optional.empty()));
        var after = projector.read(broken, catalog);
        assertTrue(after.geometry().absentSupports().contains(upper));
        assertTrue(after.geometry().unknown().contains(lower.offset(0, 1, 0)));
        assertFalse(after.blocks().containsKey(lower.offset(0, 1, 0)));
        assertSame(before.blocks().in(Region.at(neighbour)), after.blocks().in(Region.at(neighbour)));
        assertSame(after, projector.read(broken, catalog));
        var removed = broken.replace(Map.of(Region.at(neighbour), Map.of()));
        var retired = projector.read(removed, catalog);
        assertFalse(retired.blocks().containsKey(neighbour)); assertFalse(retired.geometry().volumes().contains(neighbour));
        var unavailable = catalog.observe(0, Map.of(), Map.of(stone, NavigationObservation.unavailable(NavigationObservation.Kind.POSITION_REQUIRED)));
        var unknown = projector.read(removed, unavailable);
        assertTrue(unknown.geometry().unknown().contains(lower));
        assertFalse(unknown.geometry().supports().containsKey(new TerrainColumn(15, 0)));
        assertEquals(before, projector.read(original, catalog), "returning to older evidence must not keep successor facts");
    }
}
