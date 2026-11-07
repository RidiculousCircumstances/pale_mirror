package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Exact equipment shares storage policy and navigation, but requires a whole free item slot. */
public final class WorkEquipmentStorage {
    /** Exact source custody and storage ownership are shared by selection and HOT/COLD issue. */
    public static boolean sourceAvailable(FrontierWorldState state, ExactItemStack item,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId container) {
        return item != null && item.equals(state.inventory().items().get(item.id()))
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(container)
                && ResourceStorageSelection.permitsWithdrawal(state.inventory(), container, item.economicOwnerId(),
                    Set.of(ResourceStorageRole.WAREHOUSE, ResourceStorageRole.LOCAL_BUFFER))
                && !ReferenceContainerCustody.blocksCanonicalUse(state, container);
    }
    public static boolean available(FrontierWorldState state, ExactItemStack item, ShipmentEndpoint endpoint) {
        var record = state.inventory().containers().get(endpoint.containerId());
        return record != null && record.storage().filter(policy -> policy.endpoint().withStation(endpoint.station()).equals(endpoint)).isPresent()
                && ResourceStorageSelection.permits(state.inventory(), endpoint.containerId(), item.economicOwnerId(),
                    item.itemKind(), Set.of(ResourceStorageRole.WAREHOUSE))
                && ContainerStorageAdmission.first(state, endpoint.containerId(), Optional.empty(), slot -> false).isPresent();
    }
    public static Optional<ShipmentEndpoint> select(FrontierWorldState state, ExactItemStack item, SurfaceAnchor origin, UnitNavigationProfile profile) {
        KnownPedestrianNavigation.RouteUnavailable waiting = null;
        for (var endpoint : ResourceStorageSelection.candidates(state, item.economicOwnerId(), item.itemKind(), origin,
                Set.of(ResourceStorageRole.WAREHOUSE))) {
            if (!available(state, item, endpoint)) continue;
            try {
                var selected = ResourceStorageNavigation.selectApproach(state, origin, profile, endpoint);
                if (selected.isEmpty()) continue;
                ResourceStorageNavigation.plannedPath(state, origin, profile, selected.orElseThrow());
                return selected;
            }
            catch (KnownPedestrianNavigation.RouteUnavailable pending) {
                if (pending.status() != PedestrianRouteResult.Status.NO_PATH) waiting = pending;
            }
        }
        if (waiting != null) throw waiting;
        return Optional.empty();
    }
    private WorkEquipmentStorage() { }
}
