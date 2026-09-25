package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Validation local to the production input-hold ownership boundary. */
final class FrontierProductionInputHoldValidation {
    private FrontierProductionInputHoldValidation() {}

    static void validateFungibleProductionHold(ProductionJob job, Settlement settlement, FungibleResourceLedger resources,
                                               SubjectId accountId, SubjectId claimId, boolean bound, long epoch) {
        CustodyAccount account = resources.accounts().get(accountId);
        java.util.Map<SubjectId, Integer> inputLots = switch (job.inputHold()) {
            case ProductionInputHold.FungibleCold cold -> cold.inputLots();
            case ProductionInputHold.FungibleBound held -> held.inputLots();
            default -> throw new IllegalArgumentException("fungible production validation requires a fungible hold");
        };
        ClaimAllocation claim = resources.claims().get(claimId);
        if (account == null || claim == null || !(account.custody() instanceof ResourceCustody.Container container)
                || !container.containerId().equals(FrontierWorldState.depotId(settlement.id()))
                || inputLots.values().stream().mapToInt(Integer::intValue).sum() != job.outputCount()
                || inputLots.entrySet().stream().anyMatch(entry -> {
                    ResourceLot lot = resources.lots().get(entry.getKey());
                    return lot == null || !lot.economicOwnerId().equals(settlement.id()) || !"minecraft:wheat".equals(lot.itemKind())
                            || account.lotQuantities().getOrDefault(lot.id(), 0) < entry.getValue();
                })
                || claim.purpose() != ClaimPurpose.PRODUCTION_WORK || !claim.claimantId().equals(job.id())
                || !claim.economicOwnerId().equals(settlement.id())
                || !"minecraft:wheat".equals(claim.itemKind()) || claim.quantity() != job.outputCount()
                || !claim.lotQuantities().equals(inputLots)
                || account.claimQuantities().getOrDefault(claim.id(), 0) != claim.quantity()) {
            throw new IllegalArgumentException("fungible production job must retain its exact depot lot allocation");
        }
        var bindings = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(accountId)).toList();
        boolean hasEpoch = !bindings.isEmpty() && bindings.stream().allMatch(binding -> binding.authorityEpoch() == epoch);
        if (bound && !hasEpoch) {
            throw new IllegalArgumentException("fungible production job does not match its current physical custody epoch");
        }
    }
}
