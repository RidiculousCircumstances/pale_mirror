package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/** Composition of declared settlement service geometry; depots are the first provider. */
public final class SettlementServiceAccessPoints {
    // Rebuildable geometry only: one current world, one latest view per facility, bounded eviction.
    // Actor positions, claims, meals and permits are always read from current canonical state.
    private static FrontierBootstrap cachedBootstrap;
    private static final Map<SubjectId, Geometry> GEOMETRY = new LinkedHashMap<>();
    private static Map<SubjectId, ResidentProfile> cachedResidents;
    private static final Map<SubjectId, Integer> POPULATION = new LinkedHashMap<>();
    private static final int MAX_CACHED_FACILITIES = 64;
    private static final class Geometry {
        final KnownPedestrianRouteKnowledge knowledge;
        final ServiceAccessBoundary boundary;
        final Set<SurfaceAnchor> egress;
        ServiceAccessPoint point;
        int population = -1;
        Geometry(KnownPedestrianRouteKnowledge knowledge, ServiceAccessBoundary boundary) {
            this.knowledge = knowledge;
            this.boundary = boundary;
            this.egress = ServiceClearanceTargets.egressRegion(boundary, knowledge);
        }
    }
    private SettlementServiceAccessPoints() { }
    public static SettlementDepotServicePort depotPort(FrontierWorldState state, SubjectId settlementId) {
        return FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId).structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT)
                .findFirst().map(SettlementDepotServicePort::forDepot)
                .orElseThrow(() -> new IllegalArgumentException("settlement has no current depot"));
    }
    public static boolean occupancyChanged(FrontierWorldState state, SubjectId settlementId, BodyPosition previous, BodyPosition observed) {
        if (previous.equals(observed)) return false;
        return FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId).structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT)
                .map(SettlementDepotServicePort::forDepot).map(SettlementDepotServicePort::accessBoundary)
                .anyMatch(boundary -> boundary.occupied(previous) || boundary.occupied(observed));
    }
    public static java.util.Optional<ServiceAccessPoint> occupiedPoint(FrontierWorldState state, SubjectId settlementId, BodyPosition body) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId);
        return settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.DEPOT)
                .filter(structure -> SettlementDepotServicePort.forDepot(structure).accessBoundary().occupied(body))
                .findFirst().map(structure -> forDepot(state, settlement, structure));
    }
    /** Admission may resume at either the service boundary or one of its declared waiting spots. */
    public static java.util.Optional<ServiceAccessPoint> placementPoint(FrontierWorldState state,
            SubjectId settlementId, BodyPosition body) {
        return forSettlement(state, settlementId).stream()
                .filter(point -> ServiceAreaDestinations.temporary(point, body.supportingSurface()))
                .findFirst();
    }
    public static List<ServiceAccessPoint> forSettlement(FrontierWorldState state, SubjectId settlementId) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId);
        return settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.DEPOT)
                .map(structure -> forDepot(state, settlement, structure)).toList();
    }
    public static synchronized ServiceAccessPoint forDepot(FrontierWorldState state, Settlement settlement, SettlementStructure depot) {
        Geometry geometry = geometry(state, settlement, depot);
        int population = population(state, settlement.id());
        if (geometry.point == null || geometry.population != population) {
            geometry.point = compile(settlement, depot, geometry, population);
            geometry.population = population;
        }
        return geometry.point;
    }

    /** Availability needs only the escape perimeter, never a population-sized waiting area. */
    public static synchronized Set<SurfaceAnchor> egress(FrontierWorldState state, SettlementDepotServicePort port) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), port.settlementId());
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.id().equals(port.depotId())).findFirst().orElseThrow();
        if (!SettlementDepotServicePort.forDepot(depot).equals(port))
            throw new IllegalArgumentException("service geometry differs from declared port");
        return geometry(state, settlement, depot).egress;
    }

    private static Geometry geometry(FrontierWorldState state, Settlement settlement, SettlementStructure depot) {
        if (cachedBootstrap != state.bootstrap()) {
            GEOMETRY.clear(); POPULATION.clear(); cachedResidents = null;
            cachedBootstrap = state.bootstrap();
        }
        var knowledge = KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), List.of(
                new KnownPedestrianRouteKnowledge.Passage(depot, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
        Geometry prior = GEOMETRY.get(depot.id());
        if (prior != null && prior.knowledge == knowledge) return prior;
        if (GEOMETRY.size() >= MAX_CACHED_FACILITIES) GEOMETRY.clear();
        Geometry result = new Geometry(knowledge, SettlementDepotServicePort.forDepot(depot).accessBoundary());
        GEOMETRY.put(depot.id(), result);
        return result;
    }

    static synchronized int population(FrontierWorldState state, SubjectId settlementId) {
        if (cachedResidents != state.humanPopulation().residents()) {
            POPULATION.clear();
            state.humanPopulation().residents().values().forEach(resident ->
                    POPULATION.merge(resident.settlementId(), 1, Integer::sum));
            cachedResidents = state.humanPopulation().residents();
        }
        return POPULATION.getOrDefault(settlementId, 0);
    }
    private static ServiceAccessPoint compile(Settlement settlement, SettlementStructure depot,
                                             Geometry geometry, int population) {
        var knowledge = geometry.knowledge;
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        ServiceAccessBoundary boundary = geometry.boundary;
        SurfaceAnchor apron = port.facing().step(port.exteriorApproach(), -1);
        Set<SurfaceAnchor> waiting = new LinkedHashSet<>();
        for (int distance = 1; distance <= Math.max(4, (settlement.residents().size() + 1) / 2); distance++) {
            for (SurfaceAnchor side : List.of(port.facing().stepLeft(apron, distance), port.facing().stepRight(apron, distance))) {
                SurfaceAnchor supported = knowledge.supportAt(side.x(), side.z());
                if (!boundary.occupied(supported.standingBody())) waiting.add(supported);
            }
        }
        // A two-dimensional temporary buffer, not a population-sized pair of parking strips.
        int radius = Math.min(16, Math.max(2, (int) Math.ceil(Math.sqrt(population))));
        for (int ring = 1; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    SurfaceAnchor supported = knowledge.supportAt(apron.x() + dx, apron.z() + dz);
                    if (boundary.cleared(supported.standingBody()))
                        waiting.add(supported);
                }
            }
        }
        // Applicants must never park on the current user's escape perimeter.
        // The same geometry policy governs clearance and both HOT/COLD waiting.
        var egress = geometry.egress;
        waiting.removeIf(surface -> egress.contains(surface) || !knowledge.traversable(List.of(surface)));
        return new ServiceAccessPoint(port.depotId(), settlement.id(), port.serviceSurface(), boundary, List.copyOf(waiting), egress);
    }
}
