package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Stable social home for exact people; it owns no hidden population aggregate. */
public record Household(SubjectId id, SubjectId settlementId) {
    public Household {
        Objects.requireNonNull(id, "household id");
        Objects.requireNonNull(settlementId, "household settlement id");
        if (!id.value().startsWith("household:")) throw new IllegalArgumentException("household id must use household: namespace");
        if (!settlementId.value().startsWith("settlement:")) throw new IllegalArgumentException("household settlement must use settlement: namespace");
    }
}
