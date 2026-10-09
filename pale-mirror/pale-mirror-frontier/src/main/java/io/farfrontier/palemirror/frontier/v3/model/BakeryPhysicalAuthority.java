package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Exact scope fence while a prepared bakery effect may have changed loaded physical stock. */
public final class BakeryPhysicalAuthority {
    private BakeryPhysicalAuthority() { }

    public static java.util.List<ContainerSlotClaim> slotClaims(FrontierWorldState state) {
        return slotClaims(state.productionJobs());
    }
    public static java.util.List<ContainerSlotClaim> slotClaims(java.util.Map<SubjectId, ProductionJob> jobs) {
        var result = new java.util.ArrayList<ContainerSlotClaim>();
        for (ProductionJob job : jobs.values()) {
            var work = job.bakeryWork().orElse(null);
            if (work == null || work.pendingPhysicalStep().isEmpty()
                    || work.phase() != BakeryWorkState.Phase.DEPOT_DELIVERY) continue;
            var depot = FrontierWorldState.depotId(job.settlementId());
            result.add(new ContainerSlotClaim(new ContainerSlotClaim.Owner(ContainerSlotClaim.Family.PRODUCTION, job.id()), new InventoryCustody.ContainerSlot(depot,
                    work.pendingPhysicalStep().orElseThrow().destinationSlot()),
                    java.util.Optional.of(new ContainerInboundCapacity.Demand(job.id(), depot,
                            job.outputItemKind(), job.outputCount()))));
        }
        return java.util.List.copyOf(result);
    }

    public static boolean pendingForContainer(FrontierWorldState state, SubjectId containerId) {
        for (ProductionJob job : state.productionJobs().values()) {
            BakeryWorkState work = job.bakeryWork().orElse(null);
            if (work == null || work.pendingPhysicalStep().isEmpty()) continue;
            if (work.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                    || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY) {
                if (FrontierWorldState.depotId(job.settlementId()).equals(containerId)) return true;
                continue;
            }
            if (state.inventory().containers().values().stream().flatMap(container -> container.productionStation().stream())
                    .anyMatch(station -> station.id().equals(work.stationId())
                            && station.facilityId().equals(job.facilityId())
                            && station.containerId().equals(containerId))) return true;
        }
        return false;
    }
}
