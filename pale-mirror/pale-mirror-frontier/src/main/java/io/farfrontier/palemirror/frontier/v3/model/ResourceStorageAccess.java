package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Retained container relationship resolves its owning access capability, never an assumed depot. */
public record ResourceStorageAccess(ShipmentEndpoint endpoint, List<SurfaceAnchor> stations, ServiceAccessBoundary accessBoundary) {
    public ResourceStorageAccess {
        Objects.requireNonNull(endpoint); stations = List.copyOf(stations); Objects.requireNonNull(accessBoundary);
        if (stations.isEmpty() || stations.size() > 8 || stations.stream().distinct().count() != stations.size()
                || !accessBoundary.occupiedSurfaces().containsAll(stations))
            throw new IllegalArgumentException("storage access lacks its bounded declared service stations");
    }
    public SubjectId settlementId() { return endpoint.settlementId(); }
    public SubjectId containerId() { return endpoint.containerId(); }
    public SurfaceAnchor interactionStation(FrontierWorldState state) { return ShipmentEndpointComposition.interactionStation(state, endpoint); }
    public List<SurfaceAnchor> ownedAccessSurfaces() { return accessBoundary.occupiedSurfaces().stream().sorted(
            Comparator.comparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::y).thenComparingInt(SurfaceAnchor::z)).toList(); }
    /** Position classification only; it grants neither service access nor terrain clearance. */
    public boolean insideSupportingFacility(FrontierWorldState state, SurfaceAnchor surface) {
        return ShipmentEndpointComposition.supportingFacility(state, endpoint)
                .filter(facility -> facility.definition().fabric().contains(
                        facility.placement().local(surface.support().offset(0, 1, 0)))).isPresent();
    }
    public static ResourceStorageAccess at(FrontierWorldState state, SubjectId containerId) {
        var record = Objects.requireNonNull(state.inventory().containers().get(containerId), "retained resource storage");
        var endpoint = record.storage().orElseThrow(() -> new IllegalArgumentException("container has no declared storage capability")).endpoint();
        ShipmentEndpointComposition.validate(state, endpoint);
        return new ResourceStorageAccess(endpoint, ShipmentEndpointComposition.stations(state, endpoint),
                ServiceAccessCoordinator.boundary(state, containerId));
    }
}
