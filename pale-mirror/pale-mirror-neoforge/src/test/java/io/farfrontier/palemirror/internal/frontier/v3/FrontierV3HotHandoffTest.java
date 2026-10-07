package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3HotHandoffTest {
    @Test void admittedTerrainDoesNotWaitForLaterEffectsButFreshWorkStillDoes() {
        var waiting = new FrontierV3HotHandoff.Review(31L, List.of(new FrontierV3HotHandoff.Check(
                new SubjectId("container:7-depot"), FrontierV3HotHandoff.Status.WAITING, "container_effect_recovery")));
        assertTrue(FrontierV3GrayboxExecutor.FirstVisibility.READY.presentable(() -> {
            fail("already admitted terrain must not poll later resource effects"); return waiting;
        }));
        assertFalse(waiting.ready(), "terrain admission does not grant a new physical interaction");
        assertFalse(FrontierV3GrayboxExecutor.FirstVisibility.STATIC_CURRENT.presentable(() -> waiting),
                "a new native load still needs initial dynamic reconciliation");
        assertFalse(FrontierV3GrayboxExecutor.FirstVisibility.PENDING.presentable(() -> {
            fail("unfinished static geometry has no dynamic proof to query"); return waiting;
        }));
        var conflict = new FrontierV3HotHandoff.Review(32L, List.of(new FrontierV3HotHandoff.Check(
                new SubjectId("site:7-wheat-field"), FrontierV3HotHandoff.Status.CONFLICT, "field_foreign_change")));
        assertTrue(FrontierV3GrayboxExecutor.FirstVisibility.STATIC_CURRENT.presentable(() -> conflict));
        assertTrue(FrontierV3GrayboxExecutor.FirstVisibility.BLOCKED.presentable(() -> conflict),
                "a classified structural conflict must not permanently hide the whole chunk");
        assertFalse(conflict.ready(), "conflict visibility is not HOT work permission");
    }
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
