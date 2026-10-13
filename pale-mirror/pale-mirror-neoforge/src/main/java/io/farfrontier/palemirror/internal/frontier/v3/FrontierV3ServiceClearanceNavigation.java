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

    /**
     * Losing a service turn is not permission to park in its access boundary.
     * The retained activity only yields space; no item, job phase or service
     * completion is changed. Both work providers use this same protocol.
     */
    static FrontierV3GoalNavigation.Result waitForAccess(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, FrontierWorldState state,
            SubjectId settlementId, SubjectId depotId, SubjectId ownerId, SubjectId actorId,
            int phase, long generation, FrontierV3ActorActuation actuation) {
        if (!actuation.current(body))
            return new FrontierV3GoalNavigation.Result(FrontierV3GoalNavigation.Status.AMBIGUOUS,
                    "stale-body-or-execution-authority", java.util.Optional.empty(), java.util.Optional.empty());
        var boundary = ServiceAccessCoordinator.boundary(state, depotId);
        var supported = FrontierV3SupportedBodyCapture.observe(level, body);
        var observed = supported.orElseGet(() -> FrontierV3SurfaceObservation.observedBody(body));
        if (boundary.cleared(observed)) {
            // Release the occupied turn on witnessed exit, not at a later work goal.
            if (supported.isPresent() && boundary.occupied(state.actorLocations().get(actorId).body()))
                FrontierV3ActorBodyController.inspectCurrent(level, runtime, body);
            // Being outside is not a parking instruction: the exit must remain available.
            // A normal jump on the way to a buffer must not switch back to an egress goal;
            // only the occupancy receipt above requires supported-ground evidence.
            return FrontierV3ServiceApproachNavigation.waitOutside(level, body, state, depotId,
                    ownerId, actorId, phase, generation, actuation);
        }
        return pursue(level, body, state, settlementId, depotId, ownerId, actorId, phase, generation, actuation);
    }

    static FrontierV3GoalNavigation.Result pursue(ServerLevel level, Mob body, FrontierWorldState state,
            SubjectId settlementId, SubjectId depotId, SubjectId ownerId, SubjectId actorId,
            int phase, long generation, FrontierV3ActorActuation actuation) {
        var knownStations = KnownServiceExitNavigation.supportedExitStations(state, settlementId, depotId,
                FrontierV3SurfaceObservation.observedBody(body).supportingSurface());
        var stations = knownStations.stream().filter(station ->
                level.hasChunkAt(new net.minecraft.core.BlockPos(station.x(), station.y(), station.z()))
                        && FrontierV3SemanticMovement.targetGeometryIsNavigable(level, body, station)).toList();
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
