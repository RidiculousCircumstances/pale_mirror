package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Atomic stock loss and acknowledgements by explicitly registered promise owners. */
public final class FungibleClaimForfeitureStateSupport {
    private FungibleClaimForfeitureStateSupport() { }

    public static boolean supports(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        try { plan(state, observed); return true; }
        catch (IllegalArgumentException rejected) { return false; }
    }

    public static FungibleResourceHandoffObserved stampOwnerDiagnostic(FrontierWorldState state,
                                                                      FungibleResourceHandoffObserved observed) {
        plan(state, observed);
        return observed;
    }

    public static FrontierWorldState apply(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        List<ClaimAllocation> claims = plan(state, observed);
        FungibleResourceLedger cleared = state.inventory().fungibleResources().releaseClaims(observed.forfeitedClaimIds());
        FungibleResourceHandoffObserved effective = observed.withoutReleasedClaims(observed.forfeitedClaimIds());
        CustodyAccount existing = cleared.accounts().get(effective.destinationAccount().id());
        if (existing != null && !expectedDestination(existing, effective).equals(effective.destinationAccount()))
            throw new IllegalArgumentException("physical stock loss has a forged existing destination balance");
        FungibleResourceLedger transferred = existing == null
                ? cleared.transferObservedToNewAccount(effective.sourceAccountId(), effective.destinationAccount(), effective.sourceEpoch(),
                    effective.destinationEpoch(), effective.lotQuantities(), effective.claimQuantities(), effective.remainingSource(), effective.destinationBindings())
                : cleared.transferObservedToExistingAccount(effective.sourceAccountId(), existing.id(), effective.sourceEpoch(), effective.destinationEpoch(),
                    effective.lotQuantities(), effective.claimQuantities(), effective.remainingSource(), effective.destinationBindings());
        if (!effective.playerSaveFence().isEmpty())
            transferred = transferred.fenceUnresolvedPlayerSave(effective.destinationAccount().id(), effective.playerSaveFence());
        var settlement = FungibleForfeitureSettlement.from(state, state.inventory().withFungibleResources(transferred));
        for (var purpose : claims.stream().map(ClaimAllocation::purpose).distinct()
                .sorted(java.util.Comparator.comparing(ClaimPurpose::name)).toList()) {
            settlement = owner(purpose).settle(state,
                    claims.stream().filter(claim -> claim.purpose() == purpose).toList(), settlement);
        }
        return state.withChanges(settlement.contribution());
    }

    private static List<ClaimAllocation> plan(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        Objects.requireNonNull(state, "stock-loss state"); Objects.requireNonNull(observed, "stock-loss observation");
        if (observed.retirementDiagnostic().isPresent())
            throw new IllegalArgumentException("physical stock loss cannot carry a retired ration diagnostic");
        if (observed.forfeitedClaimIds().isEmpty() || !observed.forfeitedClaimIds().containsAll(observed.claimQuantities().keySet())
                || !observed.forfeitedClaimIds().equals(observed.forfeitAffectedClaims(state.inventory().fungibleResources()).forfeitedClaimIds()))
            throw new IllegalArgumentException("physical stock loss must retire every affected current allocation");
        CustodyAccount source = state.inventory().fungibleResources().accounts().get(observed.sourceAccountId());
        if (source == null) throw new IllegalArgumentException("physical stock loss has no current source account");
        List<ClaimAllocation> affected = observed.forfeitedClaimIds().stream().sorted().map(id -> {
            ClaimAllocation claim = state.inventory().fungibleResources().claims().get(id);
            if (claim == null || !source.claimQuantities().containsKey(id)
                    || !observed.claimQuantities().containsKey(id)
                    && claim.lotQuantities().keySet().stream().noneMatch(observed.lotQuantities()::containsKey))
                throw new IllegalArgumentException("physical stock loss has no affected current allocation");
            return claim;
        }).toList();
        affected.forEach(claim -> owner(claim.purpose()).validate(state, claim, observed));
        return affected;
    }

    private static FungibleClaimForfeitureOwner owner(ClaimPurpose purpose) {
        var owner = FungibleClaimForfeitureComposition.OWNERS.get(purpose);
        if (owner == null) throw new IllegalArgumentException("stock loss has no declared owner transition for " + purpose);
        return owner;
    }

    private static CustodyAccount expectedDestination(CustodyAccount current, FungibleResourceHandoffObserved observed) {
        Map<SubjectId, Integer> lots = new LinkedHashMap<>(current.lotQuantities());
        observed.lotQuantities().forEach((id, quantity) -> lots.merge(id, quantity, Integer::sum));
        return current.withQuantities(lots, Map.of());
    }
}
