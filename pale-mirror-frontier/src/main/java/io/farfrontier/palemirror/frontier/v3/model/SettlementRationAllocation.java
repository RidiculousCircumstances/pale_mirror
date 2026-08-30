package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One bounded slice of one named exact food stack assigned to a current settlement ration cycle. */
public record SettlementRationAllocation(SubjectId itemId, int count) {
    public SettlementRationAllocation {
        Objects.requireNonNull(itemId, "ration item id");
        if (count < 1 || count > 64) throw new IllegalArgumentException("ration allocation count must be 1..64");
    }
}
