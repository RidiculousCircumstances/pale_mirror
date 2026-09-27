package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Same accepted bread job acquires a new explicit 64-wheat allocation after a witnessed loss. */
public record BakeryInputReallocated(SubjectId jobId, ProductionInputHold replacement) implements FrontierPayload {
    public BakeryInputReallocated {
        Objects.requireNonNull(jobId, "bakery job");
        Objects.requireNonNull(replacement, "replacement input");
        if (!(replacement instanceof ProductionInputHold.FungibleCold
                || replacement instanceof ProductionInputHold.FungibleBound
                || replacement instanceof ProductionInputHold.Materialized))
            throw new IllegalArgumentException("bakery replacement must be a declared depot input");
    }
    @Override public String type() { return "frontier.bakery_input_reallocated"; }
}
