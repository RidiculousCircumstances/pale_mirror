package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Resource-ledger-owned implementation; commercial consent belongs to its caller. */
final class ResourceTitleTransferSupport {
    private ResourceTitleTransferSupport() { }

    static FungibleResourceLedger transfer(FungibleResourceLedger ledger, ResourceTitleTransfer transfer) {
        CustodyAccount account = ledger.accounts().get(transfer.accountId());
        ClaimAllocation claim = ledger.claims().get(transfer.claimId());
        if (account == null || claim == null || claim.lotQuantities().isEmpty()
                || !claim.economicOwnerId().equals(transfer.sourceOwnerId())
                || account.claimQuantities().getOrDefault(claim.id(), 0) != claim.quantity()
                || transfer.quantity() > claim.quantity()) {
            throw new IllegalArgumentException("title transfer lacks its exact allocated custody");
        }
        Map<SubjectId, ResourceLot> lots = new HashMap<>(ledger.lots());
        Map<SubjectId, Integer> accountLots = new HashMap<>(account.lotQuantities());
        Map<SubjectId, Integer> claimLots = new HashMap<>(claim.lotQuantities());
        Set<SubjectId> requiredSplits = new HashSet<>();
        for (var portion : transfer.portions().entrySet()) {
            ResourceLot lot = ledger.lots().get(portion.getKey());
            int count = portion.getValue();
            if (lot == null || !lot.economicOwnerId().equals(transfer.sourceOwnerId())
                    || !lot.itemKind().equals(claim.itemKind())
                    || claimLots.getOrDefault(lot.id(), 0) < count
                    || accountLots.getOrDefault(lot.id(), 0) < count) {
                throw new IllegalArgumentException("title transfer exceeds its declared lot allocation");
            }
            subtract(claimLots, lot.id(), count);
            if (count == lot.quantity()) {
                lots.put(lot.id(), lot.withEconomicOwner(transfer.destinationOwnerId()));
            } else {
                requiredSplits.add(lot.id());
                SubjectId childId = transfer.splitLotIds().get(lot.id());
                if (childId == null || ledger.lots().containsKey(childId)) {
                    throw new IllegalArgumentException("partial title transfer requires a fresh explicit split identity");
                }
                ResourceLot child = lot.splitChild(childId, count).withEconomicOwner(transfer.destinationOwnerId());
                lots.put(lot.id(), lot.withQuantity(lot.quantity() - count)); lots.put(child.id(), child);
                subtract(accountLots, lot.id(), count); accountLots.put(child.id(), count);
            }
        }
        if (!requiredSplits.equals(transfer.splitLotIds().keySet())) {
            throw new IllegalArgumentException("title transfer split declarations do not match partial lots");
        }
        Map<SubjectId, ClaimAllocation> claims = new HashMap<>(ledger.claims());
        Map<SubjectId, Integer> accountClaims = new HashMap<>(account.claimQuantities());
        int remaining = claim.quantity() - transfer.quantity();
        if (remaining == 0) { claims.remove(claim.id()); accountClaims.remove(claim.id()); }
        else {
            claims.put(claim.id(), new ClaimAllocation(claim.id(), claim.claimantId(), claim.economicOwnerId(),
                    claim.itemKind(), remaining, claimLots, claim.purpose()));
            accountClaims.put(claim.id(), remaining);
        }
        Map<SubjectId, CustodyAccount> accounts = new HashMap<>(ledger.accounts());
        accounts.put(account.id(), account.withQuantities(accountLots, accountClaims));
        return FungibleBindingRepartition.retainLayout(ledger, lots, claims, accounts, account.id());
    }

    private static void subtract(Map<SubjectId, Integer> values, SubjectId id, int quantity) {
        int remaining = Math.subtractExact(values.getOrDefault(id, 0), quantity);
        if (remaining < 0) throw new IllegalArgumentException("title transfer overdraws its allocation");
        if (remaining == 0) values.remove(id); else values.put(id, remaining);
    }
}
