package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * A durable facility job that owns one exact input until its output is confirmed or work is
 * explicitly cancelled. COLD holds the stack itself; HOT retains its physical depot stack.
 */
public record ProductionJob(
        SubjectId id,
        SubjectId settlementId,
        SubjectId facilityId,
        SubjectId workerId,
        SubjectId consumedItemId,
        ProductionInputHold inputHold,
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
        Objects.requireNonNull(inputHold, "production input hold");
        Objects.requireNonNull(outputItemId, "output item id");
        if (!consumedItemId.equals(inputHold.itemId())) throw new IllegalArgumentException("production input hold must retain its exact item id");
        if (outputItemKind == null || !outputItemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")) {
            throw new IllegalArgumentException("production output kind must be namespace:path");
        }
        if (outputCount <= 0 || outputCount > 64) throw new IllegalArgumentException("production output count must be 1..64");
    }

    /** Compatibility fixture constructor: an existing inventory stack is a materialized hold. */
    public ProductionJob(SubjectId id, SubjectId settlementId, SubjectId facilityId, SubjectId workerId, SubjectId consumedItemId,
                         SubjectId outputItemId, String outputItemKind, int outputCount) {
        this(id, settlementId, facilityId, workerId, consumedItemId, new ProductionInputHold.Materialized(consumedItemId), outputItemId, outputItemKind, outputCount);
    }

    public ProductionJob withInputHold(ProductionInputHold next) {
        return new ProductionJob(id, settlementId, facilityId, workerId, consumedItemId, next, outputItemId, outputItemKind, outputCount);
    }
}
