package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProviders;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Physical execution of the same actor goal that remains canonical through COLD/HOT. */
final class FrontierV3ActorMovementNavigation {
    private record Route(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId actuation,
                         long leaseRevision, MovementOrder order, RouteTopology topology,
                         Map<BlockPosition, PhysicalDelta> physicalDeltas, List<SurfaceAnchor> waypoints) { }
    private static final Map<Mob, Route> ROUTES = new WeakHashMap<>();
    private record Wait(io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution,
                        MovementOrder order, String reason) { }
    private static final Map<Mob, Wait> BLOCKED = new WeakHashMap<>();
    private record Steering(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId actuation,
                            MovementOrder order, MovementPermission permission) { }
    private static final Map<Mob, Steering> STEERING = new WeakHashMap<>();

    private FrontierV3ActorMovementNavigation() { }

    static void pursue(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                       FrontierWorldState state, Mob body,
                       AmbientActorLease lease, ActorMovement movement) {
        if (movement == null) {
            ROUTES.remove(body);
            BLOCKED.remove(body);
            STEERING.remove(body);
            return;
        }
        var provider = ActorMovementProviders.require(movement);
        provider.validate(state, movement);
        var actuation = FrontierV3AmbientActuation.capture(state, runtime, body, lease, movement.executionId()).orElse(null);
        if (actuation == null || !actuation.current(body)) return;
        var service = provider.serviceApproach(state, movement);
        if (service.isPresent()) {
            var identity = service.orElseThrow();
            if (!identity.actorId().equals(movement.order().actorId())
                    || !identity.ownerId().equals(movement.order().ownerId()))
                throw new IllegalArgumentException("movement service approach lost its exact actor/owner declaration");
        }
        if (service.isPresent() && !ServiceAccessCoordinator.available(state, service.orElseThrow())) {
            var identity = service.orElseThrow();
            var port = ServiceAccessCoordinator.port(state, identity.pointId());
            var waiting = FrontierV3ServiceClearanceNavigation.waitForAccess(level, runtime, body, state,
                    port.settlementId(), identity.pointId(), identity.ownerId(), identity.actorId(),
                    Math.toIntExact(movement.order().goalOrdinal()), movement.executionId().generation(), actuation);
            blocked(body, movement, "service-access:" + waiting.reason());
            return; // Local clearance is not arrival at the retained service/work goal.
        }
        long tick = runtime.calendarInstant().orElseThrow();
        var previous = STEERING.get(body);
        var permission = provider.movementPermission(state, movement, FrontierV3SurfaceObservation.observedBody(body).supportingSurface(), tick,
                FrontierV3ActorPositionView.observed(level, state, tick),
                previous != null && previous.actuation().equals(actuation.id()) && previous.order().equals(movement.order())
                        ? previous.permission() : MovementPermission.allow());
        STEERING.put(body, new Steering(actuation.id(), movement.order(), permission));
        if (!permission.allowed()) {
            blocked(body, movement, "permission:" + permission.reason() + ":peer="
                    + permission.waitingFor().map(value -> value.value()).orElse("none"));
            FrontierV3GoalNavigation.stop(body, actuation);
            return;
        }
        Route route = ROUTES.get(body);
        if (route == null || !route.actuation().equals(actuation.id()) || route.leaseRevision() != lease.revision()
                || !route.order().equals(movement.order())
                || route.topology() != state.routeTopology() || route.physicalDeltas() != state.physicalDeltas()) {
            try {
                route = new Route(actuation.id(), lease.revision(), movement.order(), state.routeTopology(), state.physicalDeltas(),
                        provider.route(state, movement,
                                FrontierV3SurfaceObservation.observedBody(body).supportingSurface()));
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                blocked(body, movement, "known_route:" + unavailable.status()
                        + (unavailable.status() == PedestrianRouteResult.Status.PLANNING ? "" : ":" + unavailable.getMessage()));
                FrontierV3GoalNavigation.stop(body, actuation);
                return;
            }
            ROUTES.put(body, route);
        }
        if (FrontierV3SemanticMovement.arrived(level, body, route.waypoints().getLast())) {
            BLOCKED.remove(body);
            FrontierV3GoalNavigation.stop(body, actuation);
            return;
        }
        try {
            FrontierV3GoalNavigation.Result result = FrontierV3GoalNavigation.pursue(level, body,
                    FrontierV3GoalNavigation.Goal.routed(movement.order(), route.waypoints(), state.bootstrap().bounds()), actuation, permission.pace());
            if (result.status() == FrontierV3GoalNavigation.Status.BLOCKED)
                blocked(body, movement, "minecraft_path:" + result.reason());
            else BLOCKED.remove(body);
        } catch (IllegalArgumentException unavailable) {
            blocked(body, movement, "local_leg:" + unavailable.getMessage());
            FrontierV3GoalNavigation.stop(body, actuation);
        }
    }

    private static void blocked(Mob body, ActorMovement movement, String reason) {
        var wait = new Wait(movement.executionId(), movement.order(), reason);
        if (!wait.equals(BLOCKED.put(body, wait)))
            PaleMirrorMod.LOGGER.warn("Actor movement route waits actor={} goal={} reason={}",
                    movement.order().actorId().value(), movement.order().goalRevision(), reason);
    }
    /** Exact current-goal observation only; never drives movement or starts a search. */
    static java.util.Optional<String> waitReason(ServerLevel level, FrontierWorldState state, ActorMovement movement) {
        var entity = level.getEntity(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(
                state.bootstrap().worldId(), movement.order().actorId()));
        if (!(entity instanceof Mob body)) return java.util.Optional.empty();
        var wait = BLOCKED.get(body);
        return wait != null && wait.execution().equals(movement.executionId()) && wait.order().equals(movement.order())
                ? java.util.Optional.of(wait.reason()) : java.util.Optional.empty();
    }
}
