package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One bounded ration slice from either an exact item or a COLD lot claim. */
public record SettlementRationAllocation(SubjectId itemId, List<SubjectId> recipientIds,
                                         Optional<FungibleSource> fungibleSource) {
    public SettlementRationAllocation {
        Objects.requireNonNull(itemId, "ration item id");
        recipientIds = List.copyOf(Objects.requireNonNull(recipientIds, "ration recipients"));
        fungibleSource = Objects.requireNonNull(fungibleSource, "ration fungible source");
        if (recipientIds.isEmpty() || recipientIds.size() > 64 || recipientIds.stream().distinct().count() != recipientIds.size()) {
            throw new IllegalArgumentException("ration allocation recipients must be distinct and fit one exact stack");
        }
    }

    public SettlementRationAllocation(SubjectId itemId, List<SubjectId> recipientIds) {
        this(itemId, recipientIds, Optional.empty());
    }

    public static SettlementRationAllocation fungible(SubjectId lotId, SubjectId accountId, SubjectId claimId, List<SubjectId> recipientIds) {
        return new SettlementRationAllocation(lotId, recipientIds, Optional.of(new FungibleSource(accountId, claimId)));
    }

    public boolean fungible() { return fungibleSource.isPresent(); }
    public SubjectId identity() { return fungibleSource.map(FungibleSource::claimId).orElse(itemId); }

    public int count() {
        return recipientIds.size();
    }

    public record FungibleSource(SubjectId accountId, SubjectId claimId) {
        public FungibleSource {
            Objects.requireNonNull(accountId, "ration resource account"); Objects.requireNonNull(claimId, "ration claim");
        }
    }
}
