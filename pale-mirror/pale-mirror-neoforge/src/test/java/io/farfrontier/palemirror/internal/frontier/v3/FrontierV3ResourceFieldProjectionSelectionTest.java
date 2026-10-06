package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ResourceFieldProjectionSelectionTest {
    @Test void currentCellsDoNotSpendTheWriteSlotsNeededBySparseColdChanges() {
        var before = cycle(64, index -> 0);
        var after = cycle(64, index -> index == 7 || index == 63 ? 1 : 0);
        var witness = claimed(before);
        var selection = FrontierV3ResourceFieldProjectionSelection.select(after, witness, 0);
        assertEquals(List.of(new ResourceFieldLayout.CellId(8), new ResourceFieldLayout.CellId(64)), selection.cells());
        assertEquals(64, selection.probes());
        assertEquals(0, selection.nextIndex());
        assertEquals(0, witness.cell(new ResourceFieldLayout.CellId(64)).committed().growthStage(),
                "discovery does not acknowledge or write a physical effect");
    }

    @Test void discoveryAndWritesStayBoundedAndSparseDistantCellsCannotStarve() {
        var before = cycle(256, index -> 0);
        var after = cycle(256, index -> index == 199 ? 1 : 0);
        var witness = claimed(before);
        int cursor = 0;
        for (int turn = 0; turn < 4; turn++) {
            var selected = FrontierV3ResourceFieldProjectionSelection.select(after, witness, cursor);
            assertEquals(64, selected.probes()); cursor = selected.nextIndex();
            assertEquals(turn == 3 ? List.of(new ResourceFieldLayout.CellId(200)) : List.of(), selected.cells());
        }
        var allDirty = cycle(256, index -> 7);
        var first = FrontierV3ResourceFieldProjectionSelection.select(allDirty, witness, 0);
        var next = FrontierV3ResourceFieldProjectionSelection.select(allDirty, witness, first.nextIndex());
        assertEquals(8, first.cells().size()); assertEquals(8, first.probes());
        assertTrue(java.util.Collections.disjoint(first.cells(), next.cells()));
    }

    @Test void pendingWorkKeepsItsOwnerAndForeignEpochCannotSelectWrites() {
        var before = cycle(16, index -> 0);
        var after = cycle(16, index -> 1);
        var id = new ResourceFieldLayout.CellId(1);
        var transition = ResourceFieldCellTransition.between(before.siteId(), before.epoch(), before.layout().revision(),
                id, ResourceFieldPhysicalSurface.Condition.of(before.cell(id)),
                ResourceFieldPhysicalSurface.Condition.of(after.cell(id)));
        var witness = claimed(before).begin(transition, "work:retained");
        assertFalse(FrontierV3ResourceFieldProjectionSelection.select(after, witness, 0).cells().contains(id));
        assertEquals("work:retained", witness.cell(id).pending().orElseThrow().causationId());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceFieldProjectionSelection.select(
                ResourceFieldCycle.seeded(before.siteId(), before.layout(), 2), witness, 0));
    }

    private static FrontierV3ResourceFieldWitness claimed(ResourceFieldCycle cycle) {
        return FrontierV3ResourceFieldWitness.claimed(cycle.siteId(), cycle.epoch(), ResourceFieldPhysicalSurface.fromCycle(cycle));
    }
    private static ResourceFieldCycle cycle(int count, java.util.function.IntUnaryOperator age) {
        var cells = IntStream.range(0, count).mapToObj(index -> {
            var soil = SurfaceAnchor.at(index / 16, 63, index % 16);
            return new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(index + 1),
                    soil.support().offset(0, 1, 0), soil, soil);
        }).toList();
        var layout = new ResourceFieldLayout(1, count + 1, cells, List.of());
        var states = new LinkedHashMap<ResourceFieldLayout.CellId, ResourceFieldCycle.CellState>();
        for (int i = 0; i < count; i++) {
            int stage = age.applyAsInt(i);
            states.put(cells.get(i).id(), new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.FARMLAND,
                    stage == 7 ? ResourceFieldCycle.Crop.MATURE : ResourceFieldCycle.Crop.GROWING, stage, false, false));
        }
        return ResourceFieldCycle.restore(new SubjectId("site:projection-test"), layout, 1, states);
    }
}
