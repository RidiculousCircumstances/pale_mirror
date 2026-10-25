package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.List;

/** Shared physical exit provider. Families retain effects and completion; this owns no job policy. */
final class FrontierV3ServiceClearanceNavigation {
    private record Attempt(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId authority,
                           SubjectId owner, SubjectId actor, int phase, long generation,
                           FrontierV3GoalAlternatives alternatives) {
        Attempt withAlternatives(FrontierV3GoalAlternatives next) {
            return new Attempt(authority, owner, actor, phase, generation, next);
        }
    }
    private static final java.util.Map<Mob, Attempt> ATTEMPTS = new java.util.WeakHashMap<>();
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
            state = runtime.decodedState().orElseThrow();
            if (!actuation.current(body))
                return new FrontierV3GoalNavigation.Result(FrontierV3GoalNavigation.Status.AMBIGUOUS,
                        "stale-body-or-execution-authority", java.util.Optional.empty(), java.util.Optional.empty());
            // Being outside is not a parking instruction: the exit must remain available.
            // A normal jump on the way to a buffer must not switch back to an egress goal;
            // only the occupancy receipt above requires supported-ground evidence.
            return FrontierV3ServiceApproachNavigation.waitOutside(level, body, state, depotId,
                    actorId, phase, generation, actuation);
        }
        return pursue(level, body, state, settlementId, depotId, ownerId, actorId, phase, generation, actuation);
    }

    static FrontierV3GoalNavigation.Result pursue(ServerLevel level, Mob body, FrontierWorldState state,
            SubjectId settlementId, SubjectId depotId, SubjectId ownerId, SubjectId actorId,
            int phase, long generation, FrontierV3ActorActuation actuation) {
        var knownStations = KnownServiceExitNavigation.supportedExitStations(state, settlementId, depotId,
                FrontierV3SurfaceObservation.observedBody(body).supportingSurface());
        var stations = knownStations.stream().filter(station ->
                FrontierV3LoadedWorld.ready(level, new net.minecraft.core.BlockPos(station.x(), station.y(), station.z()))
                        && FrontierV3SemanticMovement.targetGeometryIsNavigable(level, body, station)).toList();
        if (stations.isEmpty()) {
            ATTEMPTS.remove(body);
            FrontierV3GoalNavigation.stop(body, actuation);
            return new FrontierV3GoalNavigation.Result(FrontierV3GoalNavigation.Status.BLOCKED,
                    "service_exit:no-available-supported-exit candidates=" + knownStations.stream()
                    .map(station -> station.support() + ":" + (FrontierV3LoadedWorld.ready(level, new net.minecraft.core.BlockPos(
                            station.x(), station.y(), station.z()))
                            ? FrontierV3SemanticMovement.detail(FrontierV3SemanticMovement.target(level, body, station))
                            : "unloaded")).toList(),
                    java.util.Optional.of(FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE), java.util.Optional.empty());
        }
        var attempt = ATTEMPTS.get(body);
        if (attempt == null || !attempt.authority().equals(actuation.id()) || !attempt.owner().equals(ownerId)
                || !attempt.actor().equals(actorId) || attempt.phase() != phase || attempt.generation() != generation
                || !attempt.alternatives().matches(stations))
            attempt = new Attempt(actuation.id(), ownerId, actorId, phase, generation, new FrontierV3GoalAlternatives(stations));
        attempt = attempt.withAlternatives(attempt.alternatives().nextPassIfExhausted());
        var batch = attempt.alternatives().batch();
        MovementOrder order = new MovementOrder(ownerId, actorId, phase, generation, batch,
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
        var goal = FrontierV3GoalNavigation.Goal.routed(order, List.of(), state.bootstrap().bounds());
        var selected = FrontierV3MinecraftGoalNavigation.selectedTarget(body,
                new FrontierV3MinecraftGoalNavigation.Demand(batch, goal.scope(), goal.order()));
        var result = FrontierV3GoalNavigation.pursue(level, body, goal, actuation);
        if (FrontierV3GoalAlternatives.reconsider(result)) {
            // Replanning starts with the remaining candidates on the next refresh, never
            // by cancelling a later batch with a new attempt at the first batch.
            var rejected = result.blockReason().filter(reason -> reason == FrontierV3GoalNavigation.BlockReason.PATH_STALLED)
                    .isPresent() && selected.isPresent() ? List.of(selected.orElseThrow()) : batch;
            attempt = attempt.withAlternatives(attempt.alternatives().reject(rejected));
        }
        if (result.status() == FrontierV3GoalNavigation.Status.ARRIVED
                || result.status() == FrontierV3GoalNavigation.Status.AMBIGUOUS) ATTEMPTS.remove(body);
        else ATTEMPTS.put(body, attempt);
        return result;
    }
}
