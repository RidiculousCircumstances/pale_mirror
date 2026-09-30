package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3AmbientActorLocalTargetsTest {
    @Test
    void presentationIdlingCannotReachAnotherCanonicalSupportColumn() {
        assertEquals(0.20D, FrontierV3AmbientActorLocalTargets.boundedPresentationRadius(0.20D));
        assertEquals(0.40D, FrontierV3AmbientActorLocalTargets.boundedPresentationRadius(1.50D));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3AmbientActorLocalTargets.boundedPresentationRadius(-0.01D));
    }

    @Test
    void presentationAnchorFollowsTheCanonicalActorRatherThanAStaleLeaseOrigin() {
        var state = FrontierV3FixtureCatalog.serviceDecontaminationConfiguration(new WorldId("frontier:ambient-local-anchor"), 41L).initialState();
        SubjectId medic = new SubjectId("resident:9-29");
        var moved = state.withActorBody(medic, new BodyPosition(-334, 65, 310));

        assertEquals(moved.actorLocations().get(medic).supportingSurface().support(), FrontierV3AmbientActorLocalTargets.localAnchor(moved, medic));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3AmbientActorLocalTargets.localAnchor(moved, new SubjectId("resident:missing")));
    }

    @Test
    void localTargetsUseTheCanonicalFeetHeightRatherThanTheSupportBlock() {
        var state = FrontierV3FixtureCatalog.serviceDecontaminationConfiguration(new WorldId("frontier:ambient-local-height"), 41L).initialState();
        SubjectId medic = new SubjectId("resident:9-29");

        assertEquals(state.actorLocations().get(medic).body().y(),
                FrontierV3AmbientActorLocalTargets.targetFeetY(state.actorLocations().get(medic).body()));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3AmbientActorLocalTargets.targetFeetY(null));
    }

    @Test
    void declaredWorkReturnUsesTheRetainedWorkAnchorRatherThanTheFinalHarvestHandoff() {
        var state = FrontierV3FixtureCatalog.serviceDecontaminationConfiguration(new WorldId("frontier:ambient-work-return"), 41L).initialState();
        SubjectId farmer = new SubjectId("resident:7-31");
        BodyPosition finalCrop = state.actorLocations().get(farmer).body();
        BodyPosition farmAnchor = new BodyPosition(finalCrop.x() + 9, finalCrop.y(), finalCrop.z() - 7);
        AmbientActorLease lease = new AmbientActorLease(farmer, finalCrop,
                io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 1L,
                AmbientLeaseStatus.HOT, AmbientGoalKind.WORK, farmAnchor);

        assertEquals(true, FrontierV3AmbientActorLocalTargets.directedGoal(lease));
        assertEquals(true, FrontierV3AmbientActorLocalTargets.directedGoal(
                lease.withGoal(AmbientGoalKind.ACTOR_MOVEMENT, farmAnchor)));
        assertEquals(farmAnchor.x() + 0.5D, FrontierV3AmbientActorLocalTargets.localTargetAt(state, farmer, finalCrop, lease, 0L).x);
        assertEquals(farmAnchor.z() + 0.5D, FrontierV3AmbientActorLocalTargets.localTargetAt(state, farmer, finalCrop, lease, 0L).z);
    }

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
