package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable source of truth for ordinary resources.  Lots carry economic lineage, allocations
 * carry stable reservations, accounts carry custody, and bindings only describe a current HOT
 * Vanilla layout.  No map here is a derived stock counter.
 */
public record FungibleResourceLedger(Map<SubjectId, ResourceLot> lots, Map<SubjectId, ClaimAllocation> claims,
                                    Map<SubjectId, CustodyAccount> accounts, Map<SubjectId, PhysicalStackBinding> bindings) {
    public static final int MAX_LOTS = 16_384;
    public static final int MAX_CLAIMS = 16_384;
    public static final int MAX_ACCOUNTS = 16_384;
    public static final int MAX_BINDINGS = 16_384;

    public FungibleResourceLedger {
        lots = Map.copyOf(lots); claims = Map.copyOf(claims); accounts = Map.copyOf(accounts); bindings = Map.copyOf(bindings);
        if (lots.size() > MAX_LOTS || claims.size() > MAX_CLAIMS || accounts.size() > MAX_ACCOUNTS || bindings.size() > MAX_BINDINGS) {
            throw new IllegalArgumentException("fungible resource retention limit exceeded");
        }
        requireKeys(lots, ResourceLot::id, "resource lot"); requireKeys(claims, ClaimAllocation::id, "claim allocation");
        requireKeys(accounts, CustodyAccount::id, "custody account"); requireKeys(bindings, PhysicalStackBinding::id, "physical stack binding");
        validateAccounts(lots, claims, accounts); validateBindings(lots, claims, accounts, bindings);
    }

    public static FungibleResourceLedger empty() { return new FungibleResourceLedger(Map.of(), Map.of(), Map.of(), Map.of()); }

    /** Creates one initial lot/account pair; genesis is the only normal resource mint boundary. */
    public FungibleResourceLedger issue(ResourceLot lot, CustodyAccount account) {
        Objects.requireNonNull(lot, "issued lot"); Objects.requireNonNull(account, "issued account");
        if (lots.containsKey(lot.id()) || accounts.containsKey(account.id()) || !account.lotQuantities().equals(Map.of(lot.id(), lot.quantity()))
                || !account.claimQuantities().isEmpty()) throw new IllegalArgumentException("fungible resource issue does not establish one fresh exact account");
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(lots); nextLots.put(lot.id(), lot);
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(accounts); nextAccounts.put(account.id(), account);
        return new FungibleResourceLedger(nextLots, claims, nextAccounts, bindings);
    }

    /** Reserves part of the already-accounted stock without creating a second resource balance. */
    public FungibleResourceLedger reserve(ClaimAllocation claim, SubjectId accountId) {
        Objects.requireNonNull(claim, "claim allocation"); CustodyAccount account = requireAccount(accountId);
        requireNoPhysicalBinding(account.id(), "reserve");
        if (claims.containsKey(claim.id()) || account.claimQuantities().containsKey(claim.id()) || availableFor(account, claim) < claim.quantity()) {
            throw new IllegalArgumentException("claim allocation is not backed by one exact account balance");
        }
        Map<SubjectId, ClaimAllocation> nextClaims = new HashMap<>(claims); nextClaims.put(claim.id(), claim);
        Map<SubjectId, Integer> quantities = new HashMap<>(account.claimQuantities()); quantities.put(claim.id(), claim.quantity());
        return withAccount(new CustodyAccount(account.id(), account.custody(), account.lotQuantities(), quantities), nextClaims, bindings);
    }

    /** Exact zero-sum custody transfer; the caller names both lot and claim portions. */
    public FungibleResourceLedger transfer(SubjectId fromId, SubjectId toId, Map<SubjectId, Integer> lotQuantities,
                                            Map<SubjectId, Integer> claimQuantities) {
        CustodyAccount from = requireAccount(fromId); CustodyAccount to = requireAccount(toId);
        requireNoPhysicalBinding(from.id(), "transfer"); requireNoPhysicalBinding(to.id(), "transfer");
        if (from.id().equals(to.id())) throw new IllegalArgumentException("fungible transfer requires distinct custody accounts");
        requireSubset(from.lotQuantities(), lotQuantities, "lot transfer"); requireOptionalSubset(from.claimQuantities(), claimQuantities, "claim transfer");
        if (sum(lotQuantities) != sum(claimQuantities) && !claimQuantities.isEmpty()) throw new IllegalArgumentException("claimed transfer must preserve exact resource quantity");
        Map<SubjectId, CustodyAccount> next = new HashMap<>(accounts);
        Map<SubjectId, Integer> remainingLots = subtract(from.lotQuantities(), lotQuantities);
        Map<SubjectId, Integer> remainingClaims = subtract(from.claimQuantities(), claimQuantities);
        if (remainingLots.isEmpty()) next.remove(from.id());
        else next.put(from.id(), new CustodyAccount(from.id(), from.custody(), remainingLots, remainingClaims));
        next.put(to.id(), accountWithAdded(to, lotQuantities, claimQuantities));
        return new FungibleResourceLedger(lots, claims, next, withoutBindingsFor(from.id()));
    }

    /** Opens a newly observed player/container/carrier account by moving exact extant quantities into it. */
    public FungibleResourceLedger transferToNewAccount(SubjectId fromId, CustodyAccount destination) {
        CustodyAccount from = requireAccount(fromId); Objects.requireNonNull(destination, "new custody account");
        requireNoPhysicalBinding(from.id(), "transfer");
        if (accounts.containsKey(destination.id()) || from.id().equals(destination.id())) throw new IllegalArgumentException("new custody account identity is already live");
        requireSubset(from.lotQuantities(), destination.lotQuantities(), "new custody account lots");
        requireOptionalSubset(from.claimQuantities(), destination.claimQuantities(), "new custody account claims");
        Map<SubjectId, CustodyAccount> next = new HashMap<>(accounts);
        Map<SubjectId, Integer> remainingLots = subtract(from.lotQuantities(), destination.lotQuantities());
        Map<SubjectId, Integer> remainingClaims = subtract(from.claimQuantities(), destination.claimQuantities());
        if (remainingLots.isEmpty()) next.remove(from.id());
        else next.put(from.id(), new CustodyAccount(from.id(), from.custody(), remainingLots, remainingClaims));
        next.put(destination.id(), destination);
        return new FungibleResourceLedger(lots, claims, next, withoutBindingsFor(from.id()));
    }

    /** Changes canonical lot lineage while retaining total resource quantity and all reservations. */
    public FungibleResourceLedger split(SubjectId accountId, SubjectId sourceLotId, ResourceLot child, int quantity) {
        CustodyAccount account = requireAccount(accountId); ResourceLot source = requireLot(sourceLotId);
        requireNoPhysicalBinding(account.id(), "split");
        if (lots.containsKey(child.id()) || !source.economicOwnerId().equals(child.economicOwnerId()) || !source.itemKind().equals(child.itemKind())
                || !source.provenance().equals(child.provenance()) || child.quantity() != quantity || quantity < 1 || quantity >= source.quantity()
                || !source.splitChild(child.id(), quantity).equals(child) || account.lotQuantities().getOrDefault(sourceLotId, 0) < quantity) {
            throw new IllegalArgumentException("fungible lot split is not an exact lineage-preserving account mutation");
        }
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(lots); nextLots.put(sourceLotId, source.withQuantity(source.quantity() - quantity)); nextLots.put(child.id(), child);
        Map<SubjectId, Integer> nextQuantities = new HashMap<>(account.lotQuantities()); nextQuantities.merge(sourceLotId, -quantity, Integer::sum); nextQuantities.put(child.id(), quantity);
        return withAccount(new CustodyAccount(account.id(), account.custody(), nextQuantities, account.claimQuantities()), claims, bindings, nextLots);
    }

    /** Merges two co-located interchangeable lots into fresh bounded lineage without changing any allocation. */
    public FungibleResourceLedger merge(SubjectId accountId, SubjectId leftId, SubjectId rightId, ResourceLot merged) {
        CustodyAccount account = requireAccount(accountId); ResourceLot left = requireLot(leftId); ResourceLot right = requireLot(rightId);
        requireNoPhysicalBinding(account.id(), "merge");
        if (leftId.equals(rightId) || lots.containsKey(merged.id()) || !left.economicOwnerId().equals(right.economicOwnerId())
                || !left.itemKind().equals(right.itemKind()) || !left.provenance().equals(right.provenance())
                || !merged.economicOwnerId().equals(left.economicOwnerId()) || !merged.itemKind().equals(left.itemKind())
                || !merged.provenance().equals(left.provenance()) || merged.quantity() != left.quantity() + right.quantity()
                || account.lotQuantities().getOrDefault(leftId, 0) != left.quantity() || account.lotQuantities().getOrDefault(rightId, 0) != right.quantity()) {
            throw new IllegalArgumentException("fungible lot merge is not an exact co-located mutation");
        }
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(lots); nextLots.remove(leftId); nextLots.remove(rightId); nextLots.put(merged.id(), merged);
        Map<SubjectId, Integer> nextQuantities = new HashMap<>(account.lotQuantities()); nextQuantities.remove(leftId); nextQuantities.remove(rightId); nextQuantities.put(merged.id(), merged.quantity());
        return withAccount(new CustodyAccount(account.id(), account.custody(), nextQuantities, account.claimQuantities()), claims, bindings, nextLots);
    }

    /** Replaces only one account's current HOT stack layout after a matching fenced observation. */
    public FungibleResourceLedger rebind(SubjectId accountId, long authorityEpoch, List<PhysicalStackBinding> replacements) {
        CustodyAccount account = requireAccount(accountId); Objects.requireNonNull(replacements, "physical binding replacements");
        if (replacements.isEmpty() || replacements.stream().anyMatch(binding -> !binding.accountId().equals(accountId) || binding.authorityEpoch() != authorityEpoch)) {
            throw new IllegalArgumentException("physical bindings do not match their current custody authority");
        }
        if (bindings.values().stream().anyMatch(binding -> binding.accountId().equals(accountId) && binding.authorityEpoch() != authorityEpoch)) {
            throw new IllegalArgumentException("physical bindings are stale for the current custody authority");
        }
        Map<SubjectId, PhysicalStackBinding> next = new HashMap<>(bindings);
        bindings.values().stream().filter(binding -> binding.accountId().equals(account.id())).map(PhysicalStackBinding::id).forEach(next::remove);
        for (PhysicalStackBinding binding : replacements) {
            if (next.put(binding.id(), binding) != null) throw new IllegalArgumentException("physical stack binding identity is already live");
        }
        return new FungibleResourceLedger(lots, claims, accounts, next);
    }

    /** Releases only the current epoch after its durable physical checkpoint; a stale lease cannot reopen COLD spending. */
    public FungibleResourceLedger releaseBindings(SubjectId accountId, long authorityEpoch) {
        requireAccount(accountId);
        List<PhysicalStackBinding> current = bindings.values().stream().filter(binding -> binding.accountId().equals(accountId)).toList();
        if (current.isEmpty() || current.stream().anyMatch(binding -> binding.authorityEpoch() != authorityEpoch)) {
            throw new IllegalArgumentException("physical custody release does not match the current authority epoch");
        }
        Map<SubjectId, PhysicalStackBinding> next = withoutBindingsFor(accountId);
        return new FungibleResourceLedger(lots, claims, accounts, next);
    }

    /** A typed destruction sink removes only the named custody portions; nothing is rolled back or minted. */
    public FungibleResourceLedger destroy(SubjectId accountId, Map<SubjectId, Integer> lotQuantities, Map<SubjectId, Integer> claimQuantities) {
        CustodyAccount account = requireAccount(accountId); requireSubset(account.lotQuantities(), lotQuantities, "destruction lots");
        requireNoPhysicalBinding(account.id(), "destroy");
        requireOptionalSubset(account.claimQuantities(), claimQuantities, "destruction claims");
        if (!claimQuantities.isEmpty() && sum(lotQuantities) != sum(claimQuantities)) throw new IllegalArgumentException("claimed destruction must consume the same exact quantity");
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(lots); Map<SubjectId, ClaimAllocation> nextClaims = new HashMap<>(claims);
        lotQuantities.forEach((id, quantity) -> { ResourceLot lot = requireLot(id); int remaining = lot.quantity() - quantity; if (remaining == 0) nextLots.remove(id); else nextLots.put(id, lot.withQuantity(remaining)); });
        claimQuantities.forEach((id, quantity) -> { ClaimAllocation claim = claims.get(id); int remaining = claim.quantity() - quantity; if (remaining == 0) nextClaims.remove(id); else nextClaims.put(id, new ClaimAllocation(claim.id(), claim.claimantId(), claim.economicOwnerId(), claim.itemKind(), remaining)); });
        Map<SubjectId, Integer> nextLotsAtAccount = subtract(account.lotQuantities(), lotQuantities); Map<SubjectId, Integer> nextClaimsAtAccount = subtract(account.claimQuantities(), claimQuantities);
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(accounts);
        if (nextLotsAtAccount.isEmpty()) nextAccounts.remove(account.id());
        else nextAccounts.put(account.id(), new CustodyAccount(account.id(), account.custody(), nextLotsAtAccount, nextClaimsAtAccount));
        Map<SubjectId, PhysicalStackBinding> nextBindings = new HashMap<>(bindings);
        bindings.values().stream().filter(binding -> binding.accountId().equals(account.id())).map(PhysicalStackBinding::id).forEach(nextBindings::remove);
        return new FungibleResourceLedger(nextLots, nextClaims, nextAccounts, nextBindings);
    }

    public int totalQuantity(SubjectId owner, String kind) {
        return lots.values().stream().filter(lot -> lot.economicOwnerId().equals(owner) && lot.itemKind().equals(kind)).mapToInt(ResourceLot::quantity).sum();
    }

    private static void validateAccounts(Map<SubjectId, ResourceLot> lots, Map<SubjectId, ClaimAllocation> claims,
                                         Map<SubjectId, CustodyAccount> accounts) {
        Map<SubjectId, Integer> lotTotals = new HashMap<>(); Map<SubjectId, Integer> claimTotals = new HashMap<>();
        for (CustodyAccount account : accounts.values()) {
            account.lotQuantities().forEach((id, quantity) -> { if (!lots.containsKey(id)) throw new IllegalArgumentException("custody account references an unknown lot"); lotTotals.merge(id, quantity, Integer::sum); });
            account.claimQuantities().forEach((id, quantity) -> { ClaimAllocation claim = claims.get(id); if (claim == null) throw new IllegalArgumentException("custody account references an unknown claim"); claimTotals.merge(id, quantity, Integer::sum);
                int compatible = account.lotQuantities().entrySet().stream().filter(entry -> { ResourceLot lot = lots.get(entry.getKey()); return lot.economicOwnerId().equals(claim.economicOwnerId()) && lot.itemKind().equals(claim.itemKind()); }).mapToInt(Map.Entry::getValue).sum();
                if (quantity > compatible) throw new IllegalArgumentException("claim allocation exceeds compatible account stock"); });
        }
        lots.forEach((id, lot) -> { if (lotTotals.getOrDefault(id, 0) != lot.quantity()) throw new IllegalArgumentException("resource lot must have exact canonical custody"); });
        claims.forEach((id, claim) -> { if (claimTotals.getOrDefault(id, 0) != claim.quantity()) throw new IllegalArgumentException("claim allocation must have exact canonical custody"); });
        for (CustodyAccount account : accounts.values()) {
            Map<String, Integer> claimedByKind = new HashMap<>(); Map<String, Integer> stockByKind = new HashMap<>();
            account.claimQuantities().forEach((id, quantity) -> { ClaimAllocation claim = claims.get(id); claimedByKind.merge(claim.economicOwnerId().value() + "|" + claim.itemKind(), quantity, Integer::sum); });
            account.lotQuantities().forEach((id, quantity) -> { ResourceLot lot = lots.get(id); stockByKind.merge(lot.economicOwnerId().value() + "|" + lot.itemKind(), quantity, Integer::sum); });
            claimedByKind.forEach((key, quantity) -> { if (quantity > stockByKind.getOrDefault(key, 0)) throw new IllegalArgumentException("claim allocations exceed exact account stock"); });
        }
    }

    private static void validateBindings(Map<SubjectId, ResourceLot> lots, Map<SubjectId, ClaimAllocation> claims,
                                         Map<SubjectId, CustodyAccount> accounts,
                                         Map<SubjectId, PhysicalStackBinding> bindings) {
        Map<PhysicalStackAddress, SubjectId> addresses = new HashMap<>(); Map<SubjectId, Map<SubjectId, Integer>> boundLots = new HashMap<>();
        Map<SubjectId, Map<SubjectId, Integer>> boundClaims = new HashMap<>();
        for (PhysicalStackBinding binding : bindings.values()) {
            CustodyAccount account = accounts.get(binding.accountId());
            if (account == null || addresses.put(binding.address(), binding.id()) != null || binding.quantity() > 64) throw new IllegalArgumentException("physical stack binding is not one unique current stack");
            binding.lotQuantities().forEach((id, quantity) -> { ResourceLot lot = lots.get(id); if (lot == null || !lot.itemKind().equals(binding.itemKind())) throw new IllegalArgumentException("physical stack binding has incompatible lot evidence");
                boundLots.computeIfAbsent(account.id(), ignored -> new HashMap<>()).merge(id, quantity, Integer::sum); });
            binding.claimQuantities().forEach((id, quantity) -> { ClaimAllocation claim = claims.get(id); if (claim == null || !claim.itemKind().equals(binding.itemKind())) throw new IllegalArgumentException("physical stack binding has incompatible claim evidence");
                boundClaims.computeIfAbsent(account.id(), ignored -> new HashMap<>()).merge(id, quantity, Integer::sum); });
        }
        boundLots.forEach((account, quantities) -> requireSubset(accounts.get(account).lotQuantities(), quantities, "physical stack lots"));
        boundClaims.forEach((account, quantities) -> requireSubset(accounts.get(account).claimQuantities(), quantities, "physical stack claims"));
    }

    private ResourceLot requireLot(SubjectId id) { ResourceLot lot = lots.get(Objects.requireNonNull(id, "resource lot")); if (lot == null) throw new IllegalArgumentException("unknown resource lot"); return lot; }
    private CustodyAccount requireAccount(SubjectId id) { CustodyAccount account = accounts.get(Objects.requireNonNull(id, "custody account")); if (account == null) throw new IllegalArgumentException("unknown custody account"); return account; }
    private Integer availableFor(CustodyAccount account, ClaimAllocation claim) {
        return account.lotQuantities().entrySet().stream().filter(entry -> { ResourceLot lot = lots.get(entry.getKey()); return lot.economicOwnerId().equals(claim.economicOwnerId()) && lot.itemKind().equals(claim.itemKind()); }).mapToInt(Map.Entry::getValue).sum();
    }
    private FungibleResourceLedger withAccount(CustodyAccount account, Map<SubjectId, ClaimAllocation> nextClaims, Map<SubjectId, PhysicalStackBinding> nextBindings) { return withAccount(account, nextClaims, nextBindings, lots); }
    private FungibleResourceLedger withAccount(CustodyAccount account, Map<SubjectId, ClaimAllocation> nextClaims, Map<SubjectId, PhysicalStackBinding> nextBindings, Map<SubjectId, ResourceLot> nextLots) {
        Map<SubjectId, CustodyAccount> next = new HashMap<>(accounts); next.put(account.id(), account); return new FungibleResourceLedger(nextLots, nextClaims, next, nextBindings);
    }
    private Map<SubjectId, PhysicalStackBinding> withoutBindingsFor(SubjectId accountId) {
        Map<SubjectId, PhysicalStackBinding> next = new HashMap<>(bindings);
        bindings.values().stream().filter(binding -> binding.accountId().equals(accountId)).map(PhysicalStackBinding::id).forEach(next::remove);
        return next;
    }
    private void requireNoPhysicalBinding(SubjectId accountId, String operation) {
        if (bindings.values().stream().anyMatch(binding -> binding.accountId().equals(accountId))) {
            throw new IllegalStateException("fungible " + operation + " is fenced by active physical custody");
        }
    }
    private static CustodyAccount accountWithAdded(CustodyAccount account, Map<SubjectId, Integer> lots, Map<SubjectId, Integer> claims) {
        Map<SubjectId, Integer> nextLots = new HashMap<>(account.lotQuantities()); lots.forEach((id, quantity) -> nextLots.merge(id, quantity, Integer::sum));
        Map<SubjectId, Integer> nextClaims = new HashMap<>(account.claimQuantities()); claims.forEach((id, quantity) -> nextClaims.merge(id, quantity, Integer::sum));
        return new CustodyAccount(account.id(), account.custody(), nextLots, nextClaims);
    }
    private static Map<SubjectId, Integer> subtract(Map<SubjectId, Integer> source, Map<SubjectId, Integer> removed) {
        Map<SubjectId, Integer> next = new HashMap<>(source); removed.forEach((id, quantity) -> { int remaining = next.get(id) - quantity; if (remaining == 0) next.remove(id); else next.put(id, remaining); }); return next;
    }
    private static void requireSubset(Map<SubjectId, Integer> source, Map<SubjectId, Integer> requested, String label) {
        if (requested == null || requested.isEmpty() || requested.values().stream().anyMatch(value -> value == null || value < 1)) throw new IllegalArgumentException(label + " must name a positive exact quantity");
        requested.forEach((id, quantity) -> { if (source.getOrDefault(id, 0) < quantity) throw new IllegalArgumentException(label + " exceeds its current custody"); });
    }
    private static void requireOptionalSubset(Map<SubjectId, Integer> source, Map<SubjectId, Integer> requested, String label) {
        if (requested == null) throw new IllegalArgumentException(label + " is required");
        if (!requested.isEmpty()) requireSubset(source, requested, label);
    }
    private static int sum(Map<SubjectId, Integer> quantities) { return quantities.values().stream().mapToInt(Integer::intValue).sum(); }
    private static <T> void requireKeys(Map<SubjectId, T> values, java.util.function.Function<T, SubjectId> id, String label) {
        values.forEach((key, value) -> { if (value == null || !key.equals(id.apply(value))) throw new IllegalArgumentException(label + " map key does not match identity"); });
    }
}
