package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HotScoutOperationObserved;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.process.HivePerceptionProcess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.MinecartChest;

import java.util.Comparator;

/** Bounded observed-world scout sensing for one already materialized cargo scene. */
final class FrontierV3HotScoutObservation {
    private FrontierV3HotScoutObservation() { }

    static boolean observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                           FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease scoutLease) {
        if (!FrontierV3AmbientActorExecutor.bioform(state, actorId) || !FrontierV3AmbientActorExecutor.bioformProfile(state, actorId).isScout()
                || scoutLease.goal() != AmbientGoalKind.SCOUT_PATROL) return false;
        long now = runtime.canonicalState().orElseThrow().instant().ticks();
        SightedCarrier candidate = level.getEntitiesOfClass(MinecartChest.class, body.getBoundingBox().inflate(96.0D), entity -> !entity.isRemoved())
                .stream().map(entity -> FrontierV3CargoCarrierExecutor.activeLease(state, entity).map(lease -> new SightedCarrier(entity, lease)))
                .flatMap(java.util.Optional::stream)
                .filter(value -> sameFloorAnchor(level, new BlockPosition(value.carrier().getBlockX(), value.carrier().getBlockY(), value.carrier().getBlockZ()),
                        FrontierSceneBehaviors.logistics(value.lease()).cargoPosition()))
                .filter(value -> body.distanceToSqr(value.carrier()) <= 9_216.0D)
                .sorted(Comparator.comparingDouble((SightedCarrier value) -> body.distanceToSqr(value.carrier())).thenComparing(value -> value.lease().id()))
                .findFirst().orElse(null);
        if (candidate == null) return false;
        var logistics = FrontierSceneBehaviors.logistics(candidate.lease());
        if (!HivePerceptionProcess.shouldRefresh(state, logistics.operationId(), actorId, logistics.cargoPosition(), now)) return false;
        var result = FrontierV3CommandSubmission.submit(runtime, "ambient-scout-sighting", actorId.value() + "-" + candidate.lease().id().value(),
                new HotScoutOperationObserved(candidate.lease().id(), logistics.operationId(), actorId, logistics.cargoPosition(), now));
        FrontierV3DiagnosticTrace.recordScoutSighting(level.getServer(), "hot-scout-sighting:" + actorId.value(), candidate.lease(), actorId, result);
        return result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
    }

    private static boolean sameFloorAnchor(ServerLevel level, BlockPosition physicalBodyCell, BlockPosition canonicalFloorAnchor) {
        BlockPos expected = FrontierV3StandingPosition.aboveFloor(level, canonicalFloorAnchor);
        return expected != null && physicalBodyCell.x() == expected.getX() && physicalBodyCell.y() == expected.getY()
                && physicalBodyCell.z() == expected.getZ();
    }

    private record SightedCarrier(MinecartChest carrier, SceneLease lease) { }
}
