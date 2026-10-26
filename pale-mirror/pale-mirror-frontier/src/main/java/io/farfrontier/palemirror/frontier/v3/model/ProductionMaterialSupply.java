package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Admission-only complete material sourcing. Continuation uses the selected accounts/lots, never this discovery again. */
public final class ProductionMaterialSupply {
    public record Source(String itemKind, SubjectId containerId, FungibleResourceCustodySupport.LotSelection selection) {
        public Source { ProductionRecipe.requireItemKey(itemKind); Objects.requireNonNull(containerId); Objects.requireNonNull(selection); }
    }
    public record Bundle(List<Source> sources) {
        public Bundle {
            sources = List.copyOf(sources);
            if (sources.isEmpty() || sources.size() > 64 || sources.stream().mapToLong(source -> source.selection().quantity()).sum() > 64)
                throw new IllegalArgumentException("production supply needs a bounded complete pickup bundle");
        }
        public int quantity() { return sources.stream().mapToInt(source -> source.selection().quantity()).sum(); }
        public Map<String, Integer> quantities() {
            var quantities = new TreeMap<String, Integer>();
            sources.forEach(source -> quantities.merge(source.itemKind(), source.selection().quantity(), Math::addExact));
            return Map.copyOf(quantities);
        }
    }
    public static Optional<Bundle> select(FrontierWorldState state, SubjectId owner, ProductionStationSpec station,
            ProductionRecipe recipe, int batches) {
        var ledger = state.inventory().fungibleResources();
        var candidates = new TreeMap<String, List<Source>>(); var supply = new TreeMap<String, Integer>();
        for (var kind : recipe.ingredients().stream().flatMap(input -> input.acceptedItems().stream()).distinct().sorted().toList()) {
            var containers = ResourceStorageSelection.containers(state, owner, kind, Set.of(ResourceStorageRole.WAREHOUSE)).stream().sorted().toList();
            var accounts = new ArrayList<CustodyAccount>();
            FungibleResourceCustodySupport.accountAtContainer(state, station.containerId(), ContainerSectionId.INPUT).ifPresent(accounts::add);
            for (var container : containers) FungibleResourceCustodySupport.accountAtContainer(state, container).ifPresent(accounts::add);
            for (var account : accounts) {
                int quantity = Math.min(64, ledger.unclaimedQuantity(account.id(), owner, kind));
                if (quantity == 0) continue;
                var portion = FungibleResourceCustodySupport.selectAtAccount(ledger, account.id(), owner, kind, quantity).orElseThrow();
                if (!FungibleResourceCustodySupport.canReserve(state, portion)) continue;
                var container = ((ResourceCustody.Container) account.custody()).containerId();
                candidates.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(new Source(kind, container, portion));
                supply.merge(kind, quantity, Math::addExact);
            }
        }
        var allocation = recipe.allocate(supply, batches); if (allocation.isEmpty()) return Optional.empty();
        var sources = new ArrayList<Source>();
        for (var material : new TreeMap<>(allocation.orElseThrow().quantities()).entrySet()) {
            int remaining = material.getValue();
            for (var candidate : candidates.getOrDefault(material.getKey(), List.of())) {
                int quantity = Math.min(remaining, candidate.selection().quantity()); if (quantity == 0) break;
                var selected = FungibleResourceCustodySupport.selectAtAccount(ledger, candidate.selection().accountId(), owner, material.getKey(), quantity).orElseThrow();
                sources.add(new Source(material.getKey(), candidate.containerId(), selected)); remaining -= quantity;
            }
            if (remaining != 0) throw new IllegalArgumentException("complete recipe supply lost its selected portion");
        }
        return Optional.of(new Bundle(sources));
    }
    public static Optional<Bundle> atAccount(FungibleResourceLedger ledger, CustodyAccount account, SubjectId owner,
            ProductionRecipe recipe, int batches) {
        if (!(account.custody() instanceof ResourceCustody.Container holder)) throw new IllegalArgumentException("recipe input has no declared container account");
        var supply = new TreeMap<String, Integer>();
        for (var kind : recipe.ingredients().stream().flatMap(input -> input.acceptedItems().stream()).distinct().toList())
            supply.put(kind, ledger.unclaimedQuantity(account.id(), owner, kind));
        var allocation = recipe.allocate(supply, batches); if (allocation.isEmpty()) return Optional.empty();
        var sources = new ArrayList<Source>();
        for (var input : new TreeMap<>(allocation.orElseThrow().quantities()).entrySet()) {
            var portion = FungibleResourceCustodySupport.selectAtAccount(ledger, account.id(), owner, input.getKey(), input.getValue());
            if (portion.isEmpty()) return Optional.empty();
            sources.add(new Source(input.getKey(), holder.containerId(), portion.orElseThrow()));
        }
        return Optional.of(new Bundle(sources));
    }
    private ProductionMaterialSupply() { }
}
