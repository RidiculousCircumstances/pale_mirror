package io.farfrontier.palemirror.frontier.v3.model;

import java.util.HashMap;
import java.util.Map;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Private ledger operation: retain physical addresses, epochs, quantities and fences while changing allocations. */
final class FungibleBindingRepartition {
    private FungibleBindingRepartition() { }
    static FungibleResourceLedger retainLayout(FungibleResourceLedger before,
                                               Map<SubjectId, ResourceLot> lots, Map<SubjectId, ClaimAllocation> claims,
                                               Map<SubjectId, CustodyAccount> accounts, SubjectId accountId) {
        var current = before.bindings().values().stream().filter(b -> b.accountId().equals(accountId)).toList();
        var bindings = new HashMap<>(before.bindings()); current.forEach(b -> bindings.remove(b.id()));
        // This intermediate never leaves the ledger operation and never releases physical authority.
        var unbound = new FungibleResourceLedger(lots, claims, accounts, bindings);
        if (current.isEmpty()) return unbound;
        Map<PhysicalStackAddress, PhysicalStackBinding> originals = new HashMap<>();
        current.forEach(b -> originals.put(b.address(), b));
        long epoch = current.getFirst().authorityEpoch();
        if (current.stream().anyMatch(b -> b.authorityEpoch() != epoch)) {
            throw new IllegalArgumentException("allocation change has mixed physical authority epochs");
        }
        var layout = current.stream().map(b -> new FungiblePhysicalObservation.Stack(b.address(), b.itemKind(), b.quantity())).toList();
        for (var repartitioned : FungiblePhysicalObservation.bind(unbound, accountId, epoch, layout)) {
            var original = originals.get(repartitioned.address());
            bindings.put(original.id(), new PhysicalStackBinding(original.id(), original.accountId(), original.address(),
                    original.authorityEpoch(), original.itemKind(), repartitioned.lotQuantities(),
                    repartitioned.claimQuantities(), original.playerSaveFence()));
        }
        return new FungibleResourceLedger(lots, claims, accounts, bindings);
    }
}
