package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Closed placement Strategies. The lease purpose remains unchanged; placement awards no arrival. */
public final class AmbientPlacementPolicy {
    @FunctionalInterface private interface Strategy {
        List<SurfaceAnchor> candidates(FrontierWorldState state, AmbientActorLease lease);
        default Optional<PedestrianRouteReceipt> selectedRoute(FrontierWorldState state, AmbientActorLease lease, SurfaceAnchor surface) {
            requireExact(lease, surface); return Optional.empty();
        }
        default void requireSelected(FrontierWorldState state, AmbientActorLease lease, SurfaceAnchor surface, PedestrianRouteReceipt route) {
            throw new IllegalArgumentException("this placement capability admits only the exact retained body");
        }
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
                @Override public Optional<PedestrianRouteReceipt> selectedRoute(FrontierWorldState state, AmbientActorLease lease, SurfaceAnchor surface) {
                    if (!ResidentMealAdmissionPlacement.mayRelocate(state, lease.actorId())) { requireExact(lease, surface); return Optional.empty(); }
                    return selectedServiceRoute(state, lease, surface);
                }
                @Override public void requireSelected(FrontierWorldState state, AmbientActorLease lease, SurfaceAnchor surface, PedestrianRouteReceipt route) {
                    if (!ResidentMealAdmissionPlacement.mayRelocate(state, lease.actorId()))
                        throw new IllegalArgumentException("the meal's physical step forbids relocation");
                    requireServiceRoute(state, lease, surface, route);
                }
            }),
            Map.entry(AmbientGoalKind.ACTOR_MOVEMENT, new Strategy() {
                @Override public List<SurfaceAnchor> candidates(FrontierWorldState state, AmbientActorLease lease) { return movementZone(state, lease); }
                @Override public Optional<PedestrianRouteReceipt> selectedRoute(FrontierWorldState state, AmbientActorLease lease, SurfaceAnchor surface) {
                    var local = movementKnowledge(state, lease).localPlacementRoutes(lease.handoffBody().supportingSurface()).get(surface);
                    return local == null ? selectedServiceRoute(state, lease, surface) : Optional.of(new PedestrianRouteReceipt(local));
                }
                @Override public void requireSelected(FrontierWorldState state, AmbientActorLease lease, SurfaceAnchor surface, PedestrianRouteReceipt route) {
                    var scope = LocalNavigationEnvelope.around(lease.handoffBody(), lease.handoffBody());
                    if (route.route().stream().allMatch(value -> scope.contains(value.support())))
                        movementKnowledge(state, lease).requireLocalPlacementRoute(route);
                    else requireServiceRoute(state, lease, surface, route);
                }
            }),
            Map.entry(AmbientGoalKind.WORK, new ServiceStrategy()),
            Map.entry(AmbientGoalKind.GUARD, new ServiceStrategy()),
            Map.entry(AmbientGoalKind.PATROL, new ServiceStrategy()),
            Map.entry(AmbientGoalKind.SCOUT_PATROL, AmbientPlacementPolicy::exact),
            Map.entry(AmbientGoalKind.TRANSIT, AmbientPlacementPolicy::exact),
            Map.entry(AmbientGoalKind.ENGINEERING_ASSEMBLY, AmbientPlacementPolicy::exact),
            Map.entry(AmbientGoalKind.HIVE_TASK_ASSEMBLY, AmbientPlacementPolicy::exact),
            Map.entry(AmbientGoalKind.HIVE_TASK_RETURN, AmbientPlacementPolicy::exact));
    static {
        if (STRATEGIES.size() != AmbientGoalKind.values().length)
            throw new IllegalStateException("ambient placement capability registry is incomplete");
    }
    private AmbientPlacementPolicy() { }
    private static final class ServiceStrategy implements Strategy {
        @Override public List<SurfaceAnchor> candidates(FrontierWorldState state, AmbientActorLease lease) { return serviceZone(state, lease); }
        @Override public Optional<PedestrianRouteReceipt> selectedRoute(FrontierWorldState state, AmbientActorLease lease, SurfaceAnchor surface) {
            return selectedServiceRoute(state, lease, surface);
        }
        @Override public void requireSelected(FrontierWorldState state, AmbientActorLease lease, SurfaceAnchor surface, PedestrianRouteReceipt route) {
            requireServiceRoute(state, lease, surface, route);
        }
    }
    public static List<SurfaceAnchor> candidates(FrontierWorldState state, AmbientActorLease lease) {
        if (!lease.equals(state.ambientLeases().get(lease.actorId())) || lease.status() != AmbientLeaseStatus.PREPARED)
            throw new IllegalArgumentException("placement needs the exact prepared lease");
        return STRATEGIES.get(lease.goal()).candidates(state, lease);
    }
    public static FrontierWorldState admitted(FrontierWorldState state, AmbientActorLease previous, BodyPosition body) {
        return STRATEGIES.get(previous.goal()).admitted(state, previous, body);
    }
    /** Producer-owned selected proof. The body controller separately witnesses physical birth/position. */
    public static Optional<PedestrianRouteReceipt> selectedRoute(FrontierWorldState state, AmbientActorLease lease, SurfaceAnchor surface) {
        if (!lease.equals(state.ambientLeases().get(lease.actorId())) || lease.status() != AmbientLeaseStatus.PREPARED)
            throw new IllegalArgumentException("placement needs the exact prepared lease");
        if (surface.equals(lease.handoffBody().supportingSurface())) return Optional.empty();
        return STRATEGIES.get(lease.goal()).selectedRoute(state, lease, surface);
    }
    /** Event/replay validation: declared capability and selected geometry only; never candidate search. */
    public static void requireSelected(FrontierWorldState state, AmbientActorLease lease, SurfaceAnchor surface,
                                       Optional<PedestrianRouteReceipt> receipt) {
        if (surface.equals(lease.handoffBody().supportingSurface())) {
            if (receipt.isPresent()) throw new IllegalArgumentException("exact body placement cannot carry a relocation route");
            return;
        }
        var route = receipt.orElseThrow(() -> new IllegalArgumentException("relocated body lacks its selected access proof"));
        if (!route.route().getFirst().equals(lease.handoffBody().supportingSurface()) || !route.route().getLast().equals(surface))
            throw new IllegalArgumentException("body placement has foreign route endpoints");
        STRATEGIES.get(lease.goal()).requireSelected(state, lease, surface, route);
    }
    private static void requireExact(AmbientActorLease lease, SurfaceAnchor surface) {
        if (!surface.equals(lease.handoffBody().supportingSurface())) throw new IllegalArgumentException("placement is not the exact retained body");
    }
    private static KnownPedestrianRouteKnowledge movementKnowledge(FrontierWorldState state, AmbientActorLease lease) {
        var movement = state.actorMovements().get(lease.actorId());
        if (movement == null) throw new IllegalArgumentException("movement placement lost its exact movement");
        return io.farfrontier.palemirror.frontier.v3.process.ActorMovementProviders.require(movement).placementKnowledge(state, movement);
    }
    private static List<SurfaceAnchor> exact(FrontierWorldState state, AmbientActorLease lease) {
        return List.of(lease.handoffBody().supportingSurface());
    }
    private static List<SurfaceAnchor> movementZone(FrontierWorldState state, AmbientActorLease lease) {
        var result = new ArrayList<>(serviceZone(state, lease));
        var movement = state.actorMovements().get(lease.actorId());
        if (movement != null) {
            var knowledge = io.farfrontier.palemirror.frontier.v3.process.ActorMovementProviders.require(movement)
                    .placementKnowledge(state, movement);
            try {
                for (var candidate : knowledge.localPlacement(lease.handoffBody().supportingSurface()))
                    if (!result.contains(candidate)) result.add(candidate);
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                // Unknown or changed support does not authorize invented neighbouring geometry.
            }
        }
        return List.copyOf(result);
    }
    private static List<SurfaceAnchor> serviceZone(FrontierWorldState state, AmbientActorLease lease) {
        ResidentProfile resident = state.humanPopulation().resident(lease.actorId());
        if (resident == null) return exact(state, lease);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), resident.settlementId());
        List<SurfaceAnchor> result = new ArrayList<>(exact(state, lease));
        for (ServiceAccessPoint point : SettlementServiceAccessPoints.placementPoint(state, settlement.id(), lease.handoffBody()).stream().toList()) {
            SettlementStructure depot = settlement.structures().stream().filter(value -> value.id().equals(point.facilityId())).findFirst().orElseThrow();
            var knowledge = KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), List.of(
                    new KnownPedestrianRouteKnowledge.Passage(depot, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
            for (var surface : point.waitingSurfaces().stream().sorted(Comparator.comparingLong(
                    value -> distance(value, lease.handoffBody().supportingSurface()))).toList()) {
                MovementOrder order = new MovementOrder(lease.actorId(), lease.actorId(), 0, lease.revision(),
                        List.of(surface), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
                try {
                    knowledge.plannedPath(lease.handoffBody().supportingSurface(), order);
                    if (!result.contains(surface)) result.add(surface);
                } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                    if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
                }
            }
        }
        return List.copyOf(result);
    }
    private static ServiceAccessPoint requireServicePoint(FrontierWorldState state, AmbientActorLease lease, SurfaceAnchor surface) {
        var resident = state.humanPopulation().resident(lease.actorId());
        if (resident == null) throw new IllegalArgumentException("service placement lacks its exact resident");
        var point = SettlementServiceAccessPoints.placementPoint(state, resident.settlementId(), lease.handoffBody())
                .orElseThrow(() -> new IllegalArgumentException("body placement is outside a declared service zone"));
        if (!point.waitingSurfaces().contains(surface)) throw new IllegalArgumentException("body placement is not a declared waiting surface");
        return point;
    }
    private static KnownPedestrianRouteKnowledge serviceKnowledge(FrontierWorldState state, AmbientActorLease lease, ServiceAccessPoint point) {
        var resident = state.humanPopulation().resident(lease.actorId());
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), resident.settlementId());
        var facility = settlement.structures().stream().filter(value -> value.id().equals(point.facilityId())).findFirst().orElseThrow();
        return KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), List.of(
                new KnownPedestrianRouteKnowledge.Passage(facility, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
    }
    private static Optional<PedestrianRouteReceipt> selectedServiceRoute(FrontierWorldState state, AmbientActorLease lease, SurfaceAnchor surface) {
        var point = requireServicePoint(state, lease, surface);
        var order = new MovementOrder(lease.actorId(), lease.actorId(), 0, lease.revision(), List.of(surface),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        return Optional.of(new PedestrianRouteReceipt(serviceKnowledge(state, lease, point).plannedPath(lease.handoffBody().supportingSurface(), order)));
    }
    private static void requireServiceRoute(FrontierWorldState state, AmbientActorLease lease, SurfaceAnchor surface, PedestrianRouteReceipt route) {
        serviceKnowledge(state, lease, requireServicePoint(state, lease, surface)).requireRoute(route.route());
    }
    private static long distance(SurfaceAnchor first, SurfaceAnchor second) {
        return Math.abs((long) first.x() - second.x()) + Math.abs((long) first.y() - second.y()) + Math.abs((long) first.z() - second.z());
    }
}
