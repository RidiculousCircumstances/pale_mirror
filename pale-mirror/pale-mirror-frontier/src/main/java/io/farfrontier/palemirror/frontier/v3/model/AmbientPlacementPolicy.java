package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Closed placement Strategies. The lease purpose remains unchanged; placement awards no arrival. */
public final class AmbientPlacementPolicy {
    @FunctionalInterface private interface Strategy {
        List<SurfaceAnchor> candidates(FrontierWorldState state, AmbientActorLease lease);
        default FrontierWorldState admitted(FrontierWorldState state, AmbientActorLease lease, BodyPosition body) { return state; }
    }
    private static final Map<AmbientGoalKind, Strategy> STRATEGIES = Map.ofEntries(
            Map.entry(AmbientGoalKind.MEAL, new Strategy() {
                @Override public List<SurfaceAnchor> candidates(FrontierWorldState state, AmbientActorLease lease) {
                    return ResidentMealAdmissionPlacement.mayRelocate(state, lease.actorId())
                            ? serviceZone(state, lease) : exact(state, lease);
                }
                @Override public FrontierWorldState admitted(FrontierWorldState state, AmbientActorLease lease, BodyPosition body) {
                    return ResidentMealAdmissionPlacement.confirmed(state, lease.actorId(), body);
                }
            }),
            Map.entry(AmbientGoalKind.ACTOR_MOVEMENT, AmbientPlacementPolicy::serviceZone),
            Map.entry(AmbientGoalKind.WORK, AmbientPlacementPolicy::serviceZone),
            Map.entry(AmbientGoalKind.GUARD, AmbientPlacementPolicy::serviceZone),
            Map.entry(AmbientGoalKind.PATROL, AmbientPlacementPolicy::serviceZone),
            Map.entry(AmbientGoalKind.SCOUT_PATROL, AmbientPlacementPolicy::exact),
            Map.entry(AmbientGoalKind.TRANSIT, AmbientPlacementPolicy::exact),
            Map.entry(AmbientGoalKind.OPERATION_ASSEMBLY, AmbientPlacementPolicy::exact),
            Map.entry(AmbientGoalKind.ENGINEERING_ASSEMBLY, AmbientPlacementPolicy::exact),
            Map.entry(AmbientGoalKind.HIVE_TASK_ASSEMBLY, AmbientPlacementPolicy::exact),
            Map.entry(AmbientGoalKind.HIVE_TASK_RETURN, AmbientPlacementPolicy::exact));
    static {
        if (STRATEGIES.size() != AmbientGoalKind.values().length)
            throw new IllegalStateException("ambient placement capability registry is incomplete");
    }
    private AmbientPlacementPolicy() { }
    public static List<SurfaceAnchor> candidates(FrontierWorldState state, AmbientActorLease lease) {
        if (!lease.equals(state.ambientLeases().get(lease.actorId())) || lease.status() != AmbientLeaseStatus.PREPARED)
            throw new IllegalArgumentException("placement needs the exact prepared lease");
        return STRATEGIES.get(lease.goal()).candidates(state, lease);
    }
    public static FrontierWorldState admitted(FrontierWorldState state, AmbientActorLease previous, BodyPosition body) {
        return STRATEGIES.get(previous.goal()).admitted(state, previous, body);
    }
    private static List<SurfaceAnchor> exact(FrontierWorldState state, AmbientActorLease lease) {
        return List.of(lease.handoffBody().supportingSurface());
    }
    private static List<SurfaceAnchor> serviceZone(FrontierWorldState state, AmbientActorLease lease) {
        ResidentProfile resident = state.humanPopulation().resident(lease.actorId());
        if (resident == null) return exact(state, lease);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), resident.settlementId());
        List<SurfaceAnchor> result = new ArrayList<>(exact(state, lease));
        for (ServiceAccessPoint point : SettlementServiceAccessPoints.occupiedPoint(state, settlement.id(), lease.handoffBody()).stream().toList()) {
            SettlementStructure depot = settlement.structures().stream().filter(value -> value.id().equals(point.facilityId())).findFirst().orElseThrow();
            var knowledge = KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), List.of(
                    new KnownPedestrianRouteKnowledge.Passage(depot, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
            point.waitingSurfaces().stream().sorted(Comparator.comparingLong(surface -> distance(surface, lease.handoffBody().supportingSurface())))
                    .forEach(surface -> {
                        MovementOrder order = new MovementOrder(lease.actorId(), lease.actorId(), 0, lease.revision(),
                                List.of(surface), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
                        try {
                            knowledge.path(lease.handoffBody().supportingSurface(), order);
                            result.add(surface);
                        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { /* Not in this connected placement zone. */ }
                    });
        }
        return List.copyOf(result);
    }
    private static long distance(SurfaceAnchor first, SurfaceAnchor second) {
        return Math.abs((long) first.x() - second.x()) + Math.abs((long) first.y() - second.y()) + Math.abs((long) first.z() - second.z());
    }
}
