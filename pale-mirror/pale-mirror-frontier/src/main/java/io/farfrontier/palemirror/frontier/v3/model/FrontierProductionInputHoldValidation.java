package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Validation local to the production input-hold ownership boundary. */
final class FrontierProductionInputHoldValidation {
    private FrontierProductionInputHoldValidation() {}

    static void validateFungibleProductionHold(ProductionJob job, Settlement settlement, FungibleResourceLedger resources,
                                               SubjectId accountId, SubjectId claimId, boolean bound, long epoch) {
        CustodyAccount account = resources.accounts().get(accountId);
        ResourceLot lot = resources.lots().get(job.consumedItemId());
        ClaimAllocation claim = resources.claims().get(claimId);
        if (account == null || lot == null || claim == null || !(account.custody() instanceof ResourceCustody.Container container)
                || !container.containerId().equals(FrontierWorldState.depotId(settlement.id())) || !lot.economicOwnerId().equals(settlement.id())
                || !"minecraft:wheat".equals(lot.itemKind()) || account.lotQuantities().getOrDefault(lot.id(), 0) < job.outputCount()
                || !claim.claimantId().equals(job.id()) || !claim.economicOwnerId().equals(settlement.id())
                || !"minecraft:wheat".equals(claim.itemKind()) || claim.quantity() != job.outputCount()
                || account.claimQuantities().getOrDefault(claim.id(), 0) != claim.quantity()) {
            throw new IllegalArgumentException("fungible production job must retain one exact depot lot allocation");
        }
        var bindings = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(accountId)).toList();
        boolean hasEpoch = !bindings.isEmpty() && bindings.stream().allMatch(binding -> binding.authorityEpoch() == epoch);
        if (bound && !hasEpoch) {
            throw new IllegalArgumentException("fungible production job does not match its current physical custody epoch");
        }
    }
}
