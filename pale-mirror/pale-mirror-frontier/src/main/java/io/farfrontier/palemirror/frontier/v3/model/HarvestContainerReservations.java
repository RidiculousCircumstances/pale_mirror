package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.Set;

/** Read-only capacity claims derived from the active work that owns them. */
final class HarvestContainerReservations {
    private HarvestContainerReservations() { }

    static void validateAccounts(java.util.Map<SubjectId, CustodyAccount> accounts, java.util.Map<SubjectId, ResourceSiteLifecycle> sites) {
        Set<SubjectId> declared = new HashSet<>();
        for (var site : sites.values()) for (var job : site.harvestJobs().values()) {
            if (!declared.add(job.actorAccountId()))
                throw new IllegalArgumentException("active harvest jobs share one declared actor resource account");
            CustodyAccount account = accounts.get(job.actorAccountId());
            if (account != null && !account.custody().equals(new ResourceCustody.Actor(job.workerId())))
                throw new IllegalArgumentException("active harvest actor account has foreign custody");
            CustodyAccount depotAccount = accounts.get(job.depotAccountId());
            if (depotAccount != null && !depotAccount.custody().equals(new ResourceCustody.Container(job.outputSlot().containerId())))
                throw new IllegalArgumentException("active harvest depot account has foreign custody");
        }
    }

    static java.util.List<ContainerSlotClaim> claims(java.util.Map<SubjectId, ResourceSiteLifecycle> sites) {
        var result = new java.util.ArrayList<ContainerSlotClaim>();
        for (var site : sites.values()) for (var job : site.harvestJobs().values()) {
            var owner = new ContainerSlotClaim.Owner(ContainerSlotClaim.Family.HARVEST, job.id());
            if (job.reservesOutputCapacity()) result.add(new ContainerSlotClaim(owner, job.outputSlot()));
            job.batchSuccessorSlot().ifPresent(slot -> result.add(new ContainerSlotClaim(owner, slot)));
            job.equipment().pending().ifPresent(step -> result.add(new ContainerSlotClaim(owner, step.slot())));
        }
        return java.util.List.copyOf(result);
    }

    static void validate(java.util.Map<SubjectId, ContainerRecord> containers, java.util.Map<SubjectId, ResourceSiteLifecycle> sites) {
        Set<InventoryCustody.ContainerSlot> reserved = new HashSet<>();
        for (var site : sites.values()) for (var job : site.harvestJobs().values()) {
            if (job.reservesOutputCapacity() && !reserved.add(job.outputSlot()) || job.batchSuccessorSlot().isPresent()
                    && !reserved.add(job.batchSuccessorSlot().orElseThrow()))
                throw new IllegalArgumentException("active harvest output slot lacks exclusive container capacity");
        }
        // A future destination is not exclusive authority over a player's chest.
        // Current backing is derived by ContainerPhysicalReservations; execution
        // reselects/waits if external edits consume it. Owner identity stays exact.
        for (var slot : reserved) {
            var container = containers.get(slot.containerId());
            if (container == null || slot.slot() >= container.slotCount())
                throw new IllegalArgumentException("active harvest output names an undeclared container slot");
        }
    }
}
