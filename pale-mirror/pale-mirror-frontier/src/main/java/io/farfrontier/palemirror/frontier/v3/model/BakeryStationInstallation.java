package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Bakery's replacement adapter preserves carried ingredients and the resident, not the disappeared machine's runtime. */
public final class BakeryStationInstallation {
    /** A transfer prepared before replacement settles first; its receipt can then admit the new instance. */
    static ProductionJob afterCheckpoint(FrontierWorldState state, ProductionJob job) {
        var work = job.bakeryWork().orElse(null);
        if (work == null || work.pendingPhysicalStep().isPresent()
                || work.phase() != BakeryWorkState.Phase.DEPOT_PICKUP && work.phase() != BakeryWorkState.Phase.STATION_LOAD)
            return job;
        var old = ProductionStationEnergy.record(state, work.stationId());
        if (state.inventory().surfaces().get(old.id()).status() != ContainerSurfaceStatus.RETIRED) return job;
        return ProductionStationSelection.usable(state, job.facilityId(), work.recipeId())
                .map(station -> job.withBakeryWork(work.withReplacementStation(station))).orElse(job);
    }
    public static FrontierWorldStateUpdate installed(FrontierWorldState state, ProductionStationSpec replacement) {
        var jobs = new LinkedHashMap<>(state.productionJobs()); boolean changed = false;
        for (var job : state.productionJobs().values()) {
            var work = job.bakeryWork().orElse(null);
            if (work == null || !job.facilityId().equals(replacement.facilityId()) || work.pendingPhysicalStep().isPresent()
                    || work.phase() != BakeryWorkState.Phase.DEPOT_PICKUP && work.phase() != BakeryWorkState.Phase.STATION_LOAD) continue;
            var previous = ProductionStationEnergy.record(state, work.stationId());
            if (state.inventory().surfaces().get(previous.id()).status() != ContainerSurfaceStatus.RETIRED)
                throw new IllegalArgumentException("replacement cannot steal an order from a surviving installed machine");
            state.bootstrap().ruleset().production().requireEquipment(replacement.equipmentType())
                    .require(state.bootstrap().ruleset().production().require(work.recipeId()));
            jobs.put(job.id(), job.withBakeryWork(work.withReplacementStation(replacement))); changed = true;
        }
        return changed ? FrontierWorldStateUpdate.begin().productionJobs(jobs) : FrontierWorldStateUpdate.begin();
    }
    private BakeryStationInstallation() { }
}
