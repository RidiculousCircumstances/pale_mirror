package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Objects;

/** Explicit rights transfer over one allocation; no physical move or inferred recipient. */
public record ResourceTitleTransfer(SubjectId accountId, SubjectId claimId, SubjectId sourceOwnerId,
                                    SubjectId destinationOwnerId, Map<SubjectId, Integer> portions,
                                    Map<SubjectId, SubjectId> splitLotIds) {
    public ResourceTitleTransfer {
        Objects.requireNonNull(accountId); Objects.requireNonNull(claimId);
        Objects.requireNonNull(sourceOwnerId); Objects.requireNonNull(destinationOwnerId);
        portions = Map.copyOf(portions); splitLotIds = Map.copyOf(splitLotIds);
        if (sourceOwnerId.equals(destinationOwnerId) || portions.isEmpty() || portions.size() > 64
                || portions.values().stream().anyMatch(q -> q < 1 || q > ResourceLot.MAX_QUANTITY)
                || quantityOf(portions) > ResourceLot.MAX_QUANTITY
                || !portions.keySet().containsAll(splitLotIds.keySet())
                || splitLotIds.values().stream().distinct().count() != splitLotIds.size()) {
            throw new IllegalArgumentException("invalid declared resource title transfer");
        }
    }
    public int quantity() { return quantityOf(portions); }
    private static int quantityOf(Map<SubjectId, Integer> portions) {
        return portions.values().stream().reduce(0, Math::addExact);
    }
}
