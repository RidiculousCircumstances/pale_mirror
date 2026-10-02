package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3HotHandoffTest {
    @Test void currentPhysicalPredecessorCannotStandInForNewerColdFieldState() {
        var id = new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout.CellId(1);
        var floor = io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor.at(10, 63, 10);
        var layout = new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout(1, 3, List.of(
                new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout.Cell(
                        id, floor.support().offset(0, 1, 0), floor, floor)), List.of());
        var cycle = io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle.seeded(new SubjectId("site:test-field"), layout, 1);
        var witness = FrontierV3ResourceFieldWitness.claimed(cycle.siteId(), 1,
                io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface.fromCycle(cycle));
        var current = new FrontierV3ResourceFieldObservation.Owned(
                io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface.Condition.of(cycle.cell(id)));
        assertEquals(FrontierV3HotHandoff.Status.READY,
                FrontierV3ResourceFieldHandoff.inspectCells(cycle, witness, layout.cells(), ignored -> current).status());
        assertEquals(FrontierV3HotHandoff.Status.WAITING,
                FrontierV3ResourceFieldHandoff.inspectCells(cycle.advanceGrowthStage(), witness, layout.cells(), ignored -> current).status());
        assertEquals(FrontierV3HotHandoff.Status.WAITING,
                FrontierV3ResourceFieldHandoff.inspectCells(cycle, witness, layout.cells(),
                        ignored -> FrontierV3ResourceFieldObservation.Unloaded.INSTANCE).status());
        assertEquals(FrontierV3HotHandoff.Status.WAITING,
                FrontierV3ResourceFieldHandoff.inspectCells(
                        io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle.seeded(cycle.siteId(), layout, 2),
                        witness, layout.cells(), ignored -> current).status());
    }
    @Test void executorVisitCannotSubstituteForEveryOwnerCurrentReceipt() {
        var field = new SubjectId("site:1-wheat-field");
        var depot = new SubjectId("container:1-depot");
        var ready = new FrontierV3HotHandoff.Check(field, FrontierV3HotHandoff.Status.READY, "current_field");
        var waiting = new FrontierV3HotHandoff.Check(depot, FrontierV3HotHandoff.Status.WAITING, "pending_resource_effect");
        assertFalse(new FrontierV3HotHandoff.Review(17L, List.of(ready, waiting)).ready());
        assertFalse(new FrontierV3HotHandoff.Review(17L, List.of(ready, waiting)).presentable());
        assertTrue(new FrontierV3HotHandoff.Review(18L, List.of(new FrontierV3HotHandoff.Check(
                field, FrontierV3HotHandoff.Status.CONFLICT, "foreign_cell"))).presentable());
        assertFalse(new FrontierV3HotHandoff.Review(18L, List.of(new FrontierV3HotHandoff.Check(
                field, FrontierV3HotHandoff.Status.CONFLICT, "foreign_cell"))).ready());
        assertTrue(new FrontierV3HotHandoff.Review(19L, List.of(ready,
                new FrontierV3HotHandoff.Check(depot, FrontierV3HotHandoff.Status.READY, "current_container"))).ready());
    }
}
