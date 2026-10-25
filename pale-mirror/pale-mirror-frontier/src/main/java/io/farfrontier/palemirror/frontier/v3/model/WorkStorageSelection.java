package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Shared dynamic warehouse placement for a declared fungible or exact-item portion.
 * Work families supply requirements; this component never discovers their phases. */
public final class WorkStorageSelection {
    public static final Set<ResourceStorageRole> ROLES = Set.of(ResourceStorageRole.WAREHOUSE, ResourceStorageRole.LOCAL_BUFFER);
    public record Portion(SubjectId home, String kind, int quantity, boolean exact) {
        public Portion { Objects.requireNonNull(home); Objects.requireNonNull(kind);
            if (quantity < 1 || exact && quantity != 1) throw new IllegalArgumentException("invalid work storage portion"); }
    }
    public record Selected(ShipmentEndpoint endpoint, Optional<PedestrianRouteReceipt> route) { }
    private WorkStorageSelection() { }
    public static boolean available(FrontierWorldState state, Portion portion, ShipmentEndpoint endpoint) {
        if (!ResourceStorageSelection.receivesAt(state, endpoint, portion.home(), portion.kind(), portion.quantity(), ROLES, Optional.empty())
                || ReferenceContainerCustody.blocksCanonicalUse(state, endpoint.containerId())) return false;
        return !portion.exact() || ContainerStorageAdmission.first(state, endpoint.containerId(), Optional.empty(), slot -> false).isPresent();
    }
    public static Optional<Selected> select(FrontierWorldState state, Portion portion, SurfaceAnchor origin,
                                           Optional<ShipmentEndpoint> excluding) {
        KnownPedestrianNavigation.RouteUnavailable waiting = null;
        for (var endpoint : ResourceStorageSelection.receivingCandidates(state, portion.home(), portion.kind(), portion.quantity(), origin, ROLES, Optional.empty())) {
            if (excluding.filter(endpoint::equals).isPresent() || !available(state, portion, endpoint)) continue;
            try {
                var route = ResourceStorageNavigation.plannedPath(state, origin, endpoint);
                return Optional.of(new Selected(endpoint, route.size() > 1 ? Optional.of(new PedestrianRouteReceipt(route)) : Optional.empty()));
            } catch (KnownPedestrianNavigation.RouteUnavailable pending) {
                if (pending.status() != PedestrianRouteResult.Status.NO_PATH) waiting = pending;
            }
        }
        if (waiting != null) throw waiting;
        return Optional.empty();
    }
    public static void require(FrontierWorldState state, Portion portion, SurfaceAnchor origin, Selected selected) {
        if (!available(state, portion, selected.endpoint())) throw new IllegalArgumentException("selected work storage no longer accepts its portion");
        ResourceStorageNavigation.requireAcceptedDestination(state, origin, selected.endpoint(), selected.route());
    }
}
