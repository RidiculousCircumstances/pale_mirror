package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Family-owned access policy; shared navigation owns geometry, calculation and proof validation. */
public final class BakeryAdmissionNavigation {
    private BakeryAdmissionNavigation() { }
    private record Access(SurfaceAnchor origin, KnownPedestrianRouteKnowledge knowledge, Set<SurfaceAnchor> destinations) { }
    private static Access access(FrontierWorldState state, ProductionJob job) {
        if (job.bakeryWork().isEmpty() || job.bakeryWork().orElseThrow().phase() != BakeryWorkState.Phase.DEPOT_PICKUP)
            throw new IllegalArgumentException("bakery admission access requires a fresh selected station job");
        if (!ResourceStorageSelection.containers(state, job.settlementId(), "minecraft:wheat", Set.of(ResourceStorageRole.WAREHOUSE))
                    .contains(BakeryStorage.source(job))
                || !ResourceStorageSelection.containers(state, job.settlementId(), "minecraft:bread", Set.of(ResourceStorageRole.WAREHOUSE))
                    .contains(BakeryStorage.destination(job)))
            throw new IllegalArgumentException("bakery admission has a missing, fenced or incompatible warehouse");
        var source = ResourceStorageAccess.at(state, BakeryStorage.source(job)).endpoint();
        var destination = ResourceStorageAccess.at(state, BakeryStorage.destination(job)).endpoint();
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId());
        var facility = home.structures().stream().filter(value -> value.id().equals(job.facilityId())).findFirst().orElseThrow();
        var origin = SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach();
        return new Access(origin, ResourceStorageNavigation.forJourney(state, job.settlementId(), List.of(source, destination)),
                new HashSet<>(List.of(source.station(), destination.station())));
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
