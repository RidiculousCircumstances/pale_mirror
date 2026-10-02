package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.List;

/** Composition of declared settlement service geometry; depots are the first provider. */
public final class SettlementServiceAccessPoints {
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
                .filter(point -> point.boundary().occupied(body)
                        || point.waitingSurfaces().contains(body.supportingSurface()))
                .findFirst();
    }
    public static List<ServiceAccessPoint> forSettlement(FrontierWorldState state, SubjectId settlementId) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId);
        return settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.DEPOT)
                .map(structure -> forDepot(state, settlement, structure)).toList();
    }
    public static ServiceAccessPoint forDepot(FrontierWorldState state, Settlement settlement, SettlementStructure depot) {
        var knowledge = KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), List.of(
                new KnownPedestrianRouteKnowledge.Passage(depot, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
        return forDepot(settlement, depot, knowledge, population(state, settlement.id()));
    }
    static int population(FrontierWorldState state, SubjectId settlementId) {
        return (int) state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId)).count();
    }
    static ServiceAccessPoint forDepot(Settlement settlement, SettlementStructure depot,
                                       KnownPedestrianRouteKnowledge knowledge, int population) {
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        SurfaceAnchor apron = port.facing().step(port.exteriorApproach(), -1);
        List<SurfaceAnchor> waiting = new ArrayList<>();
        for (int distance = 1; distance <= Math.max(4, (settlement.residents().size() + 1) / 2); distance++) {
            for (SurfaceAnchor side : List.of(port.facing().stepLeft(apron, distance), port.facing().stepRight(apron, distance))) {
                SurfaceAnchor supported = knowledge.supportAt(side.x(), side.z());
                if (!port.accessBoundary().occupied(supported.standingBody())) waiting.add(supported);
            }
        }
        // A two-dimensional temporary buffer, not a population-sized pair of parking strips.
        int radius = Math.min(16, Math.max(2, (int) Math.ceil(Math.sqrt(population))));
        for (int ring = 1; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    SurfaceAnchor supported = knowledge.supportAt(apron.x() + dx, apron.z() + dz);
                    if (port.accessBoundary().cleared(supported.standingBody()) && !waiting.contains(supported))
                        waiting.add(supported);
                }
            }
        }
        return new ServiceAccessPoint(port.depotId(), settlement.id(), port.serviceSurface(), port.accessBoundary(), waiting);
    }
}
