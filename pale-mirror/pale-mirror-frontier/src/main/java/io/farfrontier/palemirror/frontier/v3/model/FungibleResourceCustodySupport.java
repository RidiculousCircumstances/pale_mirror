package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Narrow canonical queries shared by recipe and hive owners; it never infers stock from Vanilla. */
public final class FungibleResourceCustodySupport {
    private FungibleResourceCustodySupport() { }
    /** Shared allocation authority query; neither recipe nor commercial policy belongs here. */
    public static boolean canReserve(FrontierWorldState state, LotSelection input) {
        var resources = state.inventory().fungibleResources();
        var account = resources.accounts().get(input.accountId());
        if (account == null || !(account.custody() instanceof ResourceCustody.Container container)
                || ReferenceContainerCustody.blocksCanonicalUse(state, container.containerId())) return false;
        ResourceLot first = resources.lots().get(input.firstLotId());
        if (first == null || resources.unclaimedQuantity(input.accountId(), first.economicOwnerId(), first.itemKind()) < input.quantity()) return false;
        for (var entry : input.lotQuantities().entrySet()) {
            ResourceLot lot = resources.lots().get(entry.getKey());
            if (lot == null || !lot.economicOwnerId().equals(first.economicOwnerId()) || !lot.itemKind().equals(first.itemKind())) return false;
            int pinned = account.claimQuantities().keySet().stream().map(resources.claims()::get)
                    .mapToInt(claim -> claim.lotQuantities().getOrDefault(entry.getKey(), 0)).sum();
            if (entry.getValue() > account.lotQuantities().getOrDefault(entry.getKey(), 0) - pinned) return false;
        }
        var bindings = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(input.accountId())).toList();
        if (!ReferenceContainerCustody.hasLiveCustody(state, container.containerId())) return bindings.isEmpty();
        if (!ReferenceContainerCustody.hasOperationalCustody(state, container.containerId())) return false;
        long epoch = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(container.containerId())).authorityEpoch();
        return !bindings.isEmpty() && bindings.stream().allMatch(binding -> binding.authorityEpoch() == epoch)
                && input.lotQuantities().entrySet().stream().allMatch(entry -> bindings.stream()
                    .mapToInt(binding -> binding.lotQuantities().getOrDefault(entry.getKey(), 0)).sum() >= entry.getValue());
    }

    public static Optional<LotAtContainer> firstAtContainer(FrontierWorldState state, SubjectId containerId, String itemKind, int minimumQuantity) {
        Objects.requireNonNull(state, "resource state"); Objects.requireNonNull(containerId, "resource container");
        if (itemKind == null || itemKind.isBlank() || minimumQuantity < 1) throw new IllegalArgumentException("resource query is invalid");
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        return resources.accounts().values().stream().filter(account -> account.custody() instanceof ResourceCustody.Container container
                        && container.containerId().equals(containerId)).flatMap(account -> account.lotQuantities().entrySet().stream()
                        .filter(entry -> entry.getValue() >= minimumQuantity).map(entry -> new LotAtContainer(account.id(), resources.lots().get(entry.getKey()), entry.getValue())))
                .filter(value -> value.lot().itemKind().equals(itemKind)).sorted(Comparator.comparing(value -> value.lot().id())).findFirst();
    }

    public static Optional<CustodyAccount> accountAtContainer(FrontierWorldState state, SubjectId containerId) {
        Objects.requireNonNull(state, "resource state"); Objects.requireNonNull(containerId, "resource container");
        return state.inventory().fungibleResources().accounts().values().stream().filter(account -> account.custody() instanceof ResourceCustody.Container container
                && container.containerId().equals(containerId)).findFirst();
    }

    /**
     * Deterministic recipe candidate, not a reservation. The caller must commit a claim and
     * retain this exact lot map before any physical write; account-level unclaimed stock alone
     * cannot protect the chosen lot identities from a competing consumer.
     */
    public static Optional<LotSelection> selectAtContainer(FrontierWorldState state, SubjectId containerId,
                                                           SubjectId economicOwnerId, String itemKind, int quantity) {
        Objects.requireNonNull(state, "resource state"); Objects.requireNonNull(containerId, "resource container");
        Objects.requireNonNull(economicOwnerId, "resource owner");
        if (itemKind == null || itemKind.isBlank() || quantity < 1 || quantity > 64) {
            throw new IllegalArgumentException("recipe lot selection is invalid");
        }
        var account = accountAtContainer(state, containerId).orElse(null);
        if (account == null) return Optional.empty();
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        if (resources.unclaimedQuantity(account.id(), economicOwnerId, itemKind) < quantity) return Optional.empty();
        Map<SubjectId, Integer> pinned = new java.util.HashMap<>();
        account.claimQuantities().keySet().forEach(claimId -> {
            ClaimAllocation claim = resources.claims().get(claimId);
            claim.lotQuantities().forEach((lotId, count) -> pinned.merge(lotId, count, Integer::sum));
        });
        Map<SubjectId, Integer> selected = new LinkedHashMap<>();
        int remaining = quantity;
        for (var entry : account.lotQuantities().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            ResourceLot lot = resources.lots().get(entry.getKey());
            if (!lot.economicOwnerId().equals(economicOwnerId) || !lot.itemKind().equals(itemKind)) continue;
            int take = Math.min(remaining, entry.getValue() - pinned.getOrDefault(lot.id(), 0));
            if (take > 0) selected.put(lot.id(), take);
            remaining -= take;
            if (remaining == 0) break;
        }
        return remaining == 0 ? Optional.of(new LotSelection(account.id(), selected)) : Optional.empty();
    }

    public record LotSelection(SubjectId accountId, Map<SubjectId, Integer> lotQuantities) {
        public LotSelection {
            Objects.requireNonNull(accountId, "resource account");
            lotQuantities = Map.copyOf(Objects.requireNonNull(lotQuantities, "selected resource lots"));
            if (lotQuantities.isEmpty() || lotQuantities.size() > 64 || lotQuantities.values().stream().anyMatch(value -> value == null || value < 1 || value > 64)
                    || lotQuantities.values().stream().mapToInt(Integer::intValue).sum() > 64) {
                throw new IllegalArgumentException("recipe lot selection exceeds its bounded input");
            }
        }

        public int quantity() { return lotQuantities.values().stream().mapToInt(Integer::intValue).sum(); }
        public SubjectId firstLotId() { return lotQuantities.keySet().stream().min(Comparator.naturalOrder()).orElseThrow(); }
    }

    public record LotAtContainer(SubjectId accountId, ResourceLot lot, int quantity) {
        public LotAtContainer {
            Objects.requireNonNull(accountId, "resource account"); Objects.requireNonNull(lot, "resource lot");
            if (quantity < 1 || quantity > lot.quantity()) throw new IllegalArgumentException("resource custody quantity is invalid");
        }
    }
}
