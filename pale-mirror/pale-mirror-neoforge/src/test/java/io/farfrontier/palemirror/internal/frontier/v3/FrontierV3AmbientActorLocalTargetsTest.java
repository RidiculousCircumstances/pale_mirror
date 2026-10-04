package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3AmbientActorLocalTargetsTest {
    @Test
    void ordinaryIngressWritersShareTheFixedEightCellWorkBudget() {
        assertEquals(8, FrontierV3GrayboxExecutor.firstVisibilityCellBudget());
        assertEquals(8, FrontierV3GrayboxExecutor.structuralCellBudget());
        assertEquals(8, FrontierV3ResourceSiteExecutor.projectionWriteBudget());
    }

    @Test
    void naturallyLoadedPlanChunkIsRetainedBeforeAnyPlayerIngress() {
        var planCell = new io.farfrontier.palemirror.frontier.v3.model.GrayboxCell(
                new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(32, 64, 48),
                new io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTarget(io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK, new SubjectId("route:natural-arrival")),
                io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial.ROUTE,
                io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart.ROUTE_SURFACE);
        var cursor = FrontierV3GrayboxExecutor.Cursor.fromCells(java.util.List.of(planCell), null);

        assertEquals(true, FrontierV3GrayboxExecutor.naturalFirstVisibilityCandidate(cursor, new net.minecraft.world.level.ChunkPos(2, 3)),
                "a naturally loaded immutable-plan chunk must enter bounded first-visibility work without a player join");
        assertEquals(false, FrontierV3GrayboxExecutor.naturalFirstVisibilityCandidate(cursor, new net.minecraft.world.level.ChunkPos(0, 0)),
                "ordinary natural terrain remains outside the first-visibility owner");
    }
}
