package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Disposable queries of the sole immutable ledger; never allocation or custody authority. */
final class FungibleResourceReadIndex {
    private record Balance(SubjectId owner, String kind) { }
    private record Image(Map<SubjectId, ResourceLot> lots, Map<SubjectId, ClaimAllocation> claims,
                         Map<SubjectId, CustodyAccount> accounts,
                         Map<SubjectId, Map<Balance, Integer>> available,
                         Map<SubjectId, CustodyAccount> containers) { }
    // Retain one immutable image per owner thread, not accumulated world revisions.
    private static final ThreadLocal<Image> CURRENT = new ThreadLocal<>();
    private FungibleResourceReadIndex() { }

    static int available(FungibleResourceLedger ledger, SubjectId account, SubjectId owner, String kind) {
        Objects.requireNonNull(owner, "resource economic owner"); Objects.requireNonNull(kind, "resource item kind");
        if (!ledger.accounts().containsKey(Objects.requireNonNull(account, "custody account")))
            throw new IllegalArgumentException("unknown custody account");
        return image(ledger).available().computeIfAbsent(account,
                id -> balances(ledger, ledger.accounts().get(id))).getOrDefault(new Balance(owner, kind), 0);
    }

    static Optional<CustodyAccount> container(FungibleResourceLedger ledger, SubjectId container) {
        return Optional.ofNullable(image(ledger).containers().get(Objects.requireNonNull(container)));
    }

    private static Image image(FungibleResourceLedger ledger) {
        var current = CURRENT.get();
        if (current != null && current.lots() == ledger.lots() && current.claims() == ledger.claims()
                && current.accounts() == ledger.accounts()) return current;
        var containers = new HashMap<SubjectId, CustodyAccount>();
        for (var account : ledger.accounts().values()) {
            if (account.custody() instanceof ResourceCustody.Container holder)
                containers.putIfAbsent(holder.containerId(), account);
        }
        current = new Image(ledger.lots(), ledger.claims(), ledger.accounts(), new HashMap<>(), Map.copyOf(containers));
        CURRENT.set(current);
        return current;
    }

    private static Map<Balance, Integer> balances(FungibleResourceLedger ledger, CustodyAccount account) {
        var balances = new HashMap<Balance, Integer>();
        for (var entry : account.lotQuantities().entrySet()) {
            var lot = ledger.lots().get(entry.getKey());
            balances.merge(new Balance(lot.economicOwnerId(), lot.itemKind()), entry.getValue(), Math::addExact);
        }
        for (var entry : account.claimQuantities().entrySet()) {
            var claim = ledger.claims().get(entry.getKey());
            balances.merge(new Balance(claim.economicOwnerId(), claim.itemKind()), -entry.getValue(), Math::addExact);
        }
        return Map.copyOf(balances);
    }
}
