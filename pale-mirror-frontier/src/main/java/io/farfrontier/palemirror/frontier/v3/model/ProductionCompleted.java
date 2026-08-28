package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Fact that a durable job produced its already-identified output into an exact warehouse slot. */
public record ProductionCompleted(SubjectId jobId, ExactItemStack output) implements FrontierPayload {
    public ProductionCompleted {
        Objects.requireNonNull(jobId, "production job id");
        Objects.requireNonNull(output, "production output");
    }
    @Override public String type() { return "frontier.production_completed"; }
}
