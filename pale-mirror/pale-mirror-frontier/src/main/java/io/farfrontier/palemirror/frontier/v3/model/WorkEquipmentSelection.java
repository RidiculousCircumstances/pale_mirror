package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.tooling.EquipmentRequirements;
import java.util.*;

/** Shared equipment discovery. Work owners declare requirements and unavailable exact identities. */
public final class WorkEquipmentSelection {
    public record Selection(ExactItemStack item, ShipmentEndpoint source, Optional<PedestrianRouteReceipt> route) { }

    public static Optional<Selection> select(FrontierWorldState state, SubjectId owner, SurfaceAnchor origin,
            EquipmentRequirements requirements, Set<SubjectId> unavailable) {
        var triedStores = new HashSet<SubjectId>();
        KnownPedestrianNavigation.RouteUnavailable waiting = null;
        for (var item : state.inventory().items().values().stream()
                .filter(value -> available(state, owner, value, requirements, unavailable))
                .sorted(Comparator.comparingLong((ExactItemStack item) -> distance(origin, endpoint(state, item).station()))
                        .thenComparing(ExactItemStack::id)).toList()) {
            var source = endpoint(state, item);
            if (!triedStores.add(source.containerId())) continue;
            try {
                var route = ResourceStorageNavigation.plannedPath(state, origin, source);
                return Optional.of(new Selection(item, source, route.size() > 1
                        ? Optional.of(new PedestrianRouteReceipt(route)) : Optional.empty()));
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailableRoute) {
                if (unavailableRoute.status() != PedestrianRouteResult.Status.NO_PATH) waiting = unavailableRoute;
            }
        }
        if (waiting != null) throw waiting;
        return Optional.empty();
    }

    public static boolean available(FrontierWorldState state, SubjectId owner, ExactItemStack item,
            EquipmentRequirements requirements, Set<SubjectId> unavailable) {
        if (item == null || !item.equals(state.inventory().items().get(item.id())) || !item.economicOwnerId().equals(owner)
                || unavailable.contains(item.id()) || !(item.custody() instanceof InventoryCustody.ContainerSlot slot)
                || ReferenceContainerCustody.blocksCanonicalUse(state, slot.containerId())
                || state.inventory().containers().get(slot.containerId()).storage().isEmpty()) return false;
        return requirements.serviceable(state.bootstrap().ruleset().tools(), item);
    }
    private static ShipmentEndpoint endpoint(FrontierWorldState state, ExactItemStack item) {
        return state.inventory().containers().get(((InventoryCustody.ContainerSlot) item.custody()).containerId())
                .storage().orElseThrow().endpoint();
    }
    private static long distance(SurfaceAnchor a, SurfaceAnchor b) {
        return Math.abs((long) a.x() - b.x()) + Math.abs((long) a.y() - b.y()) + Math.abs((long) a.z() - b.z());
    }
    private WorkEquipmentSelection() { }
}
