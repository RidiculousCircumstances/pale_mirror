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
                             Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems,
                             Map<UUID, List<SubjectId>> worldCarrierItems, Map<SubjectId, InventoryConflict> conflicts,
                             Map<SubjectId, ContainerSurface> surfaces) {
    private static final int MAX_WORLD_CARRIERS = 4_096;
    private static final int MAX_CONFLICTS = 4_096;
    public ExactInventory {
        containers = Map.copyOf(containers); items = Map.copyOf(items); cargo = Map.copyOf(cargo);
        playerItems = playerItems.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        worldCarrierItems = worldCarrierItems.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        conflicts = Map.copyOf(conflicts);
        surfaces = Map.copyOf(surfaces);
        if (worldCarrierItems.size() > MAX_WORLD_CARRIERS) throw new IllegalArgumentException("world carrier retention limit exceeded");
        if (conflicts.size() > MAX_CONFLICTS) throw new IllegalArgumentException("inventory conflict retention limit exceeded");
        Map<InventoryCustody.ContainerSlot, SubjectId> slots = new HashMap<>();
        for (Map.Entry<SubjectId, ContainerRecord> entry : containers.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("container map key does not match container identity");
        }
        if (!containers.keySet().equals(surfaces.keySet())) throw new IllegalArgumentException("every exact container requires one physical surface");
        for (Map.Entry<SubjectId, ContainerSurface> entry : surfaces.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().containerId()) || !containers.containsKey(entry.getKey())) throw new IllegalArgumentException("container surface must own one known container");
        }
        for (Map.Entry<SubjectId, ExactItemStack> entry : items.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("item map key does not match item identity");
        }
        for (Map.Entry<SubjectId, InventoryConflict> entry : conflicts.entrySet()) {
            InventoryConflict conflict = entry.getValue();
            if (!entry.getKey().equals(conflict.id()) || (!items.containsKey(conflict.subjectId()) && !containers.containsKey(conflict.subjectId()))) {
                throw new IllegalArgumentException("inventory conflict must retain one exact or physical-container subject");
            }
            ContainerRecord container = containers.get(conflict.containerId());
            if (container == null || conflict.slot() >= container.slotCount()) throw new IllegalArgumentException("inventory conflict targets an invalid container slot");
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
            case InventoryCustody.WorldCarrier carrier -> {
                List<SubjectId> value = worldCarrierItems.get(carrier.carrierId());
                if (value == null || !value.contains(item.id())) throw new IllegalArgumentException("dangling or conflicting world carrier custody");
            }
        };
        for (CargoBatch batch : cargo.values()) {
            for (SubjectId item : batch.itemIds()) require(items.get(item), new InventoryCustody.Cargo(batch.id()), "cargo reverse custody");
        }
        for (Map.Entry<UUID, List<SubjectId>> player : playerItems.entrySet()) {
            requireDistinct(player.getValue(), "player custody");
            for (SubjectId item : player.getValue()) require(items.get(item), new InventoryCustody.Player(player.getKey()), "player reverse custody");
        }
        for (Map.Entry<UUID, List<SubjectId>> carrier : worldCarrierItems.entrySet()) {
            requireDistinct(carrier.getValue(), "world carrier custody");
            for (SubjectId item : carrier.getValue()) require(items.get(item), new InventoryCustody.WorldCarrier(carrier.getKey()), "world carrier reverse custody");
        }
    }
    public ExactInventory(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                          Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems) {
        this(containers, items, cargo, playerItems, Map.of(), Map.of(), Map.of());
    }
    public ExactInventory(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                          Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems, Map<UUID, List<SubjectId>> worldCarrierItems) {
        this(containers, items, cargo, playerItems, worldCarrierItems, Map.of(), Map.of());
    }
    public static ExactInventory empty() { return new ExactInventory(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()); }

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
        return new ExactInventory(containers, nextItems, cargo, playerItems, worldCarrierItems, conflicts, surfaces);
    }

    /** Removes one exact item only after the caller has recorded the durable process which owns it. */
    public ExactInventory withoutItem(SubjectId itemId) {
        Objects.requireNonNull(itemId, "item id");
        if (!items.containsKey(itemId)) throw new IllegalArgumentException("item is absent: " + itemId.value());
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        nextItems.remove(itemId);
        return new ExactInventory(containers, nextItems, cargo, playerItems, worldCarrierItems, conflicts, surfaces);
    }

    /** Stores a new exact stack only in an actual currently-free owned container slot. */
    public ExactInventory store(ExactItemStack item) {
        Objects.requireNonNull(item, "item");
        if (items.containsKey(item.id())) throw new IllegalArgumentException("stored item identity already exists: " + item.id().value());
        if (!(item.custody() instanceof InventoryCustody.ContainerSlot)) throw new IllegalArgumentException("stored item must enter a container slot");
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        nextItems.put(item.id(), item);
        return new ExactInventory(containers, nextItems, cargo, playerItems, worldCarrierItems, conflicts, surfaces);
    }

    /**
     * Moves a complete named set of warehouse stacks into one new identified cargo batch. The
     * stacks keep their identities and counts; only their one canonical custody changes.
     */
    public ExactInventory loadCargo(CargoBatch batch) {
        Objects.requireNonNull(batch, "cargo batch");
        if (cargo.containsKey(batch.id())) throw new IllegalArgumentException("cargo identity already exists: " + batch.id().value());
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        for (SubjectId itemId : batch.itemIds()) {
            ExactItemStack item = nextItems.get(itemId);
            if (item == null) throw new IllegalArgumentException("cargo item is absent: " + itemId.value());
            if (!(item.custody() instanceof InventoryCustody.ContainerSlot)) {
                throw new IllegalArgumentException("cargo item is not in a warehouse slot: " + itemId.value());
            }
            InventoryCustody.ContainerSlot slot = (InventoryCustody.ContainerSlot) item.custody();
            ContainerRecord container = containers.get(slot.containerId());
            if (container == null || !container.ownerId().equals(batch.ownerId())) {
                throw new IllegalArgumentException("cargo item is not owned by the cargo sender: " + itemId.value());
            }
            nextItems.put(itemId, new ExactItemStack(item.id(), item.itemKind(), item.count(), new InventoryCustody.Cargo(batch.id())));
        }
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo);
        nextCargo.put(batch.id(), batch);
        return new ExactInventory(containers, nextItems, nextCargo, playerItems, worldCarrierItems, conflicts, surfaces);
    }

    /** Moves every exact cargo item into observed receiver slots and removes the completed batch. */
    public ExactInventory completeCargoHandoff(SubjectId cargoId, List<CargoHandoffPlacement> placements) {
        CargoBatch batch = cargo.get(Objects.requireNonNull(cargoId, "cargo id"));
        if (batch == null) throw new IllegalArgumentException("completed cargo is absent: " + cargoId.value());
        List<SubjectId> observedItems = placements.stream().map(CargoHandoffPlacement::itemId).sorted().toList();
        if (!observedItems.equals(batch.itemIds().stream().sorted().toList())) {
            throw new IllegalArgumentException("cargo hand-off observation does not place every exact cargo item");
        }
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        for (CargoHandoffPlacement placement : placements) {
            ExactItemStack item = nextItems.get(placement.itemId());
            if (item == null || !item.custody().equals(new InventoryCustody.Cargo(cargoId))) {
                throw new IllegalArgumentException("handoff item is not in its exact cargo batch: " + placement.itemId().value());
            }
            nextItems.put(item.id(), new ExactItemStack(item.id(), item.itemKind(), item.count(), placement.receiverSlot()));
        }
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo);
        nextCargo.remove(cargoId);
        return new ExactInventory(containers, nextItems, nextCargo, playerItems, worldCarrierItems, conflicts, surfaces);
    }

    /** Applies observed player/container custody only when the exact canonical stack still has its expected source. */
    public ExactInventory moveObservedItem(SubjectId itemId, InventoryCustody from, InventoryCustody to) {
        Objects.requireNonNull(itemId, "item id"); Objects.requireNonNull(from, "source custody"); Objects.requireNonNull(to, "target custody");
        ExactItemStack current = items.get(itemId);
        if (current == null || !current.custody().equals(from)) throw new IllegalArgumentException("observed item source does not match canonical custody");
        if (to instanceof InventoryCustody.ContainerSlot target) {
            ContainerRecord container = containers.get(target.containerId());
            if (container == null || target.slot() >= container.slotCount() || itemAt(target.containerId(), target.slot()).isPresent()) {
                throw new IllegalArgumentException("observed container target is unavailable");
            }
        }
        Map<UUID, List<SubjectId>> nextPlayers = mutableCustody(playerItems);
        removePlayerCustody(nextPlayers, from, itemId);
        addPlayerCustody(nextPlayers, to, itemId);
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        nextItems.put(itemId, new ExactItemStack(current.id(), current.itemKind(), current.count(), to));
        return new ExactInventory(containers, nextItems, cargo, nextPlayers, worldCarrierItems, conflicts, surfaces);
    }

    public ExactInventory recordConflict(InventoryConflict conflict) {
        Objects.requireNonNull(conflict, "inventory conflict");
        InventoryConflict previous = conflicts.get(conflict.id());
        if (conflict.equals(previous)) return this;
        if (previous != null) throw new IllegalArgumentException("inventory conflict identity cannot change its evidence");
        Map<SubjectId, InventoryConflict> next = new HashMap<>(conflicts);
        if (next.size() >= MAX_CONFLICTS) throw new IllegalArgumentException("inventory conflict retention limit exceeded");
        next.put(conflict.id(), conflict);
        return new ExactInventory(containers, items, cargo, playerItems, worldCarrierItems, next, surfaces);
    }
    public ExactInventory withSurfaceStatus(SubjectId containerId, ContainerSurfaceStatus status) {
        ContainerSurface current = surfaces.get(Objects.requireNonNull(containerId, "container id"));
        if (current == null) throw new IllegalArgumentException("container has no physical surface: " + containerId.value());
        Map<SubjectId, ContainerSurface> next = new HashMap<>(surfaces); next.put(containerId, current.transitionTo(status));
        return new ExactInventory(containers, items, cargo, playerItems, worldCarrierItems, conflicts, next);
    }

    private static Map<UUID, List<SubjectId>> mutableCustody(Map<UUID, List<SubjectId>> values) {
        Map<UUID, List<SubjectId>> mutable = new HashMap<>();
        values.forEach((player, itemIds) -> mutable.put(player, new java.util.ArrayList<>(itemIds)));
        return mutable;
    }
    private static void removePlayerCustody(Map<UUID, List<SubjectId>> players, InventoryCustody custody, SubjectId itemId) {
        if (custody instanceof InventoryCustody.Player player) {
            List<SubjectId> held = players.get(player.playerId());
            if (held == null || !held.remove(itemId)) throw new IllegalArgumentException("observed player source is unavailable");
            if (held.isEmpty()) players.remove(player.playerId());
        }
    }
    private static void addPlayerCustody(Map<UUID, List<SubjectId>> players, InventoryCustody custody, SubjectId itemId) {
        if (custody instanceof InventoryCustody.Player player) {
            players.computeIfAbsent(player.playerId(), ignored -> new java.util.ArrayList<>()).add(itemId);
        }
    }
    private static void require(Object value, Object expected, String label) {
        if (value instanceof ExactItemStack item && item.custody().equals(expected)) return;
        throw new IllegalArgumentException("dangling or conflicting " + label);
    }
    private static void requireDistinct(List<SubjectId> values, String label) {
        if (values.stream().distinct().count() != values.size()) throw new IllegalArgumentException(label + " must contain distinct exact item identities");
    }
}
