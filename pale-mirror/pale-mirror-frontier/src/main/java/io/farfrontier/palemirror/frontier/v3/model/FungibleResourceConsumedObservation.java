package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Observed consumption of one claimed fungible portion from a current HOT layout. */
public record FungibleResourceConsumedObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId accountId,
                                                  SubjectId lotId, SubjectId claimId, int consumedCount, long authorityEpoch,
                                                  List<FungiblePhysicalObservation.Stack> remainingStacks)
        implements PhysicalEffectObservation {
    public FungibleResourceConsumedObservation {
        Objects.requireNonNull(id, "fungible consumption observation id"); Objects.requireNonNull(intentId, "fungible consumption intent");
        Objects.requireNonNull(accountId, "fungible consumption account"); Objects.requireNonNull(lotId, "fungible consumption lot");
        Objects.requireNonNull(claimId, "fungible consumption claim");
        if (consumedCount < 1 || consumedCount > ResourceLot.MAX_QUANTITY || authorityEpoch < 1) {
            throw new IllegalArgumentException("fungible consumption receipt has invalid count or authority");
        }
        remainingStacks = List.copyOf(Objects.requireNonNull(remainingStacks, "fungible consumption remaining stacks"));
        if (remainingStacks.size() > 54 || remainingStacks.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() != remainingStacks.size()) {
            throw new IllegalArgumentException("fungible consumption receipt has duplicate or unbounded remaining layout");
        }
    }
}
