package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** One atomic container loss, composed with explicit allocation-owner dispositions. */
public final class PlayerStockClaimLoss {
    private static final Map<ClaimPurpose, PlayerStockClaimLossOwner> OWNERS = register(List.of(
            new ResidentMealPlayerStockLoss(), new InternalShipmentPlayerStockLoss()));
    private PlayerStockClaimLoss() { }

    private static Map<ClaimPurpose, PlayerStockClaimLossOwner> register(List<PlayerStockClaimLossOwner> owners) {
        var result = new EnumMap<ClaimPurpose, PlayerStockClaimLossOwner>(ClaimPurpose.class);
        for (var owner : owners) if (result.put(owner.purpose(), owner) != null)
            throw new IllegalArgumentException("duplicate player source-loss owner");
        return Map.copyOf(result);
    }

    public static Set<SubjectId> admissibleClaims(FrontierWorldState state, SubjectId accountId) {
        var account = state.inventory().fungibleResources().accounts().get(accountId);
        if (account == null) return Set.of();
        var result = new HashSet<SubjectId>();
        for (var id : account.claimQuantities().keySet()) {
            var claim = state.inventory().fungibleResources().claims().get(id);
            var owner = OWNERS.get(claim.purpose());
            if (owner == null) continue;
            try { owner.validate(state, accountId, claim); result.add(id); }
            catch (IllegalArgumentException held) { /* Owner retains a physically pending allocation. */ }
        }
        return Set.copyOf(result);
    }

    public static FrontierWorldState settle(FrontierWorldState state, FungibleStockDepartureObserved loss) {
        var ledger = state.inventory().fungibleResources();
        var claims = loss.forfeitedClaimIds().stream().sorted().map(id -> {
            var claim = ledger.claims().get(id);
            if (claim == null) throw new IllegalArgumentException("player source loss has no current claim: " + id);
            var owner = require(claim);
            owner.validate(state, loss.sourceAccountId(), claim);
            return claim;
        }).toList();
        var cleared = claims.isEmpty() ? ledger : ledger.releaseClaims(loss.forfeitedClaimIds());
        var departed = cleared.departObserved(loss.sourceAccountId(), loss.authorityEpoch(), loss.departedLots(), loss.remaining());
        var transaction = new PlayerStockClaimLossOwner.Settlement(FungibleForfeitureSettlement.from(state,
                state.inventory().withFungibleResources(departed)), state.humanPopulation());
        for (var claim : claims) transaction = require(claim).settle(state, claim, transaction);
        return state.withChanges(transaction.resources().contribution().humanPopulation(transaction.population()));
    }

    public record ReleasedActivity(SubjectId actor, Optional<io.farfrontier.palemirror.frontier.v3.api.ScheduleId> cancelledProgress,
                                   OptionalLong retargetAtTick) { }
    public static List<ReleasedActivity> released(FrontierWorldState before, FungibleStockDepartureObserved loss) {
        return loss.forfeitedClaimIds().stream().sorted().map(before.inventory().fungibleResources().claims()::get)
                .flatMap(claim -> require(claim).released(before, claim).stream())
                .map(value -> new ReleasedActivity(value.actor(), value.cancelledProgress(), value.retargetAtTick())).toList();
    }
    private static PlayerStockClaimLossOwner require(ClaimAllocation claim) {
        var owner = OWNERS.get(claim.purpose());
        if (owner == null) throw new IllegalArgumentException("claim has no player source-loss owner: " + claim.purpose());
        return owner;
    }
}
