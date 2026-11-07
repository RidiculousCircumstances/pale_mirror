package io.farfrontier.palemirror.frontier.v3.model;

/** The claim's owning domain decides its disposition; the resource handoff owns only physical accounting. */
interface FungibleClaimForfeitureOwner {
    /** Checkpoint only the owner's exact obsolete motion before removing its promise.
     * The caller publishes this and the stock/title/finance disposition atomically. */
    default FrontierWorldState checkpoint(FrontierWorldState before, ClaimAllocation claim, long atTick) { return before; }
    void validate(FrontierWorldState before, ClaimAllocation claim,
                  io.farfrontier.palemirror.frontier.v3.api.SubjectId sourceAccount);
    FungibleForfeitureSettlement settle(FrontierWorldState before, java.util.List<ClaimAllocation> originalClaims,
                                        FungibleForfeitureSettlement transaction);
    default java.util.List<SourceStockClaimLossOwner.ReleasedActivity> released(FrontierWorldState before,
                                                                             ClaimAllocation claim) {
        return java.util.List.of();
    }
}
