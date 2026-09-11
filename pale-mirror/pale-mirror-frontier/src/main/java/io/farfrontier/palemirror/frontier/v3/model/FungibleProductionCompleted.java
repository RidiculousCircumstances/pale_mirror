package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable COLD recipe receipt: an input allocation became one output lot in its same account. */
public record FungibleProductionCompleted(SubjectId jobId, ResourceLot output) implements FrontierPayload {
    public FungibleProductionCompleted {
        Objects.requireNonNull(jobId, "fungible production job"); Objects.requireNonNull(output, "fungible production output");
    }
    @Override public String type() { return "frontier.fungible_production_completed"; }
}
