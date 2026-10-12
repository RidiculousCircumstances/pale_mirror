package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Admission of a selected worker, retained input and warehouse access; it is not consumption or arrival. */
public record ProductionStarted(ProductionJob job, SubjectId inputItemId,
        java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianAccessReceipt> access) implements FrontierPayload {
    /** An incomplete envelope is not admissible as fresh bakery work. */
    public ProductionStarted(ProductionJob job, SubjectId inputItemId) { this(job, inputItemId, java.util.Optional.empty()); }
    public ProductionStarted {
        Objects.requireNonNull(job, "production job");
        Objects.requireNonNull(inputItemId, "production input item id");
        Objects.requireNonNull(access, "production access evidence");
        if (!job.consumedItemId().equals(inputItemId)) throw new IllegalArgumentException("job and production input identity differ");
    }
    @Override public String type() { return "frontier.production_started"; }
}
