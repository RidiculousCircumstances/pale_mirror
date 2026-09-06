package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Fact that one named worker consumed one exact input stack to start one durable facility job. */
public record ProductionStarted(ProductionJob job, SubjectId inputItemId) implements FrontierPayload {
    public ProductionStarted {
        Objects.requireNonNull(job, "production job");
        Objects.requireNonNull(inputItemId, "production input item id");
        if (!job.consumedItemId().equals(inputItemId)) throw new IllegalArgumentException("job and production input identity differ");
    }
    @Override public String type() { return "frontier.production_started"; }
}
