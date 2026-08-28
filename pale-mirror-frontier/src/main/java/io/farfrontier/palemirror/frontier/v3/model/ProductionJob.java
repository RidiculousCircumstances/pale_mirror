package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * A durable facility job after its exact material input has been consumed and before its exact
 * output can be put into a warehouse. A job therefore cannot fabricate output after recovery.
 */
public record ProductionJob(
        SubjectId id,
        SubjectId settlementId,
        SubjectId facilityId,
        SubjectId workerId,
        SubjectId consumedItemId,
        SubjectId outputItemId,
        String outputItemKind,
        int outputCount
) {
    public ProductionJob {
        Objects.requireNonNull(id, "production job id");
        Objects.requireNonNull(settlementId, "settlement id");
        Objects.requireNonNull(facilityId, "facility id");
        Objects.requireNonNull(workerId, "worker id");
        Objects.requireNonNull(consumedItemId, "consumed item id");
        Objects.requireNonNull(outputItemId, "output item id");
        if (outputItemKind == null || !outputItemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")) {
            throw new IllegalArgumentException("production output kind must be namespace:path");
        }
        if (outputCount <= 0 || outputCount > 64) throw new IllegalArgumentException("production output count must be 1..64");
    }
}
