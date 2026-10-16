package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Extraction owns tool eligibility; shared storage navigation owns physical reachability. */
final class ExtractionToolSelection {
    private ExtractionToolSelection() { }
    record Selection(ExactItemStack item, Optional<PedestrianRouteReceipt> route) { }
    static Optional<Selection> select(FrontierWorldState state, ResidentProfile resident) {
        return select(state, resident, state.bootstrap().ruleset().extraction().definitions().stream()
                .map(definition -> definition.toolKind()).collect(java.util.stream.Collectors.toSet()));
    }
    static Optional<Selection> select(FrontierWorldState state, ResidentProfile resident, Set<String> toolKinds) {
        var origin = state.actorLocations().get(resident.id()).supportingSurface();
        var triedStores = new HashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        KnownPedestrianNavigation.RouteUnavailable waiting = null;
        for (var item : state.inventory().items().values().stream()
                .filter(value -> toolKinds.contains(value.itemKind()) && eligible(state, resident, value))
                .sorted(Comparator.comparingLong((ExactItemStack item) -> {
                    var slot = (InventoryCustody.ContainerSlot) item.custody();
                    var station = state.inventory().containers().get(slot.containerId()).storage().orElseThrow().endpoint().station();
                    return Math.abs((long) station.x() - origin.x()) + Math.abs((long) station.y() - origin.y())
                            + Math.abs((long) station.z() - origin.z());
                }).thenComparing(ExactItemStack::id)).toList()) {
            var slot = (InventoryCustody.ContainerSlot) item.custody();
            if (!triedStores.add(slot.containerId())) continue;
            var endpoint = state.inventory().containers().get(slot.containerId()).storage().orElseThrow().endpoint();
            try {
                var route = ResourceStorageNavigation.plannedPath(state, origin, endpoint);
                return Optional.of(new Selection(item, route.size() > 1
                        ? Optional.of(new PedestrianRouteReceipt(route)) : Optional.empty()));
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) waiting = unavailable;
            }
        }
        if (waiting != null) throw waiting;
        return Optional.empty();
    }
    static boolean eligible(FrontierWorldState state, ResidentProfile resident, ExactItemStack item) {
        return item != null && item.count() == 1 && item.equals(state.inventory().items().get(item.id()))
                && item.economicOwnerId().equals(resident.settlementId())
                && state.bootstrap().ruleset().extraction().definitions().stream().anyMatch(definition -> definition.toolKind().equals(item.itemKind()))
                && item.custody() instanceof InventoryCustody.ContainerSlot slot
                && !ReferenceContainerCustody.blocksCanonicalUse(state, slot.containerId())
                && state.inventory().containers().get(slot.containerId()).storage().isPresent()
                && state.extractionSites().work().values().stream().noneMatch(job -> !job.terminal() && job.toolId().equals(item.id()));
    }
}
