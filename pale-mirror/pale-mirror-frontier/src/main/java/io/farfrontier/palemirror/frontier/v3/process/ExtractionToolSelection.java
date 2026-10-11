package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Extraction owns tool eligibility; shared storage navigation owns physical reachability. */
final class ExtractionToolSelection {
    private ExtractionToolSelection() { }
    static Optional<ExactItemStack> select(FrontierWorldState state, ResidentProfile resident) {
        var origin = state.actorLocations().get(resident.id()).supportingSurface();
        return state.inventory().items().values().stream().filter(item ->
                item.economicOwnerId().equals(resident.settlementId())
                && item.itemKind().equals(state.bootstrap().ruleset().extraction().source().toolKind())
                && item.custody() instanceof InventoryCustody.ContainerSlot slot
                && !ReferenceContainerCustody.blocksCanonicalUse(state, slot.containerId())
                && state.inventory().containers().get(slot.containerId()).storage().filter(policy ->
                        ResourceStorageNavigation.reachable(state, origin, policy.endpoint())).isPresent()
                && state.extractionSites().work().values().stream().noneMatch(job -> !job.terminal() && job.toolId().equals(item.id())))
                .min(Comparator.comparing(ExactItemStack::id));
    }
}
