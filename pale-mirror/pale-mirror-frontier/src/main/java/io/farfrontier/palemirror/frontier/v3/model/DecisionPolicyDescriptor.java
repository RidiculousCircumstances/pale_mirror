package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Stable closed-policy identity retained with a decision authority. */
public record DecisionPolicyDescriptor(String id, int version) {
    public DecisionPolicyDescriptor {
        id = Objects.requireNonNull(id, "decision policy id");
        if (id.isBlank() || version <= 0) throw new IllegalArgumentException("decision policy descriptor is invalid");
    }
}
