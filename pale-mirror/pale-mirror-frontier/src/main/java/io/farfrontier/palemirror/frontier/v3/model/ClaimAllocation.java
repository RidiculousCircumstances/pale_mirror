package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Map;

/** Stable reservation identity over fungible units; it deliberately outlives a physical stack layout. */
public record ClaimAllocation(SubjectId id, SubjectId claimantId, SubjectId economicOwnerId, String itemKind, int quantity,
                              Map<SubjectId, Integer> lotQuantities, ClaimPurpose purpose) {
    public ClaimAllocation {
        Objects.requireNonNull(id, "claim allocation id"); Objects.requireNonNull(claimantId, "claim allocation claimant");
        Objects.requireNonNull(economicOwnerId, "claim allocation owner");
        Objects.requireNonNull(purpose, "claim lifecycle owner");
        if (itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")) {
            throw new IllegalArgumentException("claim allocation kind must be namespace:path");
        }
        if (quantity < 1 || quantity > ResourceLot.MAX_QUANTITY) throw new IllegalArgumentException("claim allocation quantity is outside the bounded range");
        lotQuantities = Map.copyOf(Objects.requireNonNull(lotQuantities, "claim lot allocation"));
        if (lotQuantities.size() > 64 || lotQuantities.values().stream().anyMatch(value -> value < 1 || value > ResourceLot.MAX_QUANTITY)
                || !lotQuantities.isEmpty() && lotQuantities.values().stream().mapToInt(Integer::intValue).sum() != quantity) {
            throw new IllegalArgumentException("claim lot allocation must retain its exact bounded quantity");
        }
        if (purpose != ClaimPurpose.EXTERNAL_RESERVATION && lotQuantities.isEmpty()
                || purpose == ClaimPurpose.PRODUCTION_WORK && !itemKind.equals("minecraft:wheat")
                || purpose == ClaimPurpose.HIVE_GROWTH && !itemKind.equals("minecraft:rotten_flesh")
                || purpose == ClaimPurpose.SETTLEMENT_RATION && !itemKind.equals("minecraft:bread")) {
            throw new IllegalArgumentException("claim purpose does not match its declared resource allocation");
        }
    }
    public ClaimAllocation withQuantity(int remaining) {
        if (!lotQuantities.isEmpty()) throw new IllegalArgumentException("pinned claim cannot change quantity without its lot map");
        return new ClaimAllocation(id, claimantId, economicOwnerId, itemKind, remaining, lotQuantities, purpose);
    }
}
