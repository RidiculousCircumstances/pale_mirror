package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Stable reservation identity over fungible units; it deliberately outlives a physical stack layout. */
public record ClaimAllocation(SubjectId id, SubjectId claimantId, SubjectId economicOwnerId, String itemKind, int quantity) {
    public ClaimAllocation {
        Objects.requireNonNull(id, "claim allocation id"); Objects.requireNonNull(claimantId, "claim allocation claimant");
        Objects.requireNonNull(economicOwnerId, "claim allocation owner");
        if (itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")) {
            throw new IllegalArgumentException("claim allocation kind must be namespace:path");
        }
        if (quantity < 1 || quantity > ResourceLot.MAX_QUANTITY) throw new IllegalArgumentException("claim allocation quantity is outside the bounded range");
    }
}
