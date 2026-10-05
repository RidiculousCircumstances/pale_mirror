package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Passive presence elects courtesy movement; other activities are never displaced by this owner. */
final class FrontierV3PresenceTrafficYield {
    private record Target(FrontierV3ActorActuation authority, SurfaceAnchor station) { }
    private static final Map<Mob, Target> TARGETS = new WeakHashMap<>();
    private static final Map<Mob, Long> NEXT_QUERY = new WeakHashMap<>();
    private static final int MAX_PHYSICAL_CANDIDATES = MovementOrder.MAX_LEGAL_STATIONS;
    private FrontierV3PresenceTrafficYield() { }

    static boolean pursue(ServerLevel level, FrontierWorldState state, Mob body, AmbientActorLease lease,
                           FrontierV3ActorActuation actuation) {
        if (actuation.id().execution().activityKind() != ActorActivityKind.PRESENCE || !actuation.current(body)) {
            TARGETS.remove(body); NEXT_QUERY.remove(body); return false;
        }
        ResidentProfile resident = state.humanPopulation().resident(lease.actorId());
        if (resident == null) { TARGETS.remove(body); return false; }
        Target target = TARGETS.get(body);
        if (target != null && !target.authority().id().equals(actuation.id())) {
            TARGETS.remove(body); target = null;
        }
        var request = FrontierV3PedestrianYieldRequests.forBody(level, body, actuation.id().body());
        if (target == null && request.isEmpty()) return false;
        if (target == null || !FrontierV3SemanticMovement.targetIsNavigable(level, body, target.station())) {
            if (request.isEmpty()) { TARGETS.remove(body); return false; }
            if (level.getGameTime() < NEXT_QUERY.getOrDefault(body, 0L)) return target != null;
            NEXT_QUERY.put(body, level.getGameTime() + FrontierV3MinecraftGoalNavigation.retryIntervalTicks());
            var points = SettlementServiceAccessPoints.forSettlement(state, resident.settlementId());
            var knowledge = KnownPedestrianRouteKnowledge.forSettlement(state, resident.settlementId(), List.of());
            var excluded = new HashSet<>(ServiceDestinationClaims.excludedFor(state, lease.actorId()));
            TARGETS.entrySet().removeIf(entry -> !entry.getValue().authority().current(entry.getKey()));
            TARGETS.forEach((other, reserved) -> { if (other != body) excluded.add(reserved.station()); });
            var scope = new FrontierV3NavigationScope.ObservedWorld(state.bootstrap().bounds());
            var passage = request.orElseThrow().passage();
            int[] queries = {0};
            var destination = ServiceAreaDestinations.select(points, lease.actorId(),
                    FrontierV3SurfaceObservation.observedBody(body).supportingSurface(), knowledge, excluded,
                    station -> !passage.contains(station.support())
                            && FrontierV3SemanticMovement.targetIsNavigable(level, body, station)
                            && queries[0]++ < MAX_PHYSICAL_CANDIDATES
                            && FrontierV3GoalNavigation.canReach(level, body, FrontierV3GoalNavigation.Goal.station(station, scope)));
            if (destination.isEmpty()) return false; // No invented route and no recursive displacement chain.
            target = new Target(actuation, destination.orElseThrow());
            TARGETS.put(body, target);
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info("PMV3_TRAFFIC_YIELD actor={} requester={} target={}",
                    lease.actorId().value(), request.orElseThrow().requester(), target.station());
        }
        MovementOrder order = new MovementOrder(lease.actorId(), lease.actorId(), 0,
                actuation.id().execution().generation(), List.of(target.station()), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
        var result = FrontierV3GoalNavigation.pursue(level, body,
                FrontierV3GoalNavigation.Goal.routed(order, List.of(), state.bootstrap().bounds()), actuation);
        if (result.status() == FrontierV3GoalNavigation.Status.ARRIVED) {
            TARGETS.remove(body); NEXT_QUERY.remove(body);
        }
        // Keep this idle-only local move until real arrival, even if the requester already found a detour.
        return true;
    }
}
