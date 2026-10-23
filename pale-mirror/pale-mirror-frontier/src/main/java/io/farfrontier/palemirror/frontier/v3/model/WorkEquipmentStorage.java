package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Exact equipment shares storage policy and navigation, but requires a whole free item slot. */
public final class WorkEquipmentStorage {
    public static boolean available(FrontierWorldState state, ExactItemStack item, ShipmentEndpoint endpoint) {
        var record = state.inventory().containers().get(endpoint.containerId());
        return record != null && record.storage().filter(policy -> policy.endpoint().equals(endpoint)).isPresent()
                && ResourceStorageSelection.permits(state.inventory(), endpoint.containerId(), item.economicOwnerId(),
                    item.itemKind(), Set.of(ResourceStorageRole.WAREHOUSE))
                && ContainerStorageAdmission.first(state, endpoint.containerId(), Optional.empty(), slot -> false).isPresent();
    }
    public static Optional<ShipmentEndpoint> select(FrontierWorldState state, ExactItemStack item, SurfaceAnchor origin) {
        KnownPedestrianNavigation.RouteUnavailable waiting = null;
        for (var endpoint : ResourceStorageSelection.candidates(state, item.economicOwnerId(), item.itemKind(), origin,
                Set.of(ResourceStorageRole.WAREHOUSE))) {
            if (!available(state, item, endpoint)) continue;
            try { ResourceStorageNavigation.plannedPath(state, origin, endpoint); return Optional.of(endpoint); }
            catch (KnownPedestrianNavigation.RouteUnavailable pending) {
                if (pending.status() != PedestrianRouteResult.Status.NO_PATH) waiting = pending;
            }
        }
        if (waiting != null) throw waiting;
        return Optional.empty();
    }
    private WorkEquipmentStorage() { }
}
