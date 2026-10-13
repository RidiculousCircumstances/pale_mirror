package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;

/** Adapts an explicitly declared allocation owner; no family policy or synthetic handoff. */
record FungibleAllocationSourceStockLoss(ClaimPurpose purpose, FungibleClaimForfeitureOwner owner)
        implements SourceStockClaimLossOwner {
    FungibleAllocationSourceStockLoss {
        Objects.requireNonNull(purpose); Objects.requireNonNull(owner);
    }
    @Override public void validate(FrontierWorldState state, SubjectId source, ClaimAllocation claim) {
        var account = state.inventory().fungibleResources().accounts().get(source);
        if (claim.purpose() != purpose || account == null || !account.claimQuantities().containsKey(claim.id()))
            throw new IllegalArgumentException("source loss lacks its declared allocation and current account");
        owner.validate(state, claim, source);
    }
    @Override public Settlement settle(FrontierWorldState before, ClaimAllocation claim, Settlement transaction) {
        var next = owner.settle(before, List.of(claim), transaction.resources().withPopulation(transaction.population()));
        return new Settlement(next, next.population());
    }
    @Override public List<ReleasedActivity> released(FrontierWorldState before, ClaimAllocation claim) {
        return owner.released(before, claim);
    }
}
