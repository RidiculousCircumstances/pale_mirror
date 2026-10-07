package io.farfrontier.palemirror.frontier.v3.model;

/** The claim's owning domain decides its disposition; the resource handoff owns only physical accounting. */
interface FungibleClaimForfeitureOwner {
    void validate(FrontierWorldState before, ClaimAllocation claim, FungibleResourceHandoffObserved observation);
    FungibleForfeitureSettlement settle(FrontierWorldState before, java.util.List<ClaimAllocation> originalClaims,
                                        FungibleForfeitureSettlement transaction);
}
