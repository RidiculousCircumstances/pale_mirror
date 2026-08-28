package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/** Immutable exact custody ledger. Any dangling, duplicate or mixed custody is rejected. */
public record ExactInventory(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                             Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems) {
    public ExactInventory {
        containers = Map.copyOf(containers); items = Map.copyOf(items); cargo = Map.copyOf(cargo);
        playerItems = playerItems.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        Map<InventoryCustody.ContainerSlot, SubjectId> slots = new HashMap<>();
        for (Map.Entry<SubjectId, ContainerRecord> entry : containers.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("container map key does not match container identity");
        }
        for (Map.Entry<SubjectId, ExactItemStack> entry : items.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("item map key does not match item identity");
        }
        for (Map.Entry<SubjectId, CargoBatch> entry : cargo.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("cargo map key does not match cargo identity");
        }
        for (ExactItemStack item : items.values()) switch (item.custody()) {
            case InventoryCustody.ContainerSlot slot -> {
                ContainerRecord container = containers.get(slot.containerId());
                if (container == null || slot.slot() >= container.slotCount() || slots.put(slot, item.id()) != null) throw new IllegalArgumentException("invalid or duplicate container slot custody");
            }
            case InventoryCustody.Cargo batch -> {
                CargoBatch value = cargo.get(batch.cargoId());
                if (value == null || !value.itemIds().contains(item.id())) throw new IllegalArgumentException("dangling or conflicting cargo custody");
            }
            case InventoryCustody.Player player -> {
                List<SubjectId> value = playerItems.get(player.playerId());
                if (value == null || !value.contains(item.id())) throw new IllegalArgumentException("dangling or conflicting player custody");
            }
        };
        for (CargoBatch batch : cargo.values()) {
            for (SubjectId item : batch.itemIds()) require(items.get(item), new InventoryCustody.Cargo(batch.id()), "cargo reverse custody");
        }
        for (Map.Entry<UUID, List<SubjectId>> player : playerItems.entrySet()) {
            for (SubjectId item : player.getValue()) require(items.get(item), new InventoryCustody.Player(player.getKey()), "player reverse custody");
        }
    }
    public static ExactInventory empty() { return new ExactInventory(Map.of(), Map.of(), Map.of(), Map.of()); }

    /** Returns the exact stack occupying this physical container slot, if any. */
    public Optional<ExactItemStack> itemAt(SubjectId containerId, int slot) {
        Objects.requireNonNull(containerId, "container id");
        return items.values().stream()
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot location
                        && location.containerId().equals(containerId) && location.slot() == slot)
                .findFirst();
    }

    /** Lowest available semantic slot; callers must still validate the container's owner and use. */
    public OptionalInt firstFreeSlot(SubjectId containerId) {
        ContainerRecord container = containers.get(Objects.requireNonNull(containerId, "container id"));
        if (container == null) throw new IllegalArgumentException("unknown container: " + containerId.value());
        for (int slot = 0; slot < container.slotCount(); slot++) if (itemAt(containerId, slot).isEmpty()) return OptionalInt.of(slot);
        return OptionalInt.empty();
    }

    /**
     * Atomically consumes one exact input stack and places one exact output stack. The output
     * identity must be new and its target slot must be vacant after the input is removed.
     */
    public ExactInventory consumeAndStore(SubjectId consumedItemId, ExactItemStack producedItem) {
        Objects.requireNonNull(consumedItemId, "consumed item id");
        Objects.requireNonNull(producedItem, "produced item");
        if (!items.containsKey(consumedItemId)) throw new IllegalArgumentException("consumed item is absent: " + consumedItemId.value());
        if (items.containsKey(producedItem.id())) throw new IllegalArgumentException("produced item identity already exists: " + producedItem.id().value());
        if (!(producedItem.custody() instanceof InventoryCustody.ContainerSlot)) throw new IllegalArgumentException("production output must enter a container slot");
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        nextItems.remove(consumedItemId);
        nextItems.put(producedItem.id(), producedItem);
        return new ExactInventory(containers, nextItems, cargo, playerItems);
    }

    /** Removes one exact item only after the caller has recorded the durable process which owns it. */
    public ExactInventory withoutItem(SubjectId itemId) {
        Objects.requireNonNull(itemId, "item id");
        if (!items.containsKey(itemId)) throw new IllegalArgumentException("item is absent: " + itemId.value());
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        nextItems.remove(itemId);
        return new ExactInventory(containers, nextItems, cargo, playerItems);
    }

    /** Stores a new exact stack only in an actual currently-free owned container slot. */
    public ExactInventory store(ExactItemStack item) {
        Objects.requireNonNull(item, "item");
        if (items.containsKey(item.id())) throw new IllegalArgumentException("stored item identity already exists: " + item.id().value());
        if (!(item.custody() instanceof InventoryCustody.ContainerSlot)) throw new IllegalArgumentException("stored item must enter a container slot");
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        nextItems.put(item.id(), item);
        return new ExactInventory(containers, nextItems, cargo, playerItems);
    }
    private static void require(Object value, Object expected, String label) {
        if (value instanceof ExactItemStack item && item.custody().equals(expected)) return;
        throw new IllegalArgumentException("dangling or conflicting " + label);
    }
}
