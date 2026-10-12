package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Shared storage/ledger boundary; the caller owns why and how much to consume. */
public final class ColdResourceConsumptionSupport {
    private ColdResourceConsumptionSupport() { }

    /** Discovery is producer-only. It grants no physical interaction or outstanding reservation. */
    public static Optional<ColdResourceConsumption> select(FrontierWorldState state, SubjectId owner, String itemKind,
            int quantity, SurfaceAnchor origin, Set<ResourceStorageRole> roles) {
        Map<SubjectId, List<ExactItemStack>> exact = new HashMap<>();
        for (var item : ResourceStorageSelection.exactSources(state, owner, itemKind, quantity, origin, roles)) {
            var slot = (InventoryCustody.ContainerSlot) item.custody();
            exact.computeIfAbsent(slot.containerId(), ignored -> new ArrayList<>()).add(item);
        }
        for (var endpoint : ResourceStorageSelection.candidates(state, owner, itemKind, origin, roles)) {
            var container = endpoint.containerId();
            if (!coldSource(state, container)) continue;
            var lots = FungibleResourceCustodySupport.selectAtContainer(state, container, owner, itemKind, quantity);
            if (lots.isPresent()) {
                var candidate = new ColdResourceConsumption.Fungible(owner, container, itemKind, lots.orElseThrow());
                if (available(state, candidate, roles)) return Optional.of(candidate);
            }
            for (var item : exact.getOrDefault(container, List.of())) {
                var candidate = new ColdResourceConsumption.Exact(owner, container, itemKind, item.id(), quantity);
                if (available(state, candidate, roles)) return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** Validate only the retained portions. A different usable source is not substitution authority. */
    public static boolean available(FrontierWorldState state, ColdResourceConsumption selected, Set<ResourceStorageRole> roles) {
        Objects.requireNonNull(selected);
        if (!ResourceStorageSelection.permits(state.inventory(), selected.containerId(), selected.ownerId(), selected.itemKind(), roles)
                || !coldSource(state, selected.containerId())) return false;
        return switch (selected) {
            case ColdResourceConsumption.Exact exact -> {
                var item = state.inventory().items().get(exact.itemId());
                yield item != null && item.economicOwnerId().equals(exact.ownerId()) && item.itemKind().equals(exact.itemKind())
                        && item.count() >= exact.quantity() && item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && slot.containerId().equals(exact.containerId());
            }
            case ColdResourceConsumption.Fungible fungible -> {
                var resources = state.inventory().fungibleResources();
                var account = resources.accounts().get(fungible.lots().accountId());
                yield account != null && account.custody().equals(new ResourceCustody.Container(fungible.containerId()))
                        && fungible.lots().lotQuantities().keySet().stream().allMatch(id -> {
                            var lot = resources.lots().get(id);
                            return lot != null && lot.economicOwnerId().equals(fungible.ownerId()) && lot.itemKind().equals(fungible.itemKind());
                        }) && FungibleResourceCustodySupport.canReserve(state, fungible.lots())
                        && resources.bindings().values().stream().noneMatch(binding -> binding.accountId().equals(account.id()));
            }
        };
    }

    /** The caller publishes this stock consequence and its own domain result in the same event. */
    public static ExactInventory consume(FrontierWorldState state, ColdResourceConsumption selected, Set<ResourceStorageRole> roles) {
        if (!available(state, selected, roles)) throw new IllegalArgumentException("COLD resource consumption lost its retained unfenced portions");
        return switch (selected) {
            case ColdResourceConsumption.Exact exact -> state.inventory().consume(exact.itemId(), exact.quantity());
            case ColdResourceConsumption.Fungible fungible -> state.inventory().withFungibleResources(
                    state.inventory().fungibleResources().destroy(fungible.lots().accountId(), fungible.lots().lotQuantities(), Map.of()));
        };
    }

    private static boolean coldSource(FrontierWorldState state, SubjectId container) {
        return !ReferenceContainerCustody.blocksCanonicalUse(state, container)
                && !ReferenceContainerCustody.hasLiveCustody(state, container)
                && !ContainerPhysicalAuthorityComposition.pending(state, container);
    }
}
