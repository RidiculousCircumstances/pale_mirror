package io.farfrontier.palemirror.frontier.v3.model;

import java.util.HashMap;

final class ResourceClaimPartitionSupport {
    private ResourceClaimPartitionSupport() { }
    static FungibleResourceLedger apply(FungibleResourceLedger ledger, ResourceClaimPartition partition) {
        var account = ledger.accounts().get(partition.accountId()); var claim = ledger.claims().get(partition.claimId());
        if (account == null || claim == null || claim.lotQuantities().isEmpty()
                || ledger.claims().containsKey(partition.childClaimId())
                || account.claimQuantities().getOrDefault(claim.id(), 0) != claim.quantity()
                || partition.quantity() >= claim.quantity()) {
            throw new IllegalArgumentException("claim partition lacks one complete pinned allocation and a fresh child");
        }
        var remainder = new HashMap<>(claim.lotQuantities());
        partition.lotQuantities().forEach((lot, quantity) -> {
            int remaining = Math.subtractExact(remainder.getOrDefault(lot, 0), quantity);
            if (remaining < 0) throw new IllegalArgumentException("claim partition overdraws its pinned lot");
            if (remaining == 0) remainder.remove(lot); else remainder.put(lot, remaining);
        });
        var claims = new HashMap<>(ledger.claims());
        claims.put(claim.id(), new ClaimAllocation(claim.id(), claim.claimantId(), claim.economicOwnerId(), claim.itemKind(),
                claim.quantity() - partition.quantity(), remainder, claim.purpose()));
        claims.put(partition.childClaimId(), new ClaimAllocation(partition.childClaimId(), claim.claimantId(), claim.economicOwnerId(),
                claim.itemKind(), partition.quantity(), partition.lotQuantities(), claim.purpose()));
        var accountClaims = new HashMap<>(account.claimQuantities());
        accountClaims.put(claim.id(), claim.quantity() - partition.quantity()); accountClaims.put(partition.childClaimId(), partition.quantity());
        var accounts = new HashMap<>(ledger.accounts());
        accounts.put(account.id(), new CustodyAccount(account.id(), account.custody(), account.lotQuantities(), accountClaims));
        return FungibleBindingRepartition.retainLayout(ledger, ledger.lots(), claims, accounts, account.id());
    }
}
