package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact producer-stamped target beside physical-loss evidence; consumers validate, never classify it. */
public record PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind kind, SubjectId subjectId) {
    public PhysicalDeltaSemanticTarget {
        Objects.requireNonNull(kind, "physical-delta target kind");
        Objects.requireNonNull(subjectId, "physical-delta target subject");
    }
}
