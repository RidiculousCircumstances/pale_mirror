package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** A witnessed player pickup exits managed stock, as an explicit chest withdrawal does. */
public record GroundResourcePlayerWithdrawal(SubjectId sourceAccountId, ResourceCustody.Ground source, long epoch,
        SubjectId economicOwner, UUID witnessId, UUID playerId, Map<SubjectId, Integer> beforeLots,
        Map<SubjectId, Integer> departedLots, Set<SubjectId> forfeitedClaims,
        List<FungiblePhysicalObservation.Stack> remaining) implements GroundStockExit {
    public GroundResourcePlayerWithdrawal {
        Objects.requireNonNull(sourceAccountId); Objects.requireNonNull(source); Objects.requireNonNull(economicOwner);
        Objects.requireNonNull(witnessId); Objects.requireNonNull(playerId);
        beforeLots = Map.copyOf(beforeLots); departedLots = Map.copyOf(departedLots);
        forfeitedClaims = Set.copyOf(forfeitedClaims); remaining = List.copyOf(remaining);
        var stock = beforeLots;
        if (epoch < 1 || beforeLots.isEmpty() || beforeLots.size() > 64 || departedLots.isEmpty() || departedLots.size() > 64
                || forfeitedClaims.size() > 64 || remaining.size() > 1
                || beforeLots.values().stream().anyMatch(q -> q < 1 || q > 64)
                || departedLots.entrySet().stream().anyMatch(e -> e.getValue() < 1 || e.getValue() > stock.getOrDefault(e.getKey(), 0))
                || remaining.stream().anyMatch(stack -> !stack.address().equals(new PhysicalStackAddress.WorldEntity(source.entityId()))))
            throw new IllegalArgumentException("ground pickup requires bounded exact source/postimage evidence");
    }
    @Override public String type() { return "frontier.ground_resource_player_withdrawal"; }
    public static GroundResourcePlayerWithdrawal prepare(FrontierWorldState state, SubjectId accountId, UUID player,
            UUID witness, int amount) {
        var ledger = state.inventory().fungibleResources(); var account = ledger.accounts().get(accountId);
        if (account == null || !(account.custody() instanceof ResourceCustody.Ground ground))
            throw new IllegalArgumentException("player pickup requires exact ground custody");
        var bindings = ledger.bindings().values().stream().filter(b -> b.accountId().equals(accountId)).toList();
        if (bindings.size() != 1 || !bindings.getFirst().address().equals(new PhysicalStackAddress.WorldEntity(ground.entityId()))
                || amount < 1 || amount > bindings.getFirst().quantity())
            throw new IllegalArgumentException("player pickup requires exact bound stock");
        var binding = bindings.getFirst();
        var owners = account.lotQuantities().keySet().stream().map(id -> ledger.lots().get(id).economicOwnerId()).distinct().toList();
        if (owners.size() != 1) throw new IllegalArgumentException("player pickup requires the declared unique title");
        var losses = Map.of(binding.itemKind(), amount);
        var forfeited = SourceStockDepartureSelection.claimsNeededForExit(ledger, account, losses,
                SourceStockClaimLoss.admissibleClaims(state, accountId));
        var cleared = forfeited.isEmpty() ? ledger : ledger.releaseClaims(forfeited);
        var departed = SourceStockDepartureSelection.unclaimedDepartures(cleared, cleared.accounts().get(accountId), losses);
        int left = binding.quantity() - amount;
        var remaining = left == 0 ? List.<FungiblePhysicalObservation.Stack>of() : List.of(new FungiblePhysicalObservation.Stack(binding.address(), binding.itemKind(), left));
        var receipt = new GroundResourcePlayerWithdrawal(accountId, ground, binding.authorityEpoch(), owners.getFirst(),
                witness, player, account.lotQuantities(), departed, forfeited, remaining);
        receipt.apply(state, receipt.economicOwner()); return receipt;
    }
    @Override public FrontierWorldState apply(FrontierWorldState state, SubjectId owner) {
        var ledger = state.inventory().fungibleResources(); var account = ledger.accounts().get(sourceAccountId);
        if (!owner.equals(economicOwner) || account == null || !account.custody().equals(source) || !account.lotQuantities().equals(beforeLots)
                || beforeLots.keySet().stream().anyMatch(id -> !ledger.lots().get(id).economicOwnerId().equals(economicOwner)))
            throw new IllegalArgumentException("player pickup has stale or foreign custody/title evidence");
        return SourceStockClaimLoss.settle(state, sourceAccountId, epoch, departedLots, forfeitedClaims, remaining);
    }
}
