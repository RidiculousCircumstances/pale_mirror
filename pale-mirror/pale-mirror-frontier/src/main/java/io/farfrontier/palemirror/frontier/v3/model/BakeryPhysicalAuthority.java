package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Exact scope fence while a prepared bakery effect may have changed loaded physical stock. */
public final class BakeryPhysicalAuthority {
    private BakeryPhysicalAuthority() { }

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
