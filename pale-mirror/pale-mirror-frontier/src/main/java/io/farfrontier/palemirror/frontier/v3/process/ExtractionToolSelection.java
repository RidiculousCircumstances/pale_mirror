package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Extraction owns tool eligibility; shared storage navigation owns physical reachability. */
final class ExtractionToolSelection {
    private ExtractionToolSelection() { }
    record Selection(ExactItemStack item, Optional<PedestrianRouteReceipt> route) { }
    static Optional<Selection> select(FrontierWorldState state, ResidentProfile resident) {
        var origin = state.actorLocations().get(resident.id()).supportingSurface();
        for (var item : state.inventory().items().values().stream().filter(value -> eligible(state, resident, value))
                .sorted(Comparator.comparing(ExactItemStack::id)).toList()) {
            var slot = (InventoryCustody.ContainerSlot) item.custody();
            var endpoint = state.inventory().containers().get(slot.containerId()).storage().orElseThrow().endpoint();
            try {
                var route = ResourceStorageNavigation.plannedPath(state, origin, endpoint);
                return Optional.of(new Selection(item, route.size() > 1
                        ? Optional.of(new PedestrianRouteReceipt(route)) : Optional.empty()));
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
            }
        }
        return Optional.empty();
    }
    static boolean eligible(FrontierWorldState state, ResidentProfile resident, ExactItemStack item) {
        return item != null && item.count() == 1 && item.equals(state.inventory().items().get(item.id()))
                && item.economicOwnerId().equals(resident.settlementId())
                && item.itemKind().equals(state.bootstrap().ruleset().extraction().source().toolKind())
                && item.custody() instanceof InventoryCustody.ContainerSlot slot
                && !ReferenceContainerCustody.blocksCanonicalUse(state, slot.containerId())
                && state.inventory().containers().get(slot.containerId()).storage().isPresent()
                && state.extractionSites().work().values().stream().noneMatch(job -> !job.terminal() && job.toolId().equals(item.id()));
    }
}
