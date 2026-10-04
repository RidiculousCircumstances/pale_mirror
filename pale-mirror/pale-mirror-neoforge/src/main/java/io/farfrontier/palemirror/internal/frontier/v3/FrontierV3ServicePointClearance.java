package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Temporary clearance of a service point by an idle actor, not a new activity or return job. */
final class FrontierV3ServicePointClearance {
    private record Target(long revision, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId actuation,
                          List<SurfaceAnchor> surfaces) { }
    private static final Map<Mob, Target> TARGETS = new WeakHashMap<>();
    private FrontierV3ServicePointClearance() { }
    static boolean pursue(ServerLevel level, FrontierWorldState state, Mob body, AmbientActorLease lease,
                           FrontierV3ActorActuation actuation) {
        if (!actuation.current(body)) return false;
        ResidentProfile resident = state.humanPopulation().resident(lease.actorId());
        if (resident == null) return false;
        BodyPosition observed = FrontierV3BodyObservation.position(body);
        List<ServiceAccessPoint> points = SettlementServiceAccessPoints.forSettlement(state, resident.settlementId());
        ServiceAccessPoint point = points.stream().filter(candidate -> ServiceAreaDestinations.temporary(
                candidate, observed.supportingSurface())).findFirst().orElse(null);
        Target target = TARGETS.get(body);
        if (target != null && (target.revision() != lease.revision() || !target.actuation().equals(actuation.id()))) {
            TARGETS.remove(body); target = null;
        }
        if (point == null && target == null) return false;
        if (target == null || target.revision() != lease.revision()
                || target.surfaces().stream().noneMatch(surface -> FrontierV3SemanticMovement.targetIsNavigable(level, body, surface))) {
            if (point == null) { TARGETS.remove(body); return false; }
            Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), point.settlementId());
            SettlementStructure facility = settlement.structures().stream()
                    .filter(value -> value.id().equals(point.facilityId())).findFirst().orElseThrow();
            var knowledge = KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), List.of(
                    new KnownPedestrianRouteKnowledge.Passage(facility,
                            KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
            List<SurfaceAnchor> surfaces = ServiceAreaDestinations.select(points, lease.actorId(),
                    observed.supportingSurface(), knowledge, ServiceDestinationClaims.excludedFor(state, lease.actorId()),
                    surface -> FrontierV3SemanticMovement.targetIsNavigable(level, body, surface))
                    .map(List::of).orElse(List.of());
            if (surfaces.isEmpty()) {
                FrontierV3GoalNavigation.stop(body, actuation);
                FrontierV3PhysicalWaitTrace.actor(body, state, lease.actorId(), "service-clearance:no-safe-waiting-position");
                return true;
            }
            target = new Target(lease.revision(), actuation.id(), surfaces);
            TARGETS.put(body, target);
        }
        MovementOrder order = new MovementOrder(lease.actorId(), lease.actorId(), 0, lease.revision(), target.surfaces(),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
        var result = FrontierV3GoalNavigation.pursue(level, body,
                FrontierV3GoalNavigation.Goal.routed(order, List.of(), state.bootstrap().bounds()), actuation);
        if (result.status() == FrontierV3GoalNavigation.Status.ARRIVED) TARGETS.remove(body);
        return true;
    }
}
