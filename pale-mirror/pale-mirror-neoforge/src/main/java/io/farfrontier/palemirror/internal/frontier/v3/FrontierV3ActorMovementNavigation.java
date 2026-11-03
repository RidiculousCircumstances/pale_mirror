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
                         Map<BlockPosition, PhysicalDelta> physicalDeltas, List<SurfaceAnchor> waypoints, long retryHintsAt, boolean waitingForService) { }
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
            FrontierV3ServiceApproachNavigation.forget(body);
            return;
        }
        var provider = ActorMovementProviders.require(movement);
        provider.validate(state, movement);
        var actuation = FrontierV3AmbientActuation.capture(state, runtime, body, lease, movement.executionId()).orElse(null);
        if (actuation == null || !actuation.current(body)) return;
        var service = provider.serviceApproach(state, movement);
        if (service.isPresent()) {
            var identity = service.orElseThrow();
            ServiceAccessCoordinator.requireDeclared(state, identity);
            if (!identity.actorId().equals(movement.order().actorId()))
                throw new IllegalArgumentException("movement service approach lost its exact actor/owner declaration");
        }
        boolean waitingForService = service.isPresent() && !ServiceAccessCoordinator.available(state, service.orElseThrow());
        if (waitingForService) {
            // The hint cache keys service admission; a grant replans from the actual waiting pocket.
            var identity = service.orElseThrow();
            var point = ServiceBoundaryComposition.declaration(state, identity.pointId());
            var waiting = FrontierV3ServiceClearanceNavigation.waitForAccess(level, runtime, body, state,
                    point.settlementId(), point.pointId(), movement.order().ownerId(), identity.actorId(),
                    Math.toIntExact(movement.order().goalOrdinal()), movement.executionId().generation(), actuation);
            blocked(body, movement, "service-access:" + waiting.reason());
            return; // Local clearance is not arrival at the retained service/work goal.
        }
        FrontierV3ServiceApproachNavigation.forget(body);
        long tick = runtime.calendarInstant().orElseThrow();
        FrontierV3MovementPositionIngress.observe(level, runtime, state, movement, tick);
        state = runtime.decodedState().orElseThrow();
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
        // Owners may request bounded local latitude; only this common path actuates it.
        // Reaching the correction never completes the retained semantic order.
        var correction=FrontierV3LocalGoalCorrection.current(body,actuation,movement.order());
        if(correction.isPresent()){
            var result=FrontierV3GoalNavigation.pursueCorrection(level,body,correction.orElseThrow(),actuation,permission);
            if(result.status()==FrontierV3GoalNavigation.Status.BLOCKED)blocked(body,movement,"local-correction:"+result.reason());
            else BLOCKED.remove(body);
            return;
        }
        var hint = routeHint(state, body, actuation);
        if (movement.order().legalStations().stream().anyMatch(station -> FrontierV3SemanticMovement.arrived(level, body, station))) {
            BLOCKED.remove(body);
            FrontierV3GoalNavigation.stop(body, actuation);
            return;
        }
        try {
            FrontierV3GoalNavigation.Result result = FrontierV3GoalNavigation.pursue(level, body,
                    FrontierV3GoalNavigation.Goal.routed(movement.order(), hint, state.bootstrap().bounds()), actuation, permission);
            if (result.status() == FrontierV3GoalNavigation.Status.BLOCKED)
                blocked(body, movement, "minecraft_path:" + result.reason());
            else BLOCKED.remove(body);
        } catch (IllegalArgumentException invalidContract) {
            // Operational failures are typed BLOCKED/AMBIGUOUS results. Invalid
            // goals/authority are defects, not permission for an endless retry.
            throw new IllegalStateException("HOT navigation contract failed actor=" + movement.order().actorId().value()
                    + " owner=" + movement.order().ownerId().value() + " goal=" + movement.order().goalRevision()
                    + " execution=" + movement.executionId(), invalidContract);
        }
    }

    /** The same retained owner route also guides a temporary service-buffer approach. */
    static List<SurfaceAnchor> routeHint(FrontierWorldState state, Mob body, FrontierV3ActorActuation actuation) {
        if (!actuation.current(body)) throw new IllegalArgumentException("route hint requires current actor authority");
        var movement = state.actorMovements().get(actuation.id().execution().actorId());
        if (movement == null) return List.of();
        if (!movement.executionId().equals(actuation.id().execution()))
            throw new IllegalArgumentException("route hint cannot borrow a competing execution");
        var provider = ActorMovementProviders.require(movement); provider.validate(state, movement);
        var lease = state.ambientLeases().get(movement.order().actorId());
        long leaseRevision = lease == null ? 0 : lease.revision();
        var service = provider.serviceApproach(state, movement);
        boolean waiting = service.isPresent() && !ServiceAccessCoordinator.available(state, service.orElseThrow());
        // An asynchronous PLANNING hint is not a permanent empty route. Once native
        // steering blocks, reread it at the shared retry cadence without cancelling
        // a progressing path or forcing a synchronous world search.
        Route route = ROUTES.get(body);
        if (route == null || !route.actuation().equals(actuation.id()) || route.leaseRevision() != leaseRevision
                || !route.order().equals(movement.order()) || route.waitingForService() != waiting
                || route.topology() != state.routeTopology() || route.physicalDeltas() != state.physicalDeltas()
                || route.waypoints().isEmpty() && BLOCKED.containsKey(body) && body.level().getGameTime() >= route.retryHintsAt()) {
            var basis = state;
            route = new Route(actuation.id(), leaseRevision, movement.order(), state.routeTopology(), state.physicalDeltas(),
                    FrontierV3NativeRouteHint.optional(() -> provider.route(basis, movement,
                            FrontierV3SurfaceObservation.observedBody(body).supportingSurface())),
                    body.level().getGameTime() + FrontierV3MinecraftGoalNavigation.retryIntervalTicks(), waiting);
            ROUTES.put(body, route);
        }
        return route.waypoints();
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
    /** The actual latched steering decision, not a second hypothetical permission calculation. */
    static java.util.Optional<MovementPermission> currentPermission(ServerLevel level, FrontierWorldState state, ActorMovement movement) {
        var entity = level.getEntity(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(
                state.bootstrap().worldId(), movement.order().actorId()));
        if (!(entity instanceof Mob body)) return java.util.Optional.empty();
        var steering = STEERING.get(body);
        return steering != null && ActorBodyAuthority.retainsPhysicalCustody(state, movement.order().actorId())
                && steering.actuation().body().equals(ActorBodyAuthority.current(state, movement.order().actorId()))
                && FrontierV3ActorBodyController.readyForExecution(level, state, List.of(steering.actuation().body()))
                && steering.actuation().execution().equals(movement.executionId())
                && steering.order().equals(movement.order()) ? java.util.Optional.of(steering.permission()) : java.util.Optional.empty();
    }
}
