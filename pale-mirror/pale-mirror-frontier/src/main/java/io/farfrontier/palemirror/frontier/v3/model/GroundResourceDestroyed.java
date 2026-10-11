package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Causal native entity destruction; unloading a chunk is never evidence of destruction. */
public record GroundResourceDestroyed(SubjectId sourceAccountId, ResourceCustody.Ground source, long epoch,
        SubjectId economicOwner, UUID witnessId, String nativeReason, Map<SubjectId, Integer> lots,
        Set<SubjectId> forfeitedClaims) implements GroundStockExit {
    public GroundResourceDestroyed {
        Objects.requireNonNull(sourceAccountId); Objects.requireNonNull(source); Objects.requireNonNull(economicOwner); Objects.requireNonNull(witnessId);
        lots = Map.copyOf(lots); forfeitedClaims = Set.copyOf(forfeitedClaims);
        if (epoch < 1 || !Set.of("KILLED", "DISCARDED").contains(nativeReason) || lots.isEmpty() || lots.size() > 64
                || lots.values().stream().anyMatch(q -> q == null || q < 1 || q > 64) || forfeitedClaims.size() > 64)
            throw new IllegalArgumentException("ground loss requires bounded exact native destruction evidence");
    }
    @Override public String type() { return "frontier.ground_resource_destroyed"; }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId owner) {
        var ledger = state.inventory().fungibleResources(); var account = ledger.accounts().get(sourceAccountId);
        var bindings = ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(sourceAccountId)).toList();
        if (!owner.equals(economicOwner) || account == null || !account.custody().equals(source) || !account.lotQuantities().equals(lots)
                || !account.claimQuantities().keySet().equals(forfeitedClaims) || bindings.size() != 1 || bindings.getFirst().authorityEpoch() != epoch
                || !bindings.getFirst().address().equals(new PhysicalStackAddress.WorldEntity(source.entityId()))
                || lots.keySet().stream().anyMatch(id -> !ledger.lots().get(id).economicOwnerId().equals(economicOwner)))
            throw new IllegalArgumentException("ground destruction has stale or foreign custody, title or reservation evidence");
        return SourceStockClaimLoss.settle(state, sourceAccountId, epoch, lots, forfeitedClaims, List.of());
    }
}
