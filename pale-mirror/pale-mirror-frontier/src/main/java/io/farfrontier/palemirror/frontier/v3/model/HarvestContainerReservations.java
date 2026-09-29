package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.Set;

/** Read-only capacity claims derived from the active work that owns them. */
final class HarvestContainerReservations {
    private HarvestContainerReservations() { }

    static Set<Integer> slots(ResourceSiteState sites, SubjectId containerId) {
        Set<Integer> reserved = new HashSet<>();
        for (ResourceSiteLifecycle lifecycle : sites.sites().values()) {
            if (!(lifecycle.activeWork().orElse(null) instanceof ResourceSiteHarvestJob job)) continue;
            if (job.outputSlot().containerId().equals(containerId)) reserved.add(job.outputSlot().slot());
            job.batchSuccessorSlot().filter(slot -> slot.containerId().equals(containerId))
                    .ifPresent(slot -> reserved.add(slot.slot()));
        }
        return Set.copyOf(reserved);
    }

    static void validate(ExactInventory inventory, ResourceSiteState sites) {
        Set<InventoryCustody.ContainerSlot> reserved = new HashSet<>();
        for (ResourceSiteLifecycle lifecycle : sites.sites().values()) {
            if (!(lifecycle.activeWork().orElse(null) instanceof ResourceSiteHarvestJob job)) continue;
            if (!reserved.add(job.outputSlot()) || job.batchSuccessorSlot().isPresent()
                    && !reserved.add(job.batchSuccessorSlot().orElseThrow()))
                throw new IllegalArgumentException("active harvest output slot lacks exclusive container capacity");
        }
        for (SubjectId containerId : reserved.stream().map(InventoryCustody.ContainerSlot::containerId).distinct().toList()) {
            if (!inventory.canReserveSlots(containerId, slots(sites, containerId)))
                throw new IllegalArgumentException("active harvest output slot lacks exclusive container capacity");
        }
    }
}
