package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** One atomic source loss, composed with explicit allocation-owner dispositions. */
public final class SourceStockClaimLoss {
    private static final Map<ClaimPurpose, SourceStockClaimLossOwner> OWNERS = register(List.of(
            new ResidentMealPlayerStockLoss(), new InternalShipmentPlayerStockLoss(),
            allocation(ClaimPurpose.GOODS_TRADE), allocation(ClaimPurpose.EXPEDITION_SUPPLY),
            allocation(ClaimPurpose.PRODUCTION_WORK), allocation(ClaimPurpose.STATION_PROCESSING), allocation(ClaimPurpose.STATION_OUTPUT)));
    private SourceStockClaimLoss() { }

    private static SourceStockClaimLossOwner allocation(ClaimPurpose purpose) {
        var owner = FungibleClaimForfeitureComposition.OWNERS.get(purpose);
        if (owner == null) throw new IllegalArgumentException("missing declared allocation-loss owner: " + purpose);
        return new FungibleAllocationSourceStockLoss(purpose, owner);
    }

    /** Exact resource debit permission; unrelated account reservations cannot block a player. */
    public static boolean canDepart(FrontierWorldState state, SubjectId source, String itemKind, int quantity) {
        var ledger = state.inventory().fungibleResources();
        var account = ledger.accounts().get(source);
        if (account == null || quantity < 0) return false;
        try {
            var losses = Map.of(itemKind, quantity);
            var forfeited = SourceStockDepartureSelection.claimsNeededForExit(ledger, account, losses,
                    admissibleClaims(state, source));
            var cleared = forfeited.isEmpty() ? ledger : ledger.releaseClaims(forfeited);
            SourceStockDepartureSelection.unclaimedDepartures(cleared, cleared.accounts().get(source), losses);
            return true;
        } catch (IllegalArgumentException held) { return false; }
    }

    private static Map<ClaimPurpose, SourceStockClaimLossOwner> register(List<SourceStockClaimLossOwner> owners) {
        var result = new EnumMap<ClaimPurpose, SourceStockClaimLossOwner>(ClaimPurpose.class);
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
        return settle(state, loss.sourceAccountId(), loss.authorityEpoch(), loss.departedLots(), loss.forfeitedClaimIds(), loss.remaining());
    }
    public static FrontierWorldState settle(FrontierWorldState state, SubjectId source, long epoch,
            Map<SubjectId, Integer> departedLots, Set<SubjectId> forfeited, List<FungiblePhysicalObservation.Stack> remaining) {
        var ledger = state.inventory().fungibleResources();
        var claims = forfeited.stream().sorted().map(id -> {
            var claim = ledger.claims().get(id);
            if (claim == null) throw new IllegalArgumentException("player source loss has no current claim: " + id);
            var owner = require(claim);
            owner.validate(state, source, claim);
            return claim;
        }).toList();
        var cleared = claims.isEmpty() ? ledger : ledger.releaseClaims(forfeited);
        var departed = cleared.departObserved(source, epoch, departedLots, remaining);
        var transaction = new SourceStockClaimLossOwner.Settlement(FungibleForfeitureSettlement.from(state,
                state.inventory().withFungibleResources(departed)), state.humanPopulation());
        for (var claim : claims) transaction = require(claim).settle(state, claim, transaction);
        return state.withChanges(transaction.resources().contribution().humanPopulation(transaction.population()));
    }

    public record ReleasedActivity(SubjectId actor, Optional<io.farfrontier.palemirror.frontier.v3.api.ScheduleId> cancelledProgress,
                                   OptionalLong retargetAtTick) { }
    public static List<ReleasedActivity> released(FrontierWorldState before, FungibleStockDepartureObserved loss) {
        return released(before, loss.forfeitedClaimIds());
    }
    public static List<ReleasedActivity> released(FrontierWorldState before, Set<SubjectId> forfeited) {
        return forfeited.stream().sorted().map(before.inventory().fungibleResources().claims()::get)
                .flatMap(claim -> require(claim).released(before, claim).stream())
                .map(value -> new ReleasedActivity(value.actor(), value.cancelledProgress(), value.retargetAtTick())).toList();
    }
    private static SourceStockClaimLossOwner require(ClaimAllocation claim) {
        var owner = OWNERS.get(claim.purpose());
        if (owner == null) throw new IllegalArgumentException("claim has no player source-loss owner: " + claim.purpose());
        return owner;
    }
}
