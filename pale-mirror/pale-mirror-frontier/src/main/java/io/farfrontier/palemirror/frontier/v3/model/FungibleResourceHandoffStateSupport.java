package io.farfrontier.palemirror.frontier.v3.model;

import java.util.LinkedHashMap;

/** One physical custody transaction, also used to preview an ordered native witness chain. */
public final class FungibleResourceHandoffStateSupport {
    private FungibleResourceHandoffStateSupport() { }

    public static FrontierWorldState apply(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        var ledger = state.inventory().fungibleResources();
        var source = ledger.accounts().get(observed.sourceAccountId());
        if (source == null || source.custody() instanceof ResourceCustody.Actor
                || observed.destinationAccount().custody() instanceof ResourceCustody.Actor)
            throw new IllegalArgumentException("physical handoff requires a current non-actor source");
        if (!observed.claimQuantities().isEmpty() && observed.forfeitedClaimIds().isEmpty())
            throw new IllegalArgumentException("physical handoff cannot move a live claimed allocation without retiring its owner");
        if (!observed.forfeitedClaimIds().isEmpty()) return FungibleClaimForfeitureStateSupport.apply(state, observed);
        var existing = ledger.accounts().get(observed.destinationAccount().id());
        FungibleResourceLedger transferred;
        if (existing == null) {
            transferred = ledger.transferObservedToNewAccount(observed.sourceAccountId(), observed.destinationAccount(), observed.sourceEpoch(),
                    observed.destinationEpoch(), observed.lotQuantities(), observed.claimQuantities(), observed.remainingSource(), observed.destinationBindings());
        } else {
            var lots = new LinkedHashMap<>(existing.lotQuantities());
            observed.lotQuantities().forEach((id, quantity) -> lots.merge(id, quantity, Math::addExact));
            var claims = new LinkedHashMap<>(existing.claimQuantities());
            observed.claimQuantities().forEach((id, quantity) -> claims.merge(id, quantity, Math::addExact));
            if (!existing.withQuantities(lots, claims).equals(observed.destinationAccount()))
                throw new IllegalArgumentException("fungible handoff has a forged existing destination balance");
            transferred = ledger.transferObservedToExistingAccount(observed.sourceAccountId(), existing.id(), observed.sourceEpoch(),
                    observed.destinationEpoch(), observed.lotQuantities(), observed.claimQuantities(), observed.remainingSource(), observed.destinationBindings());
        }
        if (!observed.playerSaveFence().isEmpty())
            transferred = transferred.fenceUnresolvedPlayerSave(observed.destinationAccount().id(), observed.playerSaveFence());
        return state.withInventory(state.inventory().withFungibleResources(transferred));
    }
}
