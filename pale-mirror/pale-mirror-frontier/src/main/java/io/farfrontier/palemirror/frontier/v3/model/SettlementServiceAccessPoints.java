package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/** Bootstrap depot geometry provider. Current service discovery belongs to ServiceBoundaryComposition. */
public final class SettlementServiceAccessPoints {
    // Rebuildable geometry only: one current world, one latest view per facility, bounded eviction.
    // Actor positions, claims, meals and permits are always read from current canonical state.
    private static FrontierBootstrap cachedBootstrap;
    private static final Map<SubjectId, Geometry> GEOMETRY = new LinkedHashMap<>();
    private static Map<SubjectId, ResidentProfile> cachedResidents;
    private static final Map<SubjectId, Integer> POPULATION = new LinkedHashMap<>();
    private static final int MAX_CACHED_FACILITIES = 64;
    private static final class Geometry {
        KnownPedestrianRouteKnowledge knowledge;
        final ServiceAccessBoundary boundary;
        final Set<SurfaceAnchor> egress;
        final Set<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region> dependencies;
        ServiceAccessPoint point;
        int population = -1;
        Geometry(KnownPedestrianRouteKnowledge knowledge, ServiceAccessBoundary boundary, Settlement settlement,
                 SettlementDepotServicePort port) {
            this.knowledge = knowledge;
            this.boundary = boundary;
            this.egress = ServiceClearanceTargets.egressRegion(boundary, knowledge);
            var apron = port.facing().step(port.exteriorApproach(), -1);
            int radius = Math.max(16, Math.max(4, (settlement.residents().size() + 1) / 2)) + 1;
            var regions = new java.util.HashSet<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region>();
            for (int x = (apron.x() - radius) >> 4; x <= (apron.x() + radius) >> 4; x++)
                for (int z = (apron.z() - radius) >> 4; z <= (apron.z() + radius) >> 4; z++)
                    regions.add(new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region(x, z));
            for (var surface : boundary.occupiedSurfaces()) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                regions.add(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region.at(surface.support().offset(dx, 0, dz)));
            dependencies = Set.copyOf(regions);
        }
    }
    private SettlementServiceAccessPoints() { }
    public static SettlementDepotServicePort depotPort(FrontierWorldState state, SubjectId settlementId) {
        return FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId).structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT)
                .findFirst().map(SettlementDepotServicePort::forDepot)
                .orElseThrow(() -> new IllegalArgumentException("settlement has no current depot"));
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
        if (prior != null && knowledge.geometry().regionalRevision().orElseThrow().matches(
                prior.knowledge.geometry().regionalRevision().orElseThrow(), prior.dependencies)) {
            prior.knowledge = knowledge; return prior;
        }
        if (GEOMETRY.size() >= MAX_CACHED_FACILITIES) GEOMETRY.clear();
        var port = SettlementDepotServicePort.forDepot(depot);
        Geometry result = new Geometry(knowledge, port.accessBoundary(), settlement, port);
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
        var preferred = new java.util.ArrayList<SurfaceAnchor>();
        for (int distance = 1; distance <= Math.max(4, (settlement.residents().size() + 1) / 2); distance++) {
            for (SurfaceAnchor side : List.of(port.facing().stepLeft(apron, distance), port.facing().stepRight(apron, distance))) {
                preferred.add(side);
            }
        }
        // Applicants must never park on the current user's escape perimeter.
        // The same geometry policy governs clearance and both HOT/COLD waiting.
        var egress = geometry.egress;
        var waiting = ServiceWaitingTargets.select(boundary, knowledge, apron, preferred, egress, population);
        return new ServiceAccessPoint(port.depotId(), settlement.id(), port.serviceSurface(), boundary, waiting, egress);
    }
}
