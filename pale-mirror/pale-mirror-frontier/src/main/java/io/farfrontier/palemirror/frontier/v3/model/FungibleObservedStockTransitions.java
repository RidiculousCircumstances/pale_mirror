package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** HOT stock changes validated against one current physical binding epoch. */
final class FungibleObservedStockTransitions {
    private FungibleObservedStockTransitions() { }

    static FungibleResourceLedger destroy(FungibleResourceLedger ledger, SubjectId accountId,
                                          long authorityEpoch, Map<SubjectId, Integer> lotQuantities,
                                          Map<SubjectId, Integer> claimQuantities,
                                          List<FungiblePhysicalObservation.Stack> remaining) {
        CustodyAccount account = requireAccount(ledger, accountId);
        FungibleResourceLedger.requireSubset(account.lotQuantities(), lotQuantities, "observed destruction lots");
        FungibleResourceLedger.requireOptionalSubset(account.claimQuantities(), claimQuantities, "observed destruction claims");
        if (authorityEpoch < 1 || sum(lotQuantities) != sum(claimQuantities))
            throw new IllegalArgumentException("observed destruction must consume one claimed exact portion");
        requireCurrentBinding(ledger, account.id(), authorityEpoch, "observed destruction");
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(ledger.lots());
        lotQuantities.forEach((id, quantity) -> {
            ResourceLot lot = requireLot(ledger, id);
            int left = lot.quantity() - quantity;
            if (left == 0) nextLots.remove(id); else nextLots.put(id, lot.withQuantity(left));
        });
        Map<SubjectId, ClaimAllocation> nextClaims = new HashMap<>(ledger.claims());
        claimQuantities.forEach((id, quantity) -> {
            ClaimAllocation claim = ledger.claims().get(id);
            int left = claim.quantity() - quantity;
            if (!claim.lotQuantities().isEmpty() && left != 0)
                throw new IllegalArgumentException("pinned claim cannot be partly destroyed");
            if (left == 0) nextClaims.remove(id); else nextClaims.put(id, claim.withQuantity(left));
        });
        Map<SubjectId, Integer> remainingLots = subtract(account.lotQuantities(), lotQuantities);
        Map<SubjectId, Integer> remainingClaims = subtract(account.claimQuantities(), claimQuantities);
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(ledger.accounts());
        if (remainingLots.isEmpty()) {
            if (!remaining.isEmpty()) throw new IllegalArgumentException("empty observed destruction account retains physical stacks");
            nextAccounts.remove(account.id());
            return new FungibleResourceLedger(nextLots, nextClaims, nextAccounts, withoutBindingsFor(ledger, account.id()));
        }
        nextAccounts.put(account.id(), account.withQuantities(remainingLots, remainingClaims));
        FungibleResourceLedger reduced = new FungibleResourceLedger(nextLots, nextClaims, nextAccounts,
                withoutBindingsFor(ledger, account.id()));
        return reduced.rebind(account.id(), authorityEpoch,
                FungiblePhysicalObservation.bind(reduced, account.id(), authorityEpoch, remaining));
    }

    static FungibleResourceLedger depart(FungibleResourceLedger ledger, SubjectId accountId,
                                         long authorityEpoch, Map<SubjectId, Integer> departedLots,
                                         List<FungiblePhysicalObservation.Stack> remaining) {
        CustodyAccount account = requireAccount(ledger, accountId);
        Objects.requireNonNull(departedLots, "departed lots");
        Objects.requireNonNull(remaining, "observed remaining stacks");
        if (!(account.custody() instanceof ResourceCustody.Container) || authorityEpoch < 1 || departedLots.isEmpty())
            throw new IllegalArgumentException("external stock exit requires a current container");
        FungibleResourceLedger.requireSubset(account.lotQuantities(), departedLots, "external stock exit lots");
        requireCurrentBinding(ledger, account.id(), authorityEpoch, "external stock exit");
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(ledger.lots());
        departedLots.forEach((id, quantity) -> {
            ResourceLot lot = requireLot(ledger, id);
            int left = lot.quantity() - quantity;
            if (left == 0) nextLots.remove(id); else nextLots.put(id, lot.withQuantity(left));
        });
        Map<SubjectId, Integer> retained = subtract(account.lotQuantities(), departedLots);
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(ledger.accounts());
        if (retained.isEmpty()) {
            if (!remaining.isEmpty()) throw new IllegalArgumentException("empty stock exit retains physical stacks");
            nextAccounts.remove(account.id());
            return new FungibleResourceLedger(nextLots, ledger.claims(), nextAccounts,
                    withoutBindingsFor(ledger, account.id()));
        }
        nextAccounts.put(account.id(), account.withQuantities(retained, account.claimQuantities()));
        FungibleResourceLedger reduced = new FungibleResourceLedger(nextLots, ledger.claims(), nextAccounts,
                withoutBindingsFor(ledger, account.id()));
        return reduced.rebind(account.id(), authorityEpoch,
                FungiblePhysicalObservation.bind(reduced, account.id(), authorityEpoch, remaining));
    }

    static FungibleResourceLedger contribute(FungibleResourceLedger ledger, SubjectId accountId,
                                             long authorityEpoch, ResourceLot contribution,
                                             List<FungiblePhysicalObservation.Stack> observed) {
        Objects.requireNonNull(accountId, "contribution account");
        CustodyAccount account = ledger.accounts().get(accountId);
        Objects.requireNonNull(contribution, "contributed resource lot");
        Objects.requireNonNull(observed, "observed contributed layout");
        SubjectId depot = FrontierWorldState.depotId(contribution.economicOwnerId());
        if (authorityEpoch < 1 || ledger.lots().containsKey(contribution.id()) || !contribution.lineage().isEmpty()
                || account != null && !account.custody().equals(new ResourceCustody.Container(depot)))
            throw new IllegalArgumentException("observed contribution requires a fresh original lot at its owner's depot");
        if (account == null) {
            if (!accountId.equals(ReferenceContainerCustody.scopeId(depot))
                    || ledger.accounts().values().stream().anyMatch(other ->
                    other.custody().equals(new ResourceCustody.Container(depot))))
                throw new IllegalArgumentException("first player gift needs its one vacant canonical depot account");
            FungibleResourceLedger created = ledger.issue(contribution, new CustodyAccount(accountId,
                    new ResourceCustody.Container(depot), Map.of(contribution.id(), contribution.quantity()), Map.of()));
            return created.rebind(accountId, authorityEpoch,
                    FungiblePhysicalObservation.bind(created, accountId, authorityEpoch, observed));
        }
        requireCurrentBinding(ledger, account.id(), authorityEpoch, "observed contribution");
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(ledger.lots());
        nextLots.put(contribution.id(), contribution);
        Map<SubjectId, Integer> quantities = new HashMap<>(account.lotQuantities());
        quantities.put(contribution.id(), contribution.quantity());
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(ledger.accounts());
        nextAccounts.put(account.id(), account.withQuantities(quantities, account.claimQuantities()));
        FungibleResourceLedger expanded = new FungibleResourceLedger(nextLots, ledger.claims(), nextAccounts,
                withoutBindingsFor(ledger, account.id()));
        return expanded.rebind(account.id(), authorityEpoch,
                FungiblePhysicalObservation.bind(expanded, account.id(), authorityEpoch, observed));
    }

    private static CustodyAccount requireAccount(FungibleResourceLedger ledger, SubjectId id) {
        CustodyAccount account = ledger.accounts().get(Objects.requireNonNull(id, "custody account"));
        if (account == null) throw new IllegalArgumentException("unknown custody account");
        return account;
    }

    private static ResourceLot requireLot(FungibleResourceLedger ledger, SubjectId id) {
        ResourceLot lot = ledger.lots().get(id);
        if (lot == null) throw new IllegalArgumentException("unknown resource lot");
        return lot;
    }

    private static void requireCurrentBinding(FungibleResourceLedger ledger, SubjectId accountId,
                                              long epoch, String operation) {
        List<PhysicalStackBinding> current = ledger.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(accountId)).toList();
        if (current.isEmpty() || current.stream().anyMatch(binding -> binding.authorityEpoch() != epoch))
            throw new IllegalArgumentException(operation + " lacks current physical authority");
    }

    private static Map<SubjectId, PhysicalStackBinding> withoutBindingsFor(FungibleResourceLedger ledger,
                                                                             SubjectId accountId) {
        Map<SubjectId, PhysicalStackBinding> next = new HashMap<>(ledger.bindings());
        ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(accountId))
                .map(PhysicalStackBinding::id).forEach(next::remove);
        return next;
    }

    private static Map<SubjectId, Integer> subtract(Map<SubjectId, Integer> source,
                                                    Map<SubjectId, Integer> removed) {
        Map<SubjectId, Integer> next = new HashMap<>(source);
        removed.forEach((id, quantity) -> {
            int left = next.get(id) - quantity;
            if (left == 0) next.remove(id); else next.put(id, left);
        });
        return next;
    }

    private static int sum(Map<SubjectId, Integer> quantities) {
        return quantities.values().stream().mapToInt(Integer::intValue).sum();
    }
}
