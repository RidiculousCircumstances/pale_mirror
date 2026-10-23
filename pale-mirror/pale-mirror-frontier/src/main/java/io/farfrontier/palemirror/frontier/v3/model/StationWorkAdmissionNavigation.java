package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Family-owned access policy; shared navigation owns geometry, calculation and proof validation. */
public final class StationWorkAdmissionNavigation {
    private StationWorkAdmissionNavigation() { }
    private record Access(SurfaceAnchor origin, KnownPedestrianRouteKnowledge knowledge, Set<SurfaceAnchor> destinations) { }
    private static Access access(FrontierWorldState state, ProductionJob job) {
        if (job.stationWork().isEmpty() || !job.stationWork().orElseThrow().phase().sourcePickup())
            throw new IllegalArgumentException("bakery admission access requires a fresh selected station job");
        if (!ProductionInputAccess.eligible(state, StationWorkStorage.source(job), job.settlementId(), job.facilityId(), job.stationWork().orElseThrow().recipeId())
                || !ResourceStorageSelection.containers(state, job.settlementId(), job.outputItemKind(), Set.of(ResourceStorageRole.WAREHOUSE))
                    .contains(StationWorkStorage.destination(job)))
            throw new IllegalArgumentException("bakery admission has a missing, fenced or incompatible warehouse");
        var source = ProductionInputAccess.at(state, StationWorkStorage.source(job)).endpoint();
        var destination = ResourceStorageAccess.at(state, StationWorkStorage.destination(job)).endpoint();
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId());
        var facility = home.structures().stream().filter(value -> value.id().equals(job.facilityId())).findFirst().orElseThrow();
        var origin = SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach();
        var endpoints = new ArrayList<ShipmentEndpoint>(List.of(source, destination));
        job.stationWork().orElseThrow().maintenanceTarget().ifPresent(target ->
                endpoints.add(ResourceStorageAccess.at(state, target.sourceContainerId()).endpoint()));
        return new Access(origin, ResourceStorageNavigation.forJourney(state, job.settlementId(), endpoints),
                endpoints.stream().map(ShipmentEndpoint::station).collect(java.util.stream.Collectors.toSet()));
    }
    public static PedestrianAccessReceipt propose(FrontierWorldState state, ProductionJob job) {
        var access = access(state, job); var routes = new LinkedHashMap<SurfaceAnchor, Optional<PedestrianRouteReceipt>>();
        for (var destination : access.destinations().stream().sorted(Comparator.comparingInt(SurfaceAnchor::x)
                .thenComparingInt(SurfaceAnchor::y).thenComparingInt(SurfaceAnchor::z)).toList()) {
            var route = access.knowledge().plannedBetween(access.origin(), destination);
            routes.put(destination, route.size() > 1 ? Optional.of(new PedestrianRouteReceipt(route)) : Optional.empty());
        }
        return new PedestrianAccessReceipt(access.origin(), routes);
    }
    /** Access is not actor arrival; actual moves still enter UAE and their registered movement provider. */
    public static void require(FrontierWorldState state, ProductionJob job, PedestrianAccessReceipt receipt) {
        var access = access(state, job);
        access.knowledge().requireAccess(receipt, access.origin(), access.destinations());
    }
}
