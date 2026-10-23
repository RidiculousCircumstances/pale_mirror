package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Bakery-owned retained resource endpoints. Shared access/navigation own their physical mechanics. */
public final class StationWorkStorage {
    private StationWorkStorage() { }
    public static SubjectId source(ProductionJob job) { return job.stationWork().orElseThrow().sourceContainerId(); }
    public static SubjectId destination(ProductionJob job) { return job.rights().destinationContainerId(); }
    public static SubjectId current(ProductionJob job) {
        return switch (job.stationWork().orElseThrow().phase()) {
            case TARGET_PICKUP, TARGET_LOAD -> job.stationWork().orElseThrow().maintenanceTarget().orElseThrow().sourceContainerId();
            case DEPOT_PICKUP, STATION_LOAD, PROCESSING -> source(job);
            case STATION_UNLOAD, DEPOT_DELIVERY, DELIVERED -> destination(job);
        };
    }
    public static ResourceStorageAccess access(FrontierWorldState state, ProductionJob job) {
        return current(job).equals(source(job)) ? ProductionInputAccess.at(state, source(job))
                : ResourceStorageAccess.at(state, current(job));
    }
}
