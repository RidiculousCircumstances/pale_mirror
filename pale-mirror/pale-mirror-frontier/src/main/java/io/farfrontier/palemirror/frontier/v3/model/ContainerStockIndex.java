package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Derived physical capacity. Claims affect availability, never the space occupied by the same goods. */
final class ContainerStockIndex {
    private static final int MAX_CONTAINER_VIEWS = 256;
    private record Slots(Set<Integer> occupied, Set<Integer> bound) { }
    private static final Slots EMPTY_SLOTS = new Slots(Set.of(), Set.of());
    private static final ImmutableInputView<Map<SubjectId, Slots>> SLOT_VIEW = new ImmutableInputView<>();
    private static final Map<SubjectId, ImmutableInputView<ContainerSlotBudget>> VIEWS = new LinkedHashMap<>();
    private static Map<SubjectId, ResourceLot> currentLots;
    private static Map<SubjectId, Slots> previousSlots = Map.of();
    private ContainerStockIndex() { }

    static synchronized ContainerSlotBudget budget(ExactInventory inventory, SubjectId container) {
        var ledger = inventory.fungibleResources();
        // Retain one metadata epoch, not a whole historical lot map per container.
        if (currentLots != ledger.lots()) { VIEWS.clear(); currentLots = ledger.lots(); }
        var slots = SLOT_VIEW.get(List.of(inventory.occupiedSlots(), ledger.bindings()), () -> slots(inventory))
                .getOrDefault(container, EMPTY_SLOTS);
        // A draft can replace claims/accounts without changing physical stock. Only
        // this receiver's quantities and item definitions feed its capacity view.
        var accounts = FungibleResourceReadIndex.containerAccounts(ledger, container);
        var inputs = new ArrayList<Object>();
        inputs.add(slots.occupied()); inputs.add(slots.bound()); inputs.add(ledger.lots());
        for (var account : accounts) { inputs.add(account.id()); inputs.add(account.lotQuantities()); }
        if (VIEWS.size() >= MAX_CONTAINER_VIEWS && !VIEWS.containsKey(container)) VIEWS.remove(VIEWS.keySet().iterator().next());
        return VIEWS.computeIfAbsent(container, ignored -> new ImmutableInputView<>()).get(inputs, () -> {
            var stock = new HashMap<String, Long>();
            for (var account : accounts) account.lotQuantities().forEach((lot, quantity) ->
                    stock.merge(ledger.lots().get(lot).itemKind(), quantity.longValue(), Math::addExact));
            long packed = stock.values().stream().mapToLong(quantity -> (quantity + 63) / 64).sum();
            return new ContainerSlotBudget(slots.occupied(), slots.bound(), packed, stock);
        });
    }

    private static Map<SubjectId, Slots> slots(ExactInventory inventory) {
        var occupied = new HashMap<SubjectId, Set<Integer>>(); var bound = new HashMap<SubjectId, Set<Integer>>();
        inventory.occupiedSlots().keySet().forEach(slot ->
                occupied.computeIfAbsent(slot.containerId(), ignored -> new HashSet<>()).add(slot.slot()));
        inventory.fungibleResources().bindings().values().forEach(binding -> {
            if (binding.address() instanceof PhysicalStackAddress.ContainerSlot address)
                bound.computeIfAbsent(address.slot().containerId(), ignored -> new HashSet<>()).add(address.slot().slot());
        });
        var containers = new HashSet<>(occupied.keySet()); containers.addAll(bound.keySet());
        var result = new HashMap<SubjectId, Slots>();
        for (var container : containers) {
            var next = new Slots(Set.copyOf(occupied.getOrDefault(container, Set.of())),
                    Set.copyOf(bound.getOrDefault(container, Set.of())));
            var old = previousSlots.get(container);
            result.put(container, next.equals(old) ? old : next);
        }
        previousSlots = Map.copyOf(result);
        return previousSlots;
    }
}
