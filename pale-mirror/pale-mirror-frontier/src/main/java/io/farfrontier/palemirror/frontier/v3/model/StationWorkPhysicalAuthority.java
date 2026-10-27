package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Exact scope fence while a prepared bakery effect may have changed loaded physical stock. */
public final class StationWorkPhysicalAuthority {
    private static final ImmutableInputView<java.util.Set<SubjectId>> PENDING_CONTAINERS = new ImmutableInputView<>();
    private StationWorkPhysicalAuthority() { }

    public static java.util.List<ContainerSlotClaim> slotClaims(FrontierWorldState state) {
        return slotClaims(state.productionJobs());
    }
    public static java.util.List<ContainerSlotClaim> slotClaims(java.util.Map<SubjectId, ProductionJob> jobs) {
        var result = new java.util.ArrayList<ContainerSlotClaim>();
        for (ProductionJob job : jobs.values()) {
            var work = job.stationWork().orElse(null);
            if (work == null || work.pendingPhysicalStep().isEmpty()
                    || work.phase() != StationWorkState.Phase.DEPOT_DELIVERY) continue;
            var depot = StationWorkStorage.current(job);
            result.add(new ContainerSlotClaim(new ContainerSlotClaim.Owner(ContainerSlotClaim.Family.PRODUCTION, job.id()), new InventoryCustody.ContainerSlot(depot,
                    work.pendingPhysicalStep().orElseThrow().destinationSlot()),
                    java.util.Optional.of(new ContainerInboundCapacity.Demand(job.id(), depot,
                            job.outputItemKind(), job.outputCount(), ProductionOutputCapacity.inboundRepresentation(job)))));
        }
        return java.util.List.copyOf(result);
    }

    public static boolean pendingForContainer(FrontierWorldState state, SubjectId containerId) {
        return PENDING_CONTAINERS.get(java.util.List.of(state.productionJobs(), state.inventory().containers()), () -> {
            var containers = new java.util.HashSet<SubjectId>();
            for (ProductionJob job : state.productionJobs().values()) {
                StationWorkState work = job.stationWork().orElse(null);
                if (work == null || work.pendingPhysicalStep().isEmpty()) continue;
                if (work.phase().sourcePickup() || work.phase() == StationWorkState.Phase.DEPOT_DELIVERY) {
                    containers.add(StationWorkStorage.current(job));
                    continue;
                }
                for (var record : ProductionStationDirectory.inFacility(state.inventory(), job.facilityId()))
                    if (record.productionStation().orElseThrow().id().equals(work.stationId())) containers.add(record.id());
            }
            return java.util.Set.copyOf(containers);
        }).contains(containerId);
    }
}
