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
    private record Route(long leaseRevision, long goalRevision, List<SurfaceAnchor> waypoints) { }
    private static final Map<Mob, Route> ROUTES = new WeakHashMap<>();
    private static final Map<Mob, String> BLOCKED = new WeakHashMap<>();

    private FrontierV3ActorMovementNavigation() { }

    static void pursue(ServerLevel level, FrontierWorldState state, Mob body,
                       AmbientActorLease lease, ActorMovement movement) {
        if (movement == null || !(movement.context() instanceof ActorMovementContext.ServiceExit)) {
            ROUTES.remove(body);
            BLOCKED.remove(body);
            FrontierV3GoalNavigation.stop(body);
            return;
        }
        Route route = ROUTES.get(body);
        if (route == null || route.leaseRevision() != lease.revision()
                || route.goalRevision() != movement.order().goalRevision()) {
            try {
                ActorMovementContext.ServiceExit serviceExit = (ActorMovementContext.ServiceExit) movement.context();
                ResidentProfile resident = state.humanPopulation().resident(movement.order().actorId());
                if (resident == null || !resident.settlementId().equals(serviceExit.settlementId()))
                    throw new IllegalArgumentException("movement actor lacks its declared service-exit owner");
                route = new Route(lease.revision(), movement.order().goalRevision(),
                        KnownServiceExitNavigation.pathFrom(state, serviceExit.settlementId(),
                                serviceExit.depotId(), movement.order(),
                                FrontierV3SurfaceObservation.observedBody(body).supportingSurface()));
            } catch (IllegalArgumentException unavailable) {
                blocked(body, movement, "known_route:" + unavailable.getMessage());
                FrontierV3GoalNavigation.stop(body);
                return;
            }
            ROUTES.put(body, route);
        }
        if (FrontierV3SemanticMovement.arrived(level, body, route.waypoints().getLast())) {
            BLOCKED.remove(body);
            FrontierV3GoalNavigation.stop(body);
            return;
        }
        try {
            FrontierV3GoalNavigation.Result result = FrontierV3GoalNavigation.pursue(level, body,
                    FrontierV3GoalNavigation.Goal.routed(movement.order(), route.waypoints(), state.bootstrap().bounds()));
            if (result.status() == FrontierV3GoalNavigation.Status.BLOCKED)
                blocked(body, movement, "minecraft_path:" + result.reason());
            else BLOCKED.remove(body);
        } catch (IllegalArgumentException unavailable) {
            blocked(body, movement, "local_leg:" + unavailable.getMessage());
            FrontierV3GoalNavigation.stop(body);
        }
    }

    private static void blocked(Mob body, ActorMovement movement, String reason) {
        if (!reason.equals(BLOCKED.put(body, reason)))
            PaleMirrorMod.LOGGER.warn("Actor movement route waits actor={} goal={} reason={}",
                    movement.order().actorId().value(), movement.order().goalRevision(), reason);
    }
}
