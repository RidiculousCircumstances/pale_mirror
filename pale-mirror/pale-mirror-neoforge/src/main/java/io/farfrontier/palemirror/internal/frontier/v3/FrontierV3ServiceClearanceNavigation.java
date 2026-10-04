package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.List;

/** Shared physical exit provider. Families retain effects and completion; this owns no job policy. */
final class FrontierV3ServiceClearanceNavigation {
    private FrontierV3ServiceClearanceNavigation() { }

    static FrontierV3GoalNavigation.Result pursue(ServerLevel level, Mob body, FrontierWorldState state,
            SubjectId settlementId, SubjectId depotId, SubjectId ownerId, SubjectId actorId,
            int phase, long generation, FrontierV3ActorActuation actuation) {
        var knownStations = KnownServiceExitNavigation.exitStations(state, settlementId, depotId, actorId,
                FrontierV3SurfaceObservation.observedBody(body).supportingSurface());
        var stations = knownStations.stream().filter(station ->
                level.hasChunkAt(new net.minecraft.core.BlockPos(station.x(), station.y(), station.z()))
                        && FrontierV3SemanticMovement.targetIsNavigable(level, body, station)).toList();
        if (stations.isEmpty()) {
            FrontierV3GoalNavigation.stop(body, actuation);
            return new FrontierV3GoalNavigation.Result(FrontierV3GoalNavigation.Status.BLOCKED,
                    "service_exit:no-available-supported-exit candidates=" + knownStations.stream()
                    .map(station -> station.support() + ":" + (level.hasChunkAt(new net.minecraft.core.BlockPos(
                            station.x(), station.y(), station.z()))
                            ? FrontierV3SemanticMovement.detail(FrontierV3SemanticMovement.target(level, body, station))
                            : "unloaded")).toList(),
                    java.util.Optional.of(FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE), java.util.Optional.empty());
        }
        FrontierV3GoalNavigation.Result result = null;
        for (int offset = 0; offset < stations.size(); offset += MovementOrder.MAX_LEGAL_STATIONS) {
            var batch = stations.subList(offset, Math.min(stations.size(), offset + MovementOrder.MAX_LEGAL_STATIONS));
            MovementOrder order = new MovementOrder(ownerId, actorId, phase, generation, batch,
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
            result = FrontierV3GoalNavigation.pursue(level, body,
                    FrontierV3GoalNavigation.Goal.routed(order, List.of(), state.bootstrap().bounds()), actuation);
            if (result.status() != FrontierV3GoalNavigation.Status.BLOCKED) return result;
        }
        return result;
    }
}
