package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Stable closed tactical-policy identity retained with an operation. */
public record TacticalPolicyDescriptor(String id, int version) {
    public TacticalPolicyDescriptor {
        id = Objects.requireNonNull(id, "tactical policy id");
        if (id.isBlank() || version <= 0) throw new IllegalArgumentException("tactical policy descriptor is invalid");
    }
}
