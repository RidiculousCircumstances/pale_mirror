package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Physical execution of the same actor goal that remains canonical through COLD/HOT. */
final class FrontierV3ActorMovementNavigation {
    private record Route(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId actuation,
                         long leaseRevision, long goalRevision, RouteTopology topology,
                         Map<BlockPosition, PhysicalDelta> physicalDeltas, List<SurfaceAnchor> waypoints) { }
    private static final Map<Mob, Route> ROUTES = new WeakHashMap<>();
    private static final Map<Mob, String> BLOCKED = new WeakHashMap<>();

    private FrontierV3ActorMovementNavigation() { }

    static void pursue(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                       FrontierWorldState state, Mob body,
                       AmbientActorLease lease, ActorMovement movement) {
        if (movement == null) {
            ROUTES.remove(body);
            BLOCKED.remove(body);
            return;
        }
        if (!(movement.context() instanceof ActorMovementContext.ServiceExit))
            throw new IllegalArgumentException("service-exit navigator requires its declared movement context");
        var actuation = FrontierV3AmbientActuation.capture(state, runtime, body, lease, movement.executionId()).orElse(null);
        if (actuation == null || !actuation.current(body)) return;
        Route route = ROUTES.get(body);
        if (route == null || !route.actuation().equals(actuation.id()) || route.leaseRevision() != lease.revision()
                || route.goalRevision() != movement.order().goalRevision()
                || route.topology() != state.routeTopology() || route.physicalDeltas() != state.physicalDeltas()) {
            try {
                ActorMovementContext.ServiceExit serviceExit = (ActorMovementContext.ServiceExit) movement.context();
                ResidentProfile resident = state.humanPopulation().resident(movement.order().actorId());
                if (resident == null || !resident.settlementId().equals(serviceExit.settlementId()))
                    throw new IllegalArgumentException("movement actor lacks its declared service-exit owner");
                route = new Route(actuation.id(), lease.revision(), movement.order().goalRevision(), state.routeTopology(), state.physicalDeltas(),
                        KnownServiceExitNavigation.pathFrom(state, serviceExit.settlementId(),
                                serviceExit.depotId(), movement.order(),
                                FrontierV3SurfaceObservation.observedBody(body).supportingSurface()));
            } catch (IllegalArgumentException unavailable) {
                blocked(body, movement, "known_route:" + unavailable.getMessage());
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
                    FrontierV3GoalNavigation.Goal.routed(movement.order(), route.waypoints(), state.bootstrap().bounds()), actuation);
            if (result.status() == FrontierV3GoalNavigation.Status.BLOCKED)
                blocked(body, movement, "minecraft_path:" + result.reason());
            else BLOCKED.remove(body);
        } catch (IllegalArgumentException unavailable) {
            blocked(body, movement, "local_leg:" + unavailable.getMessage());
            FrontierV3GoalNavigation.stop(body, actuation);
        }
    }

    private static void blocked(Mob body, ActorMovement movement, String reason) {
        if (!reason.equals(BLOCKED.put(body, reason)))
            PaleMirrorMod.LOGGER.warn("Actor movement route waits actor={} goal={} reason={}",
                    movement.order().actorId().value(), movement.order().goalRevision(), reason);
    }
}
