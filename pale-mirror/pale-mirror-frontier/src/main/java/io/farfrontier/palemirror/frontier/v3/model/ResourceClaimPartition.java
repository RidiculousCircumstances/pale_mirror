package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Objects;

/** Divides one pinned reservation into independently movable portions, without changing stock or title. */
public record ResourceClaimPartition(SubjectId accountId, SubjectId claimId, SubjectId childClaimId,
                                     Map<SubjectId, Integer> lotQuantities) {
    public ResourceClaimPartition {
        Objects.requireNonNull(accountId); Objects.requireNonNull(claimId); Objects.requireNonNull(childClaimId);
        lotQuantities = Map.copyOf(lotQuantities);
        if (claimId.equals(childClaimId) || lotQuantities.isEmpty() || lotQuantities.size() > 64
                || lotQuantities.values().stream().anyMatch(q -> q < 1 || q > ResourceLot.MAX_QUANTITY)
                || lotQuantities.values().stream().reduce(0, Math::addExact) > ResourceLot.MAX_QUANTITY) {
            throw new IllegalArgumentException("invalid declared claim partition");
        }
    }
    public int quantity() { return lotQuantities.values().stream().reduce(0, Math::addExact); }
}
