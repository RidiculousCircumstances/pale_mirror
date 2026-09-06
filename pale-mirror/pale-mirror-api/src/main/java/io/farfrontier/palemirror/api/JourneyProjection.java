package io.farfrontier.palemirror.api;

import java.util.List;

/** Bounded read-only canonical travel projection for a physical provider. */
public record JourneyProjection(String journeyId, String regionId, String populationGroupId,
                                long projectionRevision, String state, double progress, int checkpointIndex,
                                int materializationLimit, int spawnBudgetPerTick, boolean restoreAtOrigin, List<VisualPoint> path,
                                List<String> retireAtOriginResidentIds, List<String> restoreResidentIds,
                                List<JourneyResidentLeaseView> residentLeases) {
    public JourneyProjection {
        path = List.copyOf(path); retireAtOriginResidentIds = List.copyOf(retireAtOriginResidentIds);
        restoreResidentIds = List.copyOf(restoreResidentIds); residentLeases = List.copyOf(residentLeases);
        if (materializationLimit < 0) throw new IllegalArgumentException("Materialization limit must not be negative");
        if (spawnBudgetPerTick < 1) throw new IllegalArgumentException("Spawn budget must be positive");
    }
}
