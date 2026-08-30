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
                             Map<SubjectId, ContainerSurface> surfaces,
                             Map<InventoryCustody.ContainerSlot, SubjectId> occupiedSlots) {
    private static final int MAX_WORLD_CARRIERS = 4_096;
    private static final int MAX_CONFLICTS = 4_096;
    public ExactInventory {
        containers = Map.copyOf(containers); items = Map.copyOf(items); cargo = Map.copyOf(cargo);
        playerItems = playerItems.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        worldCarrierItems = worldCarrierItems.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        conflicts = Map.copyOf(conflicts);
        surfaces = Map.copyOf(surfaces); occupiedSlots = Map.copyOf(occupiedSlots);
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
                if (!container.ownerId().equals(item.economicOwnerId())) throw new IllegalArgumentException("container-held item claim must belong to its container owner");
            }
            case InventoryCustody.Cargo batch -> {
                CargoBatch value = cargo.get(batch.cargoId());
                if (value == null || !value.itemIds().contains(item.id())) throw new IllegalArgumentException("dangling or conflicting cargo custody");
                if (!value.ownerId().equals(item.economicOwnerId())) throw new IllegalArgumentException("cargo-held item claim must belong to its sender");
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
        if (!slots.equals(occupiedSlots)) throw new IllegalArgumentException("container slot index must exactly match exact item custody");
    }

    /**
     * The slot index is a derived immutable acceleration structure, never a second custody
     * source of truth.  Deserializers and ordinary callers supply only canonical item custody;
     * the strict canonical constructor proves that its reconstructed index is exact.
     */
    public ExactInventory(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                          Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems,
                          Map<UUID, List<SubjectId>> worldCarrierItems, Map<SubjectId, InventoryConflict> conflicts,
                          Map<SubjectId, ContainerSurface> surfaces) {
        this(containers, items, cargo, playerItems, worldCarrierItems, conflicts, surfaces, occupiedSlots(items));
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
        return Optional.ofNullable(occupiedSlots.get(new InventoryCustody.ContainerSlot(containerId, slot))).map(items::get);
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
        requireContainerClaim(producedItem);
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

    /** Removes one exact stack only when its surviving canonical custody still matches physical evidence. */
    public ExactInventory destroyObservedItem(SubjectId itemId, InventoryCustody source) {
        Objects.requireNonNull(itemId, "item id"); Objects.requireNonNull(source, "item source");
        ExactItemStack current = items.get(itemId);
        if (current == null || !current.custody().equals(source)) throw new IllegalArgumentException("destroyed item source does not match canonical custody");
        Map<UUID, List<SubjectId>> nextPlayers = mutableCustody(playerItems);
        Map<UUID, List<SubjectId>> nextCarriers = mutableCustody(worldCarrierItems);
        removePlayerCustody(nextPlayers, source, itemId); removeCarrierCustody(nextCarriers, source, itemId);
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items); nextItems.remove(itemId);
        return new ExactInventory(containers, nextItems, cargo, nextPlayers, nextCarriers, conflicts, surfaces);
    }

    /** Records one real item consumed from a physical stack; the stack identity remains stable. */
    public ExactInventory consumeOne(SubjectId itemId) {
        ExactItemStack current = items.get(Objects.requireNonNull(itemId, "item id"));
        if (current == null) throw new IllegalArgumentException("consumed item is absent: " + itemId.value());
        if (!(current.custody() instanceof InventoryCustody.ContainerSlot)) {
            throw new IllegalArgumentException("repair material must remain in an owned container slot");
        }
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        if (current.count() == 1) nextItems.remove(itemId);
        else nextItems.put(itemId, new ExactItemStack(current.id(), current.economicOwnerId(), current.itemKind(), current.count() - 1, current.custody()));
        return new ExactInventory(containers, nextItems, cargo, playerItems, worldCarrierItems, conflicts, surfaces);
    }

    /** Decrements one exact owned stack after a matching durable physical receipt. */
    public ExactInventory consume(SubjectId itemId, int count) {
        ExactItemStack current = items.get(Objects.requireNonNull(itemId, "item id"));
        if (current == null || count < 1 || count > current.count()) throw new IllegalArgumentException("exact consumption count does not match current stack");
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        if (count == current.count()) nextItems.remove(itemId);
        else nextItems.put(itemId, new ExactItemStack(current.id(), current.economicOwnerId(), current.itemKind(), current.count() - count, current.custody()));
        return new ExactInventory(containers, nextItems, cargo, playerItems, worldCarrierItems, conflicts, surfaces);
    }

    /** Consumes one exact COLD cargo unit only through the owning work process. */
    public ExactInventory consumeCargoUnit(SubjectId cargoId, SubjectId itemId) {
        CargoBatch batch = cargo.get(Objects.requireNonNull(cargoId, "cargo id"));
        ExactItemStack current = items.get(Objects.requireNonNull(itemId, "item id"));
        if (batch == null || !batch.itemIds().equals(java.util.List.of(itemId)) || current == null
                || !current.custody().equals(new InventoryCustody.Cargo(cargoId))) {
            throw new IllegalArgumentException("construction material is not one exact work cargo");
        }
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items); Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo);
        if (current.count() == 1) { nextItems.remove(itemId); nextCargo.remove(cargoId); }
        else nextItems.put(itemId, new ExactItemStack(current.id(), current.economicOwnerId(), current.itemKind(), current.count() - 1, current.custody()));
        return new ExactInventory(containers, nextItems, nextCargo, playerItems, worldCarrierItems, conflicts, surfaces);
    }

    /** Splits one real owned-container unit into one new exact single-unit COLD cargo. */
    public ExactInventory extractOneToCargo(SubjectId sourceItemId, CargoBatch batch, SubjectId cargoItemId) {
        Objects.requireNonNull(batch, "cargo batch"); Objects.requireNonNull(cargoItemId, "cargo item id");
        ExactItemStack source = items.get(Objects.requireNonNull(sourceItemId, "source item id"));
        if (source == null || !(source.custody() instanceof InventoryCustody.ContainerSlot slot) || source.count() < 1
                || !batch.itemIds().equals(java.util.List.of(cargoItemId)) || cargo.containsKey(batch.id()) || items.containsKey(cargoItemId)) {
            throw new IllegalArgumentException("route construction extraction has invalid exact source or cargo identity");
        }
        ContainerRecord container = containers.get(slot.containerId());
        if (container == null || !container.ownerId().equals(batch.ownerId()) || !source.economicOwnerId().equals(batch.ownerId())) {
            throw new IllegalArgumentException("route construction extraction source is not owned by its cargo sender");
        }
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        if (source.count() == 1) nextItems.remove(sourceItemId);
        else nextItems.put(sourceItemId, new ExactItemStack(source.id(), source.economicOwnerId(), source.itemKind(), source.count() - 1, source.custody()));
        nextItems.put(cargoItemId, new ExactItemStack(cargoItemId, source.economicOwnerId(), source.itemKind(), 1, new InventoryCustody.Cargo(batch.id())));
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo); nextCargo.put(batch.id(), batch);
        return new ExactInventory(containers, nextItems, nextCargo, playerItems, worldCarrierItems, conflicts, surfaces);
    }

    /** Stores a new exact stack only in an actual currently-free owned container slot. */
    public ExactInventory store(ExactItemStack item) {
        Objects.requireNonNull(item, "item");
        if (items.containsKey(item.id())) throw new IllegalArgumentException("stored item identity already exists: " + item.id().value());
        if (!(item.custody() instanceof InventoryCustody.ContainerSlot)) throw new IllegalArgumentException("stored item must enter a container slot");
        requireContainerClaim(item);
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
            if (!item.economicOwnerId().equals(batch.ownerId())) throw new IllegalArgumentException("cargo item claim does not belong to its sender");
            nextItems.put(itemId, new ExactItemStack(item.id(), item.economicOwnerId(), item.itemKind(), item.count(), new InventoryCustody.Cargo(batch.id())));
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
            ContainerRecord receiver = containers.get(placement.receiverSlot().containerId());
            if (receiver == null) throw new IllegalArgumentException("handoff receiver container is unknown");
            nextItems.put(item.id(), new ExactItemStack(item.id(), receiver.ownerId(), item.itemKind(), item.count(), placement.receiverSlot()));
        }
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo);
        nextCargo.remove(cargoId);
        return new ExactInventory(containers, nextItems, nextCargo, playerItems, worldCarrierItems, conflicts, surfaces);
    }

    /**
     * Releases one complete shipment to a single observed physical carrier. This is deliberately
     * all-or-nothing: individual stacks may leave only after the batch no longer claims custody.
     */
    public ExactInventory releaseCargoToWorldCarrier(SubjectId cargoId, UUID carrierId) {
        CargoBatch batch = cargo.get(Objects.requireNonNull(cargoId, "cargo id"));
        Objects.requireNonNull(carrierId, "carrier id");
        if (batch == null) throw new IllegalArgumentException("released cargo is absent: " + cargoId.value());
        if (worldCarrierItems.containsKey(carrierId)) throw new IllegalArgumentException("released cargo carrier identity is already owned");
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        for (SubjectId itemId : batch.itemIds()) {
            ExactItemStack item = nextItems.get(itemId);
            if (item == null || !item.custody().equals(new InventoryCustody.Cargo(cargoId))) {
                throw new IllegalArgumentException("released item is not in its exact cargo batch: " + itemId.value());
            }
            nextItems.put(item.id(), new ExactItemStack(item.id(), item.economicOwnerId(), item.itemKind(), item.count(), new InventoryCustody.WorldCarrier(carrierId)));
        }
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo); nextCargo.remove(cargoId);
        Map<UUID, List<SubjectId>> nextCarriers = mutableCustody(worldCarrierItems);
        nextCarriers.put(carrierId, new java.util.ArrayList<>(batch.itemIds()));
        return new ExactInventory(containers, nextItems, nextCargo, playerItems, nextCarriers, conflicts, surfaces);
    }

    /** Applies one observed trusted-surface transfer only when its exact source still agrees. */
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
        Map<UUID, List<SubjectId>> nextCarriers = mutableCustody(worldCarrierItems);
        removeCarrierCustody(nextCarriers, from, itemId);
        addCarrierCustody(nextCarriers, to, itemId);
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        SubjectId nextOwner = to instanceof InventoryCustody.ContainerSlot target ? containers.get(target.containerId()).ownerId() : current.economicOwnerId();
        nextItems.put(itemId, new ExactItemStack(current.id(), nextOwner, current.itemKind(), current.count(), to));
        return new ExactInventory(containers, nextItems, cargo, nextPlayers, nextCarriers, conflicts, surfaces);
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
    private static void removeCarrierCustody(Map<UUID, List<SubjectId>> carriers, InventoryCustody custody, SubjectId itemId) {
        if (custody instanceof InventoryCustody.WorldCarrier carrier) {
            List<SubjectId> held = carriers.get(carrier.carrierId());
            if (held == null || !held.remove(itemId)) throw new IllegalArgumentException("observed world carrier source is unavailable");
            if (held.isEmpty()) carriers.remove(carrier.carrierId());
        }
    }
    private static void addCarrierCustody(Map<UUID, List<SubjectId>> carriers, InventoryCustody custody, SubjectId itemId) {
        if (custody instanceof InventoryCustody.WorldCarrier carrier) {
            carriers.computeIfAbsent(carrier.carrierId(), ignored -> new java.util.ArrayList<>()).add(itemId);
        }
    }
    private static void require(Object value, Object expected, String label) {
        if (value instanceof ExactItemStack item && item.custody().equals(expected)) return;
        throw new IllegalArgumentException("dangling or conflicting " + label);
    }
    private static void requireDistinct(List<SubjectId> values, String label) {
        if (values.stream().distinct().count() != values.size()) throw new IllegalArgumentException(label + " must contain distinct exact item identities");
    }
    private static Map<InventoryCustody.ContainerSlot, SubjectId> occupiedSlots(Map<SubjectId, ExactItemStack> items) {
        Map<InventoryCustody.ContainerSlot, SubjectId> result = new HashMap<>();
        for (ExactItemStack item : items.values()) if (item.custody() instanceof InventoryCustody.ContainerSlot slot) {
            result.put(slot, item.id());
        }
        return result;
    }
    private void requireContainerClaim(ExactItemStack item) {
        InventoryCustody.ContainerSlot slot = (InventoryCustody.ContainerSlot) item.custody();
        ContainerRecord container = containers.get(slot.containerId());
        if (container == null || slot.slot() >= container.slotCount() || !container.ownerId().equals(item.economicOwnerId())) {
            throw new IllegalArgumentException("stored item claim must belong to its target container owner");
        }
    }
}
