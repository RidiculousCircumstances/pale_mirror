package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceFieldLayoutTest {
    private static ResourceFieldLayout.Cell cell(long id, int x, int y, int z) {
        var soil = SurfaceAnchor.at(x, y, z);
        return new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(id), soil.support().offset(0, 1, 0), soil, soil);
    }

    @Test void sizeDoesNotFollowStackCapacityOrBoundingRectangle() {
        for (int size : List.of(0, 1, 63, 64, 65, 129)) {
            var cells = new ArrayList<ResourceFieldLayout.Cell>();
            for (int index = 0; index < size; index++) cells.add(cell(index + 1L, index * 2, 63 + index % 3, index % 5));
            var water = List.of(new BlockPosition(-10, 63, -10));
            var layout = new ResourceFieldLayout(1, size + 1L, cells, water);
            var site = new ResourceSite(new SubjectId("site:custom"), new SubjectId("settlement:1"),
                    new SubjectId("structure:1-farm"), ResourceSiteKind.WHEAT_FIELD, layout);
            assertEquals(size, site.cropSlots().size());
            assertEquals(water, site.irrigationSlots());
            assertEquals(size * 2 + 1, site.managedSlots().size());
            assertSame(site.cropSlots(), site.cropSlots(), "read-only geometry must not be rebuilt per lookup");
        }
    }

    @Test void reorderExpandShrinkPreserveIdentityWithoutReusingRemovedCells() {
        var a = cell(1, 1, 63, 1); var b = cell(2, 4, 64, 2); var c = cell(3, 7, 65, 4);
        var original = new ResourceFieldLayout(1, 3, List.of(a, b), List.of());
        var expanded = original.revise(2, 4, List.of(b, c, a), List.of());
        assertEquals(a, expanded.requireCell(a.id()));
        assertEquals(b, expanded.cells().getFirst());
        assertEquals(0, expanded.workIndex(b.id()));
        assertEquals(2, expanded.workIndex(a.id()), "cell identity must not be mistaken for this revision's work index");
        var reduced = expanded.revise(3, 4, List.of(a), List.of());
        assertThrows(IllegalArgumentException.class, () -> reduced.requireCell(b.id()));
        assertThrows(IllegalArgumentException.class, () -> reduced.revise(4, 4, List.of(a, b), List.of()));
        assertThrows(IllegalArgumentException.class, () -> reduced.revise(4, 4, List.of(cell(1, 99, 63, 1)), List.of()));
        assertTrue(reduced.revise(4, 4, List.of(), List.of()).cells().isEmpty(), "empty field remains an identified layout");
        assertThrows(IllegalArgumentException.class, () -> reduced.revise(5, 4, List.of(a), List.of()));
        assertThrows(IllegalArgumentException.class, () -> reduced.revise(4, 2, List.of(a), List.of()));
    }

    @Test void rejectsDuplicateIdentityOverlappingFootprintsAndUndeclaredSupport() {
        var a = cell(1, 1, 63, 1);
        assertThrows(IllegalArgumentException.class, () -> new ResourceFieldLayout(1, 2, List.of(a, a), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ResourceFieldLayout(1, 3, List.of(a, cell(2, 1, 63, 1)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ResourceFieldLayout(1, 2, List.of(a), List.of(a.soil().support())));
        assertThrows(IllegalArgumentException.class, () -> new ResourceFieldLayout(1, 1, List.of(a), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ResourceFieldLayout.Cell(a.id(), a.crop().offset(0, 1, 0), a.soil(), a.workstation()));
    }

    @Test void sourceCollectionsCannotMutateACompiledLayout() {
        var source = new ArrayList<>(List.of(cell(1, 1, 63, 1)));
        var layout = new ResourceFieldLayout(1, 2, source, List.of()); source.clear();
        assertEquals(1, layout.cells().size());
        assertThrows(UnsupportedOperationException.class, () -> layout.cells().clear());
        assertThrows(UnsupportedOperationException.class, () -> layout.managedSlots().clear());
    }

    @Test void retainedFingerprintSeparatesSameRevisionAndIdsWithDifferentGeometryOrWorkOrder() {
        var a = cell(1, 1, 63, 1); var b = cell(2, 4, 63, 1);
        var original = new ResourceFieldLayout(1, 3, List.of(a, b), List.of());
        assertEquals(original.fingerprint(), new ResourceFieldLayout(1, 3, List.of(a, b), List.of()).fingerprint());
        assertNotEquals(original.fingerprint(), new ResourceFieldLayout(1, 3,
                List.of(cell(1, 20, 63, 1), b), List.of()).fingerprint());
        assertNotEquals(original.fingerprint(), new ResourceFieldLayout(1, 3, List.of(b, a), List.of()).fingerprint());
        assertNotEquals(original.fingerprint(), new ResourceFieldLayout(1, 3, List.of(a, b),
                List.of(new BlockPosition(10, 63, 10))).fingerprint());
    }

    @Test void exactCellAndChunkIndexesHandleNegativeCoordinatesWithoutChangingIdentity() {
        var western = cell(1, -17, 64, -1); var eastern = cell(2, 16, 67, 32);
        var layout = new ResourceFieldLayout(1, 3, List.of(western, eastern), List.of(new BlockPosition(-1, 63, -17)));
        assertEquals(Optional.of(western), layout.cropAt(western.crop()));
        assertEquals(Optional.of(eastern), layout.soilAt(eastern.soil().support()));
        assertEquals(Optional.empty(), layout.cropAt(eastern.soil().support()));
        assertTrue(layout.contains(new BlockPosition(-1, 63, -17)));
        assertFalse(layout.contains(new BlockPosition(-1, 63, -16)));
        assertEquals(Set.of(new ResourceFieldLayout.ChunkColumn(-2, -1), new ResourceFieldLayout.ChunkColumn(1, 2),
                new ResourceFieldLayout.ChunkColumn(-1, -2)), Set.copyOf(layout.occupiedChunks()));
        assertEquals(List.of(western), layout.cellsIn(new ResourceFieldLayout.ChunkColumn(-2, -1)));
        assertEquals(List.of(), layout.cellsIn(new ResourceFieldLayout.ChunkColumn(-1, -2)),
                "an irrigation-only chunk has no crop obligations");
        assertThrows(UnsupportedOperationException.class, () -> layout.occupiedChunks().clear());
        assertThrows(UnsupportedOperationException.class, () -> layout.cellsIn(new ResourceFieldLayout.ChunkColumn(-2, -1)).clear());
    }

    @Test void grayboxIsAnExplicitProducerChoiceNotGenericIrrigationInference() {
        var crops = new ArrayList<BlockPosition>();
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) crops.add(new BlockPosition(x, 64, z));
        var layout = FrontierResourceSitePlan.initialGrayboxLayout(crops);
        assertEquals(crops, layout.cropSlots());
        assertEquals(List.of(new BlockPosition(2, 63, -1), new BlockPosition(6, 63, -1),
                new BlockPosition(2, 63, 8), new BlockPosition(6, 63, 8)), layout.irrigationSlots());
        assertFalse(ResourceSiteHarvestTraversal.supportsCurrentHarvest(new ResourceSite(new SubjectId("site:graybox"),
                new SubjectId("settlement:1"), new SubjectId("structure:1-farm"), ResourceSiteKind.WHEAT_FIELD, layout)));
        var serpentine = new ArrayList<BlockPosition>();
        for (int x = 0; x < 8; x++) {
            if (x % 2 == 0) for (int z = 0; z < 8; z++) serpentine.add(new BlockPosition(x, 64, z));
            else for (int z = 7; z >= 0; z--) serpentine.add(new BlockPosition(x, 64, z));
        }
        assertTrue(ResourceSiteHarvestTraversal.supportsCurrentHarvest(new ResourceSite(new SubjectId("site:serpentine"),
                new SubjectId("settlement:1"), new SubjectId("structure:1-farm"), ResourceSiteKind.WHEAT_FIELD,
                FrontierResourceSitePlan.initialGrayboxLayout(serpentine))));
        var irregular = new ResourceSite(new SubjectId("site:custom"), new SubjectId("settlement:1"),
                new SubjectId("structure:1-farm"), ResourceSiteKind.WHEAT_FIELD,
                new ResourceFieldLayout(1, 3, List.of(cell(1, 0, 63, 0), cell(2, 2, 63, 0)), List.of()));
        assertFalse(ResourceSiteHarvestTraversal.supportsCurrentHarvest(irregular),
                "the current fixed-output executor must refuse a layout it cannot work honestly");
        assertThrows(IllegalArgumentException.class, () -> FrontierResourceSitePlan.initialGrayboxLayout(crops.subList(0, 63)));
    }
}
