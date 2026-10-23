package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** An external removal cancels only unacquired equipment and its work offer, never the field. */
final class FieldEquipmentDeparture {
    static boolean committed(FrontierWorldState state, SubjectId item) {
        return state.resourceSites().sites().values().stream().flatMap(site -> site.harvestJobs().values().stream())
                .anyMatch(job -> job.equipment().stage() == WorkToolAssignment.Stage.ACQUIRING && job.equipment().itemId().equals(item));
    }
    static FrontierWorldState observe(FrontierWorldState state, SubjectId item, InventoryCustody source, ExactInventory inventory) {
        var sites = state.resourceSites();
        for (var site : state.resourceSites().sites().values()) for (var job : site.harvestJobs().values()) {
            if (job.equipment().stage() != WorkToolAssignment.Stage.ACQUIRING || !job.equipment().itemId().equals(item)) continue;
            if (job.hasPendingPhysicalWork() || !(source instanceof InventoryCustody.ContainerSlot slot)
                    || !slot.containerId().equals(job.equipment().source().containerId()))
                throw new IllegalArgumentException("external equipment departure overlaps a prepared operation or foreign source");
            var next = sites.site(site.siteId());
            if (!job.progress().complete()) next = next.endHarvestOffer(job);
            var ended = next.harvestJob(job.id()).orElseThrow();
            sites = sites.replace(next.withHarvestEquipment(ended, ended.equipment().releaseUnacquired()));
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).resourceSites(sites));
    }
    private FieldEquipmentDeparture() { }
}
