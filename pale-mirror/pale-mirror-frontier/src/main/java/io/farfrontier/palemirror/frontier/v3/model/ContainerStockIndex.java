package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Read-only per-container index over exact inventory and fungible custody inputs. */
final class ContainerStockIndex {
    private static final ImmutableInputView<Map<SubjectId, ContainerSlotBudget>> VIEW = new ImmutableInputView<>();
    private static final ContainerSlotBudget EMPTY = new ContainerSlotBudget(Set.of(), Set.of(), 0, Map.of());
    private ContainerStockIndex() { }
    static ContainerSlotBudget budget(ExactInventory inventory, SubjectId container) {
        var ledger = inventory.fungibleResources();
        return VIEW.get(List.of(inventory.occupiedSlots(), ledger.bindings(), ledger.accounts(), ledger.lots()),
                () -> build(inventory)).getOrDefault(container, EMPTY);
    }
    private static Map<SubjectId, ContainerSlotBudget> build(ExactInventory inventory) {
        var occupied = new HashMap<SubjectId, Set<Integer>>(); var bound = new HashMap<SubjectId, Set<Integer>>();
        var stock = new HashMap<SubjectId, Map<String, Long>>(); var containers = new HashSet<SubjectId>();
        inventory.occupiedSlots().keySet().forEach(slot ->
                occupied.computeIfAbsent(slot.containerId(), ignored -> new HashSet<>()).add(slot.slot()));
        var ledger = inventory.fungibleResources();
        ledger.bindings().values().forEach(binding -> {
            if (binding.address() instanceof PhysicalStackAddress.ContainerSlot address)
                bound.computeIfAbsent(address.slot().containerId(), ignored -> new HashSet<>()).add(address.slot().slot());
        });
        ledger.accounts().values().forEach(account -> {
            if (account.custody() instanceof ResourceCustody.Container location) {
                var kinds = stock.computeIfAbsent(location.containerId(), ignored -> new HashMap<>());
                account.lotQuantities().forEach((lot, quantity) -> kinds.merge(ledger.lots().get(lot).itemKind(), quantity.longValue(), Math::addExact));
            }
        });
        containers.addAll(occupied.keySet()); containers.addAll(bound.keySet()); containers.addAll(stock.keySet());
        var result = new HashMap<SubjectId, ContainerSlotBudget>();
        for (var container : containers) {
            var kinds = stock.getOrDefault(container, Map.of());
            long packed = kinds.values().stream().mapToLong(quantity -> (quantity + 63) / 64).sum();
            result.put(container, new ContainerSlotBudget(occupied.getOrDefault(container, Set.of()),
                    bound.getOrDefault(container, Set.of()), packed, kinds));
        }
        return Map.copyOf(result);
    }
}
